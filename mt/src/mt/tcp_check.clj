(ns mt.tcp-check
  "DUT-side functional fixtures, executed by pinned Babashka. Not a load generator."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.stacktrace :as stacktrace]
            [clojure.string :as str])
  (:import [java.net InetAddress InetSocketAddress ServerSocket Socket SocketException SocketTimeoutException]
           [java.io DataInputStream DataOutputStream]
           [java.security MessageDigest]
           [java.time Instant]
           [java.util Arrays]
           [java.util.concurrent TimeUnit]))

(def cases ["roundtrip" "fragmented" "empty" "maximum" "request_timeout"
            "backend_timeout" "backend_refused" "oversized_request"
            "oversized_response" "incomplete_request" "accept_timeout" "supervisor_cleanup"])

(defn utc [] (str (Instant/now)))
(defn check! [condition message]
  (when-not condition (throw (ex-info message {}))))
(defn trace [exception] (with-out-str (stacktrace/print-cause-trace exception)))
(defn write-json! [path data]
  (spit (str path) (str (json/generate-string data {:pretty true}) "\n")))
(defn read-json [path] (json/parse-string (slurp (str path)) true))

(defn cpu-model [path]
  ;; Native-image FileInputStream.available fails on this DUT's procfs.
  ;; Read through coreutils rather than silently omitting hardware provenance.
  (let [result @(process/process ["cat" (str path)] {:out :string :err :string})]
    (check! (zero? (:exit result)) "Unable to collect CPU inventory")
    (or (some #(when (str/starts-with? % "model name")
                 (str/trim (second (str/split % #":" 2)))) (str/split-lines (:out result)))
        "unavailable")))

(defn sha256 [path]
  (with-open [in (clojure.java.io/input-stream (fs/file path))]
    (let [digest (MessageDigest/getInstance "SHA-256") buffer (byte-array 65536)]
      (loop []
        (let [n (.read in buffer)]
          (when (pos? n) (.update digest buffer 0 n) (recur))))
      (apply str (map #(format "%02x" (bit-and 255 %)) (.digest digest))))))

(defn listener []
  (doto (ServerSocket. 0 50 (InetAddress/getByName "127.0.0.1")) (.setSoTimeout 50)))

(defn read-frame [socket limit]
  (let [in (DataInputStream. (.getInputStream socket)) n (.readInt in)]
    (check! (<= 0 n limit) "Frame exceeds fixture limit")
    (let [payload (byte-array n)] (.readFully in payload) payload)))

(defn write-frame! [socket payload]
  (let [out (DataOutputStream. (.getOutputStream socket))]
    (.writeInt out (alength payload))
    (.write out ^bytes payload)
    (.flush out)))

(defn transformed [payload]
  (byte-array (map #(unchecked-byte (bit-xor 0xa5 (bit-and 255 %))) payload)))

(defn start-backend [mode limit]
  (let [server (listener) stop (promise) requests (atom []) errors (atom [])
        worker (when-not (= mode :refused)
                 (future
                   (try
                     (loop []
                       (when-not (realized? stop)
                         (when-let [client (try (.accept server) (catch SocketTimeoutException _ nil))]
                           (with-open [socket client]
                             (.setSoTimeout socket 3000)
                             (let [payload (read-frame socket limit)]
                               (swap! requests conj (vec payload))
                               (case mode
                                 :stall (deref stop 3000 nil)
                                 :oversized (doto (DataOutputStream. (.getOutputStream socket))
                                              (.writeInt (inc limit)) (.flush))
                                 (write-frame! socket (transformed payload))))))
                         (recur)))
                     (catch Exception e (swap! errors conj (trace e))))))
        port (.getLocalPort server)]
    (when (= mode :refused) (.close server))
    {:server server :port port :stop stop :requests requests :errors errors :worker worker}))

(defn stop-backend! [{:keys [server stop worker]}]
  (deliver stop true)
  (when (and worker (= ::timeout (deref worker 4000 ::timeout)))
    (.close server)
    (future-cancel worker)
    (throw (ex-info "Backend fixture did not stop" {})))
  (.close server))

(defn wait-process! [proc timeout-ms]
  (when-not (.waitFor (:proc proc) (long (max 1 timeout-ms)) TimeUnit/MILLISECONDS)
    (process/destroy-tree proc)
    (when-not (.waitFor (:proc proc) 1000 TimeUnit/MILLISECONDS)
      (.destroyForcibly (:proc proc)))
    (throw (ex-info "Supervisor process deadline exceeded" {})))
  @proc)

(defn connect-ready [proc port deadline]
  (loop []
    (check! (.isAlive (:proc proc)) "Forwarder exited before accepting test traffic")
    (check! (< (System/nanoTime) deadline) "Forwarder did not accept traffic before deadline")
    (let [socket (Socket.)
          connected? (try (.connect socket (InetSocketAddress. "127.0.0.1" port) 100) true
                          (catch java.net.ConnectException _ false)
                          (catch SocketTimeoutException _ false))]
      (if connected? socket
          (do (.close socket) (Thread/sleep 10) (recur))))))

(def success-cases #{"roundtrip" "fragmented" "empty" "maximum"})
(def diagnostics {"request_timeout" "phase=read_request kind=timeout"
                  "backend_timeout" "phase=read_backend kind=timeout"
                  "backend_refused" "phase=connect_backend kind=connection_refused"
                  "oversized_request" "phase=read_request kind=invalid_input"
                  "oversized_response" "phase=read_backend kind=invalid_input"
                  "incomplete_request" "phase=read_request kind=incomplete_frame"
                  "accept_timeout" "phase=accept kind=timeout"})

(defn exchange! [client proc name payload scenario]
  (.setSoTimeout client (* 1000 (:process-timeout-seconds scenario)))
  (let [out (DataOutputStream. (.getOutputStream client))]
    (case name
      ("request_timeout" "supervisor_cleanup") (.write out (byte-array [0 0]))
      "incomplete_request" (do (.writeInt out 10) (.write out (.getBytes "short" "UTF-8"))
                               (.flush out) (.shutdownOutput client))
      "oversized_request" (.writeInt out (inc (:max-bytes scenario)))
      "fragmented" (do
                     ;; Split both the length header and payload across writes.
                     (.writeByte out 0) (.flush out) (Thread/sleep 10)
                     (.write out (byte-array [0 0])) (.flush out) (Thread/sleep 10)
                     (.writeByte out (alength payload)) (.flush out) (Thread/sleep 10)
                     (.write out ^bytes payload 0 2) (.flush out) (Thread/sleep 10)
                     (.write out ^bytes payload 2 (- (alength payload) 2)))
      (write-frame! client payload))
    (.flush out)
    (when (= name "supervisor_cleanup") (process/destroy-tree proc))
    (if (success-cases name)
      (do (check! (Arrays/equals ^bytes (transformed payload)
                                ^bytes (read-frame client (:max-bytes scenario)))
                  "Response differs from backend transformation")
          (check! (= -1 (.read (.getInputStream client))) "Unexpected trailing response bytes"))
      (try
        (check! (= -1 (.read (.getInputStream client))) "Failure case returned response bytes")
        (catch SocketException e
          (when-not (re-find #"(?i)reset" (or (.getMessage e) "")) (throw e)))))))

(defn run-case! [command name output scenario]
  (let [limit (:max-bytes scenario)
        mode (get {"backend_timeout" :stall "oversized_response" :oversized
                   "backend_refused" :refused} name :echo)
        backend (start-backend mode limit)
        port (with-open [s (listener)] (.getLocalPort s))
        argv (into command [(str "127.0.0.1:" port) (str "127.0.0.1:" (:port backend))
                            (str (:io-timeout-ms scenario)) (str (:accept-timeout-ms scenario)) (str limit)])
        result (atom {:case name :started_at (utc) :command argv :status "failed"})
        proc (atom nil)]
    (try
      (reset! proc (process/process argv {:out (fs/file output (str name ".stdout"))
                                         :err (fs/file output (str name ".stderr"))
                                         :shutdown process/destroy-tree}))
      (let [deadline (+ (System/nanoTime) (* 1000000000 (:process-timeout-seconds scenario)))
            payload (case name
                      "maximum" (byte-array (map #(unchecked-byte (mod % 256)) (range limit)))
                      "empty" (byte-array 0)
                      (byte-array (concat (.getBytes "tcp-probe" "UTF-8") [0 -1])))]
        (when-not (= name "accept_timeout")
          (with-open [client (connect-ready @proc port deadline)]
            (exchange! client @proc name payload scenario)))
        (let [code (:exit (wait-process! @proc (quot (- deadline (System/nanoTime)) 1000000)))]
          (stop-backend! backend)
          (check! (if (= name "supervisor_cleanup") (not= 0 code)
                      (= code (if (success-cases name) 0 1))) "Unexpected process exit")
          (when-let [expected (or (diagnostics name) (when (success-cases name) "event=forwarded"))]
            (check! (str/includes? (slurp (str (fs/path output (str name ".stderr")))) expected)
                    (str "Missing diagnostic: " expected)))
          (let [forwarded? (or (success-cases name) (#{"backend_timeout" "oversized_response"} name))]
            (check! (= @(:requests backend) (if forwarded? [(vec payload)] []))
                    "Backend saw unexpected or altered requests"))
          (check! (empty? @(:errors backend)) (str "Backend fixture error: " @(:errors backend)))
          (with-open [socket (Socket.)]
            (check! (try (.connect socket (InetSocketAddress. "127.0.0.1" port) 100) false
                         (catch java.net.ConnectException _ true))
                    "Listener remains after process exit"))
          (swap! result assoc :status "passed" :exit_code code :backend_requests (count @(:requests backend)))))
      (catch Exception e (swap! result assoc :error (trace e)))
      (finally
        (when (and @proc (.isAlive (:proc @proc)))
          (process/destroy-tree @proc)
          (when-not (.waitFor (:proc @proc) 1000 TimeUnit/MILLISECONDS)
            (.destroyForcibly (:proc @proc))
            (.waitFor (:proc @proc))))
        (try (stop-backend! backend)
             (catch Exception e (swap! result assoc :status "failed" :cleanup_error (trace e))))
        (swap! result assoc :finished_at (utc))
        (when @proc (swap! result assoc :exit_code (.exitValue (:proc @proc))))
        (write-json! (fs/path output (str name ".json")) @result)))
    @result))

(defn capture! [argv output]
  (let [proc (process/process argv {:out (fs/file output) :err :out :shutdown process/destroy-tree})
        result (wait-process! proc 120000)]
    (check! (zero? (:exit result)) (str "Command failed; see " (fs/file-name output)))
    (str/trim (slurp (str output)))))

(defn run-probe! [work tools]
  (let [output (fs/path work "results")
        _ (fs/create-dir output {:posix-file-permissions "rwx------"})
        scenario (read-json (fs/path work "scenario.json"))
        metadata (atom (merge (read-json (fs/path work "provenance.json"))
                              {:started_at (utc) :scenario scenario :kind "functional-feasibility"
                               :os (System/getProperty "os.name") :kernel (System/getProperty "os.version")
                               :architecture (System/getProperty "os.arch")
                               :babashka (System/getProperty "babashka.version")
                               :backend "Clojure/Babashka loopback fixture"}))
        summary (atom {:status "failed" :cases []})]
    (try
      (swap! metadata assoc :cpu_model (cpu-model "/proc/cpuinfo"))
      (let [lock (read-json (fs/path tools "lock.json"))
            compiler (str (fs/path tools "bin/rustc")) runtime (str (fs/path tools "bin/wasmtime"))]
        (check! (= lock (read-json (fs/path work "toolchains.lock.json"))) "Prepared toolchain lock differs")
        (doseq [[filename source-key] [["main.rs" "implementations/feasibility/tcp-forwarder/src/main.rs"]
                                      ["check.clj" "mt/src/mt/tcp_check.clj"]
                                      ["toolchains.lock.json" "infra/feasibility/toolchains.lock.json"]]]
          (check! (= (sha256 (fs/path work filename)) (get-in @metadata [:source_sha256 (keyword source-key)]))
                  (str "Deployed source hash mismatch: " filename)))
        (swap! metadata assoc :toolchains lock
               :rustc (capture! [compiler "-vV"] (fs/path output "rustc-version.txt"))
               :wasmtime (capture! [runtime "--version"] (fs/path output "wasmtime-version.txt"))
               :runtime_binary_sha256 (sha256 runtime)
               :fixture_binary_sha256 (sha256 (fs/path tools "bin/bb")))
        (doseq [variant (:order scenario)]
          (let [target (get lock (if (= variant "native") :native_target :wasm_target))
                binary (str (fs/path output (if (= variant "native") "probe-native" "probe.wasm")))
                argv (cond-> [compiler "--edition=2024" "--crate-name" "mt_tcp_probe" (str (fs/path work "main.rs"))
                              "--target" target "-C" "opt-level=2" "-C" "panic=abort" "-o" binary]
                       (= variant "native") (into ["-C" (str "linker=" (fs/path tools "lib/rustlib/x86_64-unknown-linux-gnu/bin/rust-lld"))
                                                   "-C" "link-self-contained=yes"]))
                command (if (= variant "native") [binary] [runtime "run" "-S" "inherit-network=y" binary])
                case-output (fs/path output variant)]
            (swap! metadata assoc-in [:builds variant] {:target target :command argv})
            (capture! argv (fs/path output (str variant "-build.txt")))
            (swap! metadata assoc-in [:builds variant :artifact_sha256] (sha256 binary))
            (fs/create-dir case-output)
            (doseq [name cases]
              (let [result (run-case! command name case-output scenario)]
                (swap! summary update :cases conj (assoc result :variant variant))
                (println (str variant ": " name ": " (:status result)))
                (flush)))))
        (swap! summary assoc :status (if (every? #(= "passed" (:status %)) (:cases @summary)) "passed" "failed")))
      (catch Exception e (swap! summary assoc :error (trace e)))
      (finally
        (write-json! (fs/path output "metadata.json") (assoc @metadata :finished_at (utc)))
        (write-json! (fs/path output "summary.json") @summary)))
    (if (= "passed" (:status @summary)) 0 1)))

(defn -main [& args]
  (check! (and (= 4 (count args)) (= "--work" (first args)) (= "--tools" (nth args 2)))
          "Usage: bb check.clj --work PATH --tools PATH")
  (System/exit (run-probe! (fs/absolutize (second args)) (fs/absolutize (nth args 3)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
