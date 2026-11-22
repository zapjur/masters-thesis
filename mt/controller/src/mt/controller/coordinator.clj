(ns mt.controller.coordinator
  (:require [mt.controller.domain :as domain]
            [mt.controller.effects :as effects]
            [mt.controller.store :as store])
  (:import [java.time Duration]
           [java.util UUID]
           [java.util.concurrent ArrayBlockingQueue Semaphore TimeUnit]))

(defn diagnostic! [data]
  (binding [*out* *err*]
    (println (pr-str (assoc data :at-ms (System/currentTimeMillis))))))

(defn event [type effect]
  {:id (UUID/randomUUID) :at-ms (System/currentTimeMillis) :type type
   :command-id (:command-id effect) :effect-id (:id effect)})

(defn commit! [{:keys [connection snapshot]} event]
  (let [state (store/apply-event! connection event)]
    (reset! snapshot state)
    (diagnostic! (assoc (select-keys event [:type :command-id :effect-id :status :reason])
                        :event-id (:id event)))
    state))

(defn submit! [{:keys [queue external gate lifecycle config]} command]
  (cond
    (not (domain/valid-command? command)) {:status :rejected :reason :invalid}
    :else
    (locking gate
      (cond
        (not= :running @lifecycle) {:status :rejected :reason :unavailable}
        (not (.tryAcquire external)) {:status :rejected :reason :overloaded}
        :else
        (let [reply (promise)
              deadline (+ (System/nanoTime) (* 1000000 (:command-timeout-ms config)))]
          (when-not (.offer queue {:command command :reply reply :deadline deadline})
            (.release external)
            (throw (IllegalStateException. "External queue capacity invariant violated")))
          {:status :accepted :command-id (:command-id command) :reply reply :deadline deadline})))))

(defn await-result [{:keys [reply command-id deadline] :as admission} timeout-ms]
  (if reply
    (let [remaining-ms (max 0 (quot (- deadline (System/nanoTime)) 1000000))]
      (deref reply (min remaining-ms timeout-ms) {:status :uncertain :command-id command-id}))
    admission))

(defn process-command! [{:keys [config snapshot external] :as coordinator}
                        {:keys [command reply deadline]}]
  (try
    (let [response (cond
                     (<= deadline (System/nanoTime))
                     {:status :rejected :reason :expired}

                     (>= (get-in @snapshot [:effects :pending]) (:queue-capacity config))
                     {:status :rejected :reason :pending-limit}

                     :else
                     (try
                       (commit! coordinator command)
                       {:status :committed}
                       (catch clojure.lang.ExceptionInfo error
                         (if (:rejected (ex-data error))
                           {:status :rejected :reason :invalid-transition}
                           (throw error)))))]
      (deliver reply (assoc response :command-id (:command-id command))))
    (catch Exception error
      (deliver reply {:status :uncertain :command-id (:command-id command)})
      (throw error))
    (finally (.release external))))

(defn dispatch! [{:keys [connection queue config active run-effect lifecycle gate dispatch-gate] :as coordinator}]
  (swap! active (fn [work]
                  (into {} (remove (fn [[_ handle]]
                                     (and (:committed handle) (effects/stopped? handle)))) work)))
  (locking dispatch-gate
    (when (and (= :running @lifecycle) @run-effect
               (< (count @active) (:effect-concurrency config)))
      (when-let [{:keys [id] :as effect} (store/next-effect connection :pending)]
        (commit! coordinator (event :effect/dispatched effect))
        (locking gate
          (when (= :running @lifecycle)
            (let [handle (effects/launch!
                          @run-effect effect (:effect-timeout-ms config)
                          (fn [outcome]
                            (when-not (.offer queue {:outcome (merge (event :effect/finished effect) outcome)})
                              (throw (IllegalStateException. "Outcome reservation invariant violated")))))]
              (swap! active assoc id handle))
            true))))))

(defn work! [{:keys [queue lifecycle active] :as coordinator}]
  (try
    (loop []
      (when (#{:running :draining} @lifecycle)
        (let [dispatched? (dispatch! coordinator)
              {:keys [command outcome] :as entry}
              (.poll queue (if dispatched? 0 20) TimeUnit/MILLISECONDS)]
          (cond
            command (if (= :stopped @lifecycle)
                      (do (deliver (:reply entry) {:status :rejected :reason :shutdown
                                                   :command-id (:command-id command)})
                          (.release (:external coordinator)))
                      (process-command! coordinator entry))
            outcome (do (commit! coordinator outcome)
                        (swap! active update (:effect-id outcome) assoc :committed true))
            (= :drain (:control entry)) nil)
          (recur))))
    (catch Throwable error
      (locking (:gate coordinator)
        (when (#{:running :draining} @lifecycle) (reset! lifecycle :failed)))
      (diagnostic! {:type :controller/writer-failed :error (str (class error))}))))

(defn start! [{:keys [connection writer]} config]
  (let [coordinator {:config config :connection connection :snapshot (atom (store/state connection))
                     :lifecycle (atom :running) :run-effect (atom nil)
                     :gate (Object.) :dispatch-gate (Object.) :active (atom {})
                     :queue (ArrayBlockingQueue. (:queue-capacity config))
                     :external (Semaphore. (- (:queue-capacity config) (:reserved-capacity config)))}]
    (loop []
      (when-let [effect (store/next-effect connection :active)]
        (commit! coordinator (event :effect/recovered effect))
        (recur)))
    (let [thread (.start (.daemon (Thread/ofPlatform)) #(work! coordinator))]
      (reset! writer thread)
      (assoc coordinator :thread thread))))

(defn quiesce! [{:keys [gate lifecycle active queue]}]
  (let [started-draining? (locking gate
                            (when (compare-and-set! lifecycle :running :draining)
                              ;; Wake the writer using the reserved control slot.
                              (.offer queue {:control :drain})))]
    (doseq [handle (vals @active)] (effects/cancel! handle))
    (when started-draining?
      (diagnostic! {:type :controller/draining}))))

(defn stop! [{:keys [config gate queue lifecycle thread active external] :as coordinator}]
  (quiesce! coordinator)
  (let [deadline (+ (System/nanoTime) (* 1000000 (:shutdown-timeout-ms config)))]
    (loop []
      (when (and (= :draining @lifecycle)
                 (or (not (.isEmpty queue))
                     (seq @active)
                     (< (.availablePermits external)
                        (- (:queue-capacity config) (:reserved-capacity config))))
                 (< (System/nanoTime) deadline))
        (Thread/sleep 5)
        (recur)))
    (locking gate (reset! lifecycle :stopped))
    (.join thread (Duration/ofNanos (max 0 (- deadline (System/nanoTime)))))
    (when (or (.isAlive thread) (seq @active))
      (diagnostic! {:type :controller/drain-deadline :unresolved-slots (count @active)})))
  (loop []
    (when-let [{:keys [reply command]} (.poll queue)]
      (when reply
        (deliver reply {:status :rejected :reason :shutdown :command-id (:command-id command)}))
      (recur))))

(defn status [{:keys [config lifecycle queue snapshot active]}]
  (let [phase @lifecycle]
    {:version "0.1.0" :ready (= :running phase) :lifecycle (name phase)
     :storage_ready (boolean (#{:running :draining} phase))
     :queue {:capacity (:queue-capacity config) :occupancy (.size queue)
             :reserved (:reserved-capacity config)}
     :effect_capacity (:effect-concurrency config)
     :effect_slots_used (count @active)
     :effects (:effects @snapshot)}))
