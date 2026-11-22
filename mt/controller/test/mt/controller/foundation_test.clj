(ns mt.controller.foundation-test
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [integrant.core :as ig]
            [mt.controller.config :as config]
            [mt.controller.coordinator :as coordinator]
            [mt.controller.domain :as domain]
            [mt.controller.effects :as effects]
            [mt.controller.store :as store]
            [mt.controller.system :as system])
  (:import [java.net InetAddress ServerSocket URI]
           [java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute PosixFilePermissions]
           [java.security SecureRandom]
           [java.time Duration]
           [java.util HexFormat UUID]
           [java.util.concurrent CountDownLatch TimeUnit]))

(defn command
  ([] (command :fixture/succeed))
  ([mode]
   {:id (UUID/randomUUID) :command-id (UUID/randomUUID) :at-ms 10
    :type :fixture/requested :mode mode}))

(defn temporary-settings [test!]
  (let [root (.toFile (Files/createTempDirectory "mt-controller-test" (make-array FileAttribute 0)))
        credential (io/file root "credential")
        entropy (byte-array 32)
        _ (.nextBytes (SecureRandom.) entropy)
        token (.formatHex (HexFormat/of) entropy)
        port (with-open [socket (ServerSocket. 0 1 (InetAddress/getByName "127.0.0.1"))]
               (.getLocalPort socket))]
    (try
      (Files/createFile (.toPath credential)
                        (into-array FileAttribute [(PosixFilePermissions/asFileAttribute
                                                    (PosixFilePermissions/fromString "rw-------"))]))
      (spit credential token)
      (test! {:host "127.0.0.1" :port port :data-dir (str root)
              :database "test.sqlite" :credential-file (str credential)
              :queue-capacity 5 :reserved-capacity 3 :effect-concurrency 2
              :command-timeout-ms 1000 :effect-timeout-ms 3000 :shutdown-timeout-ms 1500}
             token)
      (finally (doseq [file (reverse (file-seq root))] (io/delete-file file))))))

(defn with-system [settings adapter run!]
  (let [system (system/start! settings adapter)]
    (try (run! (::system/coordinator system))
         (finally (ig/halt! system)))))

(defn wait-for [predicate]
  (let [deadline (+ (System/nanoTime) 3000000000)]
    (loop []
      (or (predicate)
          (when (< (System/nanoTime) deadline)
            (Thread/sleep 5)
            (recur))))))

(defn submit-and-wait [coord command]
  (coordinator/await-result (coordinator/submit! coord command) 2500))

(deftest pure-transitions
  (let [cmd (command)
        first-result (domain/transition domain/initial-state cmd nil)
        effect (first (:effects first-result))
        dispatch (coordinator/event :effect/dispatched effect)
        {active :state [intent] :effects} (domain/transition (:state first-result) dispatch effect)
        result (domain/transition active
                                  (assoc (coordinator/event :effect/finished intent) :status :completed)
                                  intent)]
    (is (= first-result (domain/transition domain/initial-state cmd nil)))
    (is (= (:command-id cmd) (:id effect)))
    (is (= 1 (get-in result [:state :accepted])))
    (is (= {:pending 0 :active 0 :completed 1 :failed 0 :uncertain 0}
           (get-in result [:state :effects])))
    (is (thrown? Exception (domain/transition domain/initial-state {} nil)))
    (is (thrown? Exception (domain/transition (:state first-result)
                                             (assoc dispatch :command-id (UUID/randomUUID)) effect)))
    (is (thrown? Exception (domain/transition (:state first-result) cmd effect)))))

(deftest migrations-reopening-and-rollback
  (temporary-settings
   (fn [settings _]
     (let [{:keys [connection] :as db} (store/open! settings)]
       (try
         (is (= domain/initial-state (store/state connection)))
         (is (thrown-with-msg? Exception #"already has an owner" (store/open! settings)))
         (store/migrate! connection)
         (is (= 1 (:total (store/one connection ["SELECT count(*) AS total FROM schema_migrations"]))))
         (store/query connection ["CREATE TRIGGER reject_update BEFORE UPDATE ON operational_state BEGIN SELECT RAISE(ABORT, 'test rollback'); END"])
         (is (thrown? Exception (store/apply-event! connection (command))))
         (is (= domain/initial-state (store/state connection)))
         (is (empty? (store/query connection ["SELECT * FROM execution_events"])))
         (is (empty? (store/query connection ["SELECT * FROM effect_intents"])))
         (store/query connection ["DROP TRIGGER reject_update"])
         (store/apply-event! connection (command))
         (finally (store/close! db))))
     (let [{:keys [connection] :as db} (store/open! settings)]
       (try
         (is (= 1 (:accepted (store/state connection))))
         (store/query connection ["INSERT INTO schema_migrations VALUES (2, 'unknown')"])
         (finally (store/close! db))))
     (is (thrown-with-msg? Exception #"newer" (store/open! settings))))))

(deftest concurrent-effects-and-saturated-admission
  (temporary-settings
   (fn [settings _]
     (let [started (CountDownLatch. 2)
           release (CountDownLatch. 1)
           running (atom 0)
           peak (atom 0)
           run! (fn [effect]
                  (swap! peak max (swap! running inc))
                  (.countDown started)
                  (try
                    (.await release)
                    (effects/fixture! effect)
                    (finally (swap! running dec))))]
       (with-system
         settings run!
         (fn [coord]
           (let [admissions (mapv deref
                                  [(future (coordinator/submit! coord (command)))
                                   (future (coordinator/submit! coord (command :fixture/fail)))])]
             (is (every? #(= :committed (:status (coordinator/await-result % 2500))) admissions)))
           (is (.await started 2 TimeUnit/SECONDS))
           (locking (:dispatch-gate coord)
             (is (wait-for #(= java.lang.Thread$State/BLOCKED (.getState (:thread coord)))))
             (let [first (coordinator/submit! coord (command))
                   second (coordinator/submit! coord (command))]
               (is (= [:accepted :accepted] (mapv :status [first second])))
               (is (= :overloaded (:reason (coordinator/submit! coord (command)))))
               (.countDown release)
               (is (wait-for #(= 4 (.size (:queue coord)))))))
           (is (wait-for #(= {:pending 0 :active 0 :completed 3 :failed 1 :uncertain 0}
                            (:effects @(:snapshot coord)))))
           (is (= 4 (:accepted @(:snapshot coord))))
           (is (= 2 @peak))))
       (let [{:keys [connection] :as db} (store/open! settings)]
         (try
           (is (= 1 (get-in (store/state connection) [:effects :failed])))
           (is (= 12 (:total (store/one connection ["SELECT count(*) AS total FROM execution_events"]))))
           (finally (store/close! db))))))))

(deftest restart-reconciles-only-pending-effects
  (temporary-settings
   (fn [settings _]
     (let [{:keys [connection] :as db} (store/open! settings)
           started (command)
           pending (command)
           executed (atom [])]
       (try
         (store/apply-event! connection started)
         (store/apply-event! connection (coordinator/event :effect/dispatched
                                                           (store/next-effect connection :pending)))
         (store/apply-event! connection pending)
         (finally (store/close! db)))
       (with-system settings (fn [effect] (swap! executed conj (:id effect)) :completed)
         (fn [coord]
           (is (wait-for #(= 1 (get-in @(:snapshot coord) [:effects :completed]))))
           (is (= [(:command-id pending)] @executed))
           (is (= 1 (get-in @(:snapshot coord) [:effects :uncertain])))))
       (with-system settings (fn [effect] (swap! executed conj (:id effect)) :completed)
         (fn [coord]
           (is (= 2 (:accepted @(:snapshot coord))))
           (is (= 1 (get-in @(:snapshot coord) [:effects :uncertain])))
           (is (= [(:command-id pending)] @executed))))))))

(deftest timeout-and-shutdown
  (temporary-settings
   (fn [settings _]
     (let [started (CountDownLatch. 1)
           interrupted (CountDownLatch. 1)
           release (CountDownLatch. 1)
           run! (fn [_]
                  (.countDown started)
                  (try (.await release)
                       (catch InterruptedException _
                         (.countDown interrupted)
                         (.await release)))
                  :completed)]
       (with-system (assoc settings :effect-timeout-ms 250 :effect-concurrency 1) run!
         (fn [coord]
           (try
             (is (= :committed (:status (submit-and-wait coord (command)))))
             (is (.await started 2 TimeUnit/SECONDS))
             (is (.await interrupted 2 TimeUnit/SECONDS))
             (is (wait-for #(= 1 (get-in @(:snapshot coord) [:effects :uncertain]))))
             (is (= :committed (:status (submit-and-wait coord (command)))))
             (is (= 1 (get-in @(:snapshot coord) [:effects :pending])))
             (coordinator/quiesce! coord)
             (is (= :unavailable (:reason (coordinator/submit! coord (command)))))
             (finally (.countDown release)))))
       (let [{:keys [connection] :as db} (store/open! settings)]
         (try
           (is (= 1 (get-in (store/state connection) [:effects :uncertain])))
           (finally (store/close! db))))))))

(deftest command-deadline-and-pending-limit
  (temporary-settings
   (fn [settings _]
     (let [db (store/open! settings)
           coord (coordinator/start! db (assoc settings :command-timeout-ms 500))]
       (try
         ;; Leave dispatch disabled so the durable pending limit is observable.
         (dotimes [_ (:queue-capacity settings)]
           (is (= :committed (:status (submit-and-wait coord (command))))))
         (is (= :pending-limit (:reason (submit-and-wait coord (command)))))
         (let [admission (locking (:dispatch-gate coord)
                           (is (wait-for #(= java.lang.Thread$State/BLOCKED (.getState (:thread coord)))))
                           (let [admission (coordinator/submit! coord (command))]
                             (is (= :uncertain (:status (coordinator/await-result admission 5))))
                             (is (wait-for #(>= (System/nanoTime) (:deadline admission))))
                             admission))]
           (is (= :expired (:reason (deref (:reply admission) 1500 nil)))))
         (finally
           (coordinator/stop! coord)
           (is (= @(:snapshot coord) (store/state (:connection db))))
           (store/close! db)))))))

(deftest shutdown-cancels-running-effect
  (temporary-settings
   (fn [settings _]
     (let [started (CountDownLatch. 1)
           interrupted (CountDownLatch. 1)
           block (CountDownLatch. 1)
           adapter (fn [_]
                     (.countDown started)
                     (try
                       (.await block)
                       :completed
                       (finally (.countDown interrupted))))]
       (with-system settings adapter
         (fn [coord]
           (is (= :committed (:status (submit-and-wait coord (command)))))
           (is (.await started 2 TimeUnit/SECONDS))))
       (is (.await interrupted 1 TimeUnit/SECONDS))
       (let [db (store/open! settings)]
         (try
           (is (= 1 (get-in (store/state (:connection db)) [:effects :uncertain])))
           (finally (store/close! db))))))))

(defn request [settings path token method]
  (let [builder (HttpRequest/newBuilder
                 (URI/create (str "http://127.0.0.1:" (:port settings) path)))]
    (when token (.header builder "Authorization" (str "Bearer " token)))
    (.timeout builder (Duration/ofSeconds 2))
    (if (= method "GET")
      (.GET builder)
      (.method builder method (java.net.http.HttpRequest$BodyPublishers/noBody)))
    (let [response (.send (HttpClient/newHttpClient) (.build builder) (HttpResponse$BodyHandlers/ofString))]
      [(.statusCode response) (json/read-str (.body response) :key-fn keyword)])))

(deftest authenticated-management
  (temporary-settings
   (fn [settings token]
     (is (thrown? Exception (config/validate (assoc settings :host "0.0.0.0"))))
     (with-system settings effects/fixture!
       (fn [_]
         (is (= [401 {:error {:code "unauthorized"}}] (request settings "/api/v1/status" nil "GET")))
         (is (= 401 (first (request settings "/api/v1/health" (apply str (repeat 64 "0")) "GET"))))
         (is (= [200 {:ready true :version "0.1.0" :lifecycle "running" :storage_ready true}]
                (request settings "/api/v1/health" token "GET")))
         (let [[status data] (request settings "/api/v1/status" token "GET")]
           (is (= 200 status))
           (is (= (:queue-capacity settings) (get-in data [:queue :capacity])))
           (is (= (:effect-concurrency settings) (:effect_capacity data))))
         (is (= 404 (first (request settings "/api/v1/missing" token "GET"))))
         (is (= 405 (first (request settings "/api/v1/status" token "POST"))))
         (is (thrown? Exception (system/start! (assoc settings :database "second.sqlite") effects/fixture!)))))
     ;; Failed HTTP startup must release its store ownership.
     (let [db (store/open! (assoc settings :database "second.sqlite"))]
       (store/close! db))
     (is (thrown? java.io.IOException (request settings "/api/v1/health" token "GET"))))))
