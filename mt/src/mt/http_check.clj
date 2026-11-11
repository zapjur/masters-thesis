(ns mt.http-check
  "HTTP correctness fixtures on DUT loopback; no performance measurements."
  (:require [babashka.fs :as fs] [babashka.process :as process]
            [clojure.string :as str] [mt.tcp-check :as tcp] [mt.quadlet :as quadlet])
  (:import [java.net Socket SocketException SocketTimeoutException]
           [java.io ByteArrayOutputStream]
           [java.security MessageDigest]
           [java.util Arrays]
           [java.util.concurrent TimeUnit]))

(def body (.getBytes "backend-ok\n" "UTF-8"))
(defn bytes [s] (.getBytes s "ISO-8859-1"))
(defn digest [data]
  (apply str (map #(format "%02x" (bit-and 255 %)) (.digest (MessageDigest/getInstance "SHA-256") data))))

(defn request
  ([target] (request target ""))
  ([target headers] (str "GET " target " HTTP/1.1\r\nHost: client.invalid\r\n" headers "\r\n")))

(def cases
  [{:id "allowed" :status 200}
   {:id "query" :status 200 :wire (request "/allowed?x=1") :target "/allowed?x=1"}
   {:id "blocked" :status 403 :wire (request "/blocked")}
   {:id "default_deny" :status 403 :wire (request "/unknown")}
   {:id "first_match" :status 403 :wire (request "/priority")}
   {:id "method_case" :status 403 :wire "get /allowed HTTP/1.1\r\nHost: client.invalid\r\n\r\n"}
   {:id "path_case" :status 403 :wire (request "/Allowed")}
   {:id "encoded_path" :status 403 :wire (request "/%61llowed")}
   {:id "fragmented" :status 200 :fragmented true}
   {:id "zero_length" :status 200 :wire (request "/allowed" "Content-Length: 0\r\n")}
   {:id "body_rejected" :status 400 :wire (str (request "/allowed" "Content-Length: 1\r\n") "x")}
   {:id "conflicting_length" :status 400 :wire (request "/allowed" "Content-Length: 0\r\nContent-Length: 1\r\n")}
   {:id "duplicate_length" :status 400 :wire (request "/allowed" "Content-Length: 0\r\nContent-Length: 0\r\n")}
   {:id "transfer_encoding" :status 400 :wire (request "/allowed" "Transfer-Encoding: chunked\r\nContent-Length: 0\r\n")}
   {:id "ambiguous_line" :status 400 :wire "GET  /allowed HTTP/1.1\r\nHost: client.invalid\r\n\r\n"}
   {:id "http_10" :status 400 :wire "GET /allowed HTTP/1.0\r\nHost: client.invalid\r\n\r\n"}
   {:id "absolute_target" :status 400 :wire "GET http://other.invalid/allowed HTTP/1.1\r\nHost: client.invalid\r\n\r\n"}
   {:id "connect_unsupported" :status 400 :wire "CONNECT other.invalid:80 HTTP/1.1\r\nHost: client.invalid\r\n\r\n"}
   {:id "head_unsupported" :status 400 :wire "HEAD /allowed HTTP/1.1\r\nHost: client.invalid\r\n\r\n"}
   {:id "upgrade_unsupported" :status 400 :wire (request "/allowed" "Upgrade: websocket\r\nConnection: upgrade\r\n")}
   {:id "expect_unsupported" :status 400 :wire (request "/allowed" "Expect: 100-continue\r\n")}
   {:id "missing_host" :status 400 :wire "GET /allowed HTTP/1.1\r\n\r\n"}
   {:id "duplicate_host" :status 400 :wire (request "/allowed" "Host: other.invalid\r\n")}
   {:id "invalid_host" :status 400 :wire "GET /allowed HTTP/1.1\r\nHost: bad host\r\n\r\n"}
   {:id "folded_header" :status 400 :wire (request "/allowed" "X-Test: value\r\n continued\r\n")}
   {:id "bare_lf" :status 400 :wire "GET /allowed HTTP/1.1\nHost: client.invalid\n\n" :eof true}
   {:id "oversized_head" :status 431 :head-size :over}
   {:id "exact_head_limit" :status 200 :head-size :exact}
   {:id "header_count" :status 431 :many-headers true}
   {:id "exact_header_count" :status 200 :exact-headers true}
   {:id "request_hop_headers" :status 200 :wire (request "/allowed" "Connection: x-secret, keep-alive\r\nX-Secret: hidden\r\nX-Keep: yes\r\nKeep-Alive: timeout=5\r\n") :check-hop true}
   {:id "critical_connection_token" :status 400 :wire (request "/allowed" "Connection: content-length\r\n")}
   {:id "response_hop_headers" :status 200 :mode :hop}
   {:id "backend_refused" :status 502 :mode :refused}
   {:id "backend_timeout" :status 502 :mode :stall}
   {:id "backend_malformed" :status 502 :mode :malformed}
   {:id "backend_oversized_body" :status 502 :mode :oversized}
   {:id "backend_incomplete_body" :status 502 :mode :incomplete}
   {:id "backend_missing_length" :status 502 :mode :missing-length}
   {:id "backend_duplicate_length" :status 502 :mode :duplicate-length}
   {:id "backend_transfer_encoding" :status 502 :mode :transfer-encoding}
   {:id "backend_header_limit" :status 502 :mode :oversized-head}
   {:id "backend_non_200" :status 502 :mode :non-200}
   {:id "request_timeout" :status nil :wire "GET /allowed HTTP/1.1\r\nHo"}
   {:id "incomplete_request" :status 400 :wire "GET /allowed HTTP/1.1\r\nHo" :eof true}
   {:id "pipelined_input" :status 200 :wire (str (request "/allowed") (request "/blocked"))}
   {:id "maximum_response" :status 200 :mode :maximum}])

(defn read-head! [socket limit]
  (let [in (.getInputStream socket) output (ByteArrayOutputStream.)]
    (loop [suffix ""]
      (let [value (.read in)]
        (tcp/check! (not= -1 value) "Fixture received incomplete HTTP head")
        (.write output value)
        (tcp/check! (<= (.size output) limit) "Fixture head limit exceeded")
        (let [suffix (str suffix (char value))
              suffix (subs suffix (max 0 (- (count suffix) 4)))]
          (if (= suffix "\r\n\r\n") (.toString output "ISO-8859-1") (recur suffix)))))))

(defn parse-head [head]
  (let [lines (str/split head #"\r\n")]
    {:line (first lines)
     :headers (mapv (fn [line]
                      (let [[name value] (str/split line #":" 2)]
                        [(str/lower-case name) (str/trim (or value ""))])) (rest lines))}))

(defn backend-response! [socket mode scenario]
  (let [out (.getOutputStream socket)
        payload (if (= mode :maximum)
                  (byte-array (map #(unchecked-byte (mod % 256)) (range (:max-body-bytes scenario)))) body)
        head (case mode
               :malformed "not HTTP\r\n\r\n"
               :oversized (str "HTTP/1.1 200 OK\r\nContent-Length: " (inc (:max-body-bytes scenario)) "\r\n\r\n")
               :oversized-head (str "HTTP/1.1 200 OK\r\nX-Pad: " (apply str (repeat (:max-head-bytes scenario) "x")) "\r\n\r\n")
               :missing-length "HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n"
               :duplicate-length "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nContent-Length: 0\r\n\r\n"
               :transfer-encoding "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nTransfer-Encoding: chunked\r\n\r\n"
               :incomplete "HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\n"
               :non-200 "HTTP/1.1 503 Unavailable\r\nContent-Length: 0\r\n\r\n"
               (str "HTTP/1.1 200 OK\r\nContent-Length: " (alength payload) "\r\nX-Backend: fixture\r\n"
                    (when (= mode :hop) "Connection: x-secret\r\nX-Secret: hidden\r\nKeep-Alive: timeout=5\r\n") "\r\n"))]
    (.write out (bytes head))
    (when (#{:normal :hop :maximum :incomplete} mode) (.write out ^bytes payload))
    (.flush out)))

(defn start-backend [mode scenario]
  (let [server (tcp/listener) stop (promise) requests (atom []) errors (atom [])
        worker (future
                 (try
                   (loop []
                     (when-not (realized? stop)
                       (when-let [client (try (.accept server) (catch SocketTimeoutException _ nil))]
                         (with-open [socket client]
                           (.setSoTimeout socket 3000)
                           (let [head (parse-head (read-head! socket 16384))
                                 ready? (= "GET /ready HTTP/1.1" (:line head))]
                             (swap! requests conj head)
                             (if (and (= mode :stall) (not ready?))
                               (deref stop 3000 nil)
                               (backend-response! socket (if ready? :normal mode) scenario)))))
                       (recur)))
                   (catch Exception e (swap! errors conj (tcp/trace e)))))]
    {:server server :port (.getLocalPort server) :stop stop :worker worker :requests requests :errors errors}))

(defn read-response! [socket limit]
  (let [in (.getInputStream socket) output (ByteArrayOutputStream.) buffer (byte-array 4096)]
    (loop []
      (let [n (try (.read in buffer)
                   (catch SocketException e
                     (if (re-find #"(?i)reset" (or (.getMessage e) "")) -1 (throw e))))]
        (if (= -1 n) (.toByteArray output)
            (do (.write output buffer 0 n)
                (tcp/check! (<= (.size output) limit) "Unbounded firewall response") (recur)))))))

(defn response [wire]
  (when (pos? (alength wire))
    (let [text (String. ^bytes wire "ISO-8859-1") end (.indexOf text "\r\n\r\n")]
      (tcp/check! (not= -1 end) "Incomplete firewall response head")
      (let [head (parse-head (subs text 0 (+ end 4)))
            code (second (re-matches #"HTTP/1\.1 ([0-9]{3}) .*" (:line head)))
            body (Arrays/copyOfRange ^bytes wire (+ end 4) (alength wire))
            lengths (filter #(= "content-length" (first %)) (:headers head))
            headers (into {} (:headers head))]
        (tcp/check! (and code (= 1 (count lengths))) "Ambiguous firewall response")
        (tcp/check! (= (str (alength body)) (get headers "content-length")) "Invalid response framing")
        (tcp/check! (= "close" (get headers "connection")) "Firewall did not close connection")
        {:status (parse-long code) :body body :headers headers}))))

(defn exchange! [proc port deadline wire scenario & [fragmented? eof?]]
  (with-open [socket (tcp/connect-ready proc port deadline)]
    (.setSoTimeout socket (* 1000 (:process-timeout-seconds scenario)))
    (let [out (.getOutputStream socket) data (bytes wire)]
      (if fragmented?
        (doseq [[start end] [[0 1] [1 10] [10 (alength data)]]]
          (.write out ^bytes data start (- end start)) (.flush out) (Thread/sleep 10))
        (.write out ^bytes data))
      (.flush out)
      (when eof? (.shutdownOutput socket))
      (read-response! socket (+ (:max-head-bytes scenario) (:max-body-bytes scenario) 4096)))))

(defn wire-for [case scenario]
  (cond
    (:head-size case) (let [base (request "/allowed" "X-Pad: \r\n")
                            size (+ (:max-head-bytes scenario) (if (= :over (:head-size case)) 1 0))]
                        (request "/allowed" (str "X-Pad: " (apply str (repeat (- size (count base)) "x")) "\r\n")))
    (:many-headers case) (request "/allowed" (apply str (repeat (:max-headers scenario) "X-Test: x\r\n")))
    (:exact-headers case) (request "/allowed" (apply str (repeat (dec (:max-headers scenario)) "X-Test: x\r\n")))
    :else (or (:wire case) (request "/allowed"))))

(defn verify-backend! [case backend]
  (let [forwarded? (or (= 200 (:status case)) (and (= 502 (:status case)) (not= :refused (:mode case))))
        seen @(:requests backend)]
    (tcp/check! (= (if forwarded? 1 0) (count seen)) "Unexpected backend request count")
    (when forwarded?
      (let [head (first seen) headers (into {} (:headers head))]
        (tcp/check! (= (str "GET " (or (:target case) "/allowed") " HTTP/1.1") (:line head)) "Request target changed")
        (tcp/check! (= (str "127.0.0.1:" (:port backend)) (get headers "host")) "Backend Host was not rewritten")
        (tcp/check! (= "0" (get headers "content-length")) "Forwarded request is not bodyless")
        (tcp/check! (= "close" (get headers "connection")) "Backend connection not explicitly closed")
        (when (:check-hop case)
          (tcp/check! (and (nil? (get headers "x-secret")) (nil? (get headers "keep-alive"))
                           (= "yes" (get headers "x-keep"))) "Hop-by-hop request headers leaked"))))))

(defn run-case! [command case output scenario]
  (let [execution (if (fn? command) (command case output scenario) {:argv command})
        backend (start-backend (or (:mode case) :normal) scenario)
        port (with-open [listener (tcp/listener)] (.getLocalPort listener))
        argv (into (:argv execution) (concat [(str "127.0.0.1:" port) (str "127.0.0.1:" (:port backend))
                                    (str (:io-timeout-ms scenario)) (str (:accept-timeout-ms scenario))
                                    (str (:max-head-bytes scenario)) (str (:max-headers scenario))
                                    (str (:max-body-bytes scenario)) (str (:max-connections scenario))] (:policy scenario)))
        name (:id case) proc (atom nil)
        result (atom {:case name :started_at (tcp/utc) :command argv :status "failed"})]
    (try
      (reset! proc (process/process argv {:out (fs/file output (str name ".stdout"))
                                         :err (fs/file output (str name ".stderr")) :shutdown process/destroy-tree}))
      (let [deadline (+ (System/nanoTime) (* 1000000000 (:process-timeout-seconds scenario)))
            _ (when-let [await-start (:await-start execution)] (await-start @proc deadline))
            ready (response (exchange! @proc port deadline (request "/ready") scenario))
            denied (response (exchange! @proc port deadline (request "/blocked") scenario))]
        (tcp/check! (and (= 200 (:status ready)) (Arrays/equals ^bytes body ^bytes (:body ready))
                         (= 403 (:status denied)) (= 1 (count @(:requests backend)))) "Active readiness failed")
        (reset! (:requests backend) [])
        (when (= :refused (:mode case)) (tcp/stop-backend! backend))
        (let [wire (exchange! @proc port deadline (wire-for case scenario) scenario (:fragmented case) (:eof case))
              parsed (response wire)
              code (:exit (tcp/wait-process! @proc (quot (- deadline (System/nanoTime)) 1000000)))]
          (with-open [file (clojure.java.io/output-stream (fs/file output (str name ".response.bin")))] (.write file ^bytes wire))
          (tcp/check! (zero? code) "Firewall did not exit cleanly after its connection budget")
          (tcp/check! (= (:status case) (:status parsed)) (str "Unexpected HTTP status: " (:status parsed)))
          (when (= 200 (:status case))
            (let [expected (if (= :maximum (:mode case))
                             (byte-array (map #(unchecked-byte (mod % 256)) (range (:max-body-bytes scenario)))) body)]
              (tcp/check! (Arrays/equals ^bytes expected ^bytes (:body parsed)) "Backend response body changed")))
          (when (= :hop (:mode case))
            (tcp/check! (and (nil? (get-in parsed [:headers "x-secret"])) (nil? (get-in parsed [:headers "keep-alive"])))
                        "Hop-by-hop response headers leaked"))
          (verify-backend! case backend)
          (tcp/stop-backend! backend)
          (tcp/check! (empty? @(:errors backend)) (str "Backend fixture error: " @(:errors backend)))
          (swap! result assoc :status "passed" :exit_code code :http_status (:status parsed)
                 :active_readiness true :backend_requests (count @(:requests backend)) :response_sha256 (digest wire))))
      (catch Exception e (swap! result assoc :error (tcp/trace e)))
      (finally
        (when (and @proc (.isAlive (:proc @proc)))
          (process/destroy-tree @proc)
          (when-not (.waitFor (:proc @proc) 1000 TimeUnit/MILLISECONDS)
            (.destroyForcibly (:proc @proc)) (.waitFor (:proc @proc))))
        (try (tcp/stop-backend! backend) (catch Exception e (swap! result assoc :status "failed" :cleanup_error (tcp/trace e))))
        (when-let [cleanup (:cleanup execution)]
          (try (cleanup) (catch Exception e (swap! result assoc :status "failed" :cleanup_error (tcp/trace e)))))
        (tcp/write-json! (fs/path output (str name ".backend.json")) @(:requests backend))
        (tcp/write-json! (fs/path output (str name ".json")) (assoc @result :finished_at (tcp/utc)))))
    @result))

(defn run-firewall! [work tools]
  (let [output (fs/path work "results") _ (fs/create-dir output {:posix-file-permissions "rwx------"})
        scenario (tcp/read-json (fs/path work "scenario.json"))
        metadata (atom (merge (tcp/read-json (fs/path work "provenance.json"))
                             {:started_at (tcp/utc) :scenario scenario :kind "functional-correctness"
                              :kernel (System/getProperty "os.version") :architecture (System/getProperty "os.arch")
                              :babashka (System/getProperty "babashka.version") :backend "Clojure/Babashka HTTP loopback fixture"}))
        summary (atom {:status "failed" :expected_case_count (* (count cases) (count (:variants scenario))) :cases []})]
    (try
      (let [lock (tcp/read-json (fs/path tools "lock.json"))
            compiler (str (fs/path tools "bin/rustc")) runtime (str (fs/path tools "bin/wasmtime"))
            linker (str (fs/path tools "lib/rustlib/x86_64-unknown-linux-gnu/bin/rust-lld"))]
        (tcp/check! (= lock (tcp/read-json (fs/path work "toolchains.lock.json"))) "Prepared toolchain lock differs")
        (doseq [[path source-key] [["firewall/src/main.rs" "implementations/firewall/src/main.rs"]
                                 ["firewall/src/http.rs" "implementations/firewall/src/http.rs"]
                                 ["firewall/dependencies.lock.json" "implementations/firewall/dependencies.lock.json"]
                                 ["mt/http_check.clj" "mt/src/mt/http_check.clj"] ["mt/tcp_check.clj" "mt/src/mt/tcp_check.clj"]]]
          (tcp/check! (= (tcp/sha256 (fs/path work path)) (get-in @metadata [:source_sha256 (keyword source-key)])) "Deployed source hash mismatch"))
        (swap! metadata assoc :toolchains lock :dependency (tcp/read-json (fs/path work "firewall/dependencies.lock.json"))
               :cases cases :runtime_binary_sha256 (tcp/sha256 runtime)
               :fixture_binary_sha256 (tcp/sha256 (fs/path tools "bin/bb")))
        (swap! metadata assoc
               :cpu_model (tcp/cpu-model "/proc/cpuinfo")
               :rustc (tcp/capture! [compiler "-vV"] (fs/path output "rustc-version.txt"))
               :wasmtime (tcp/capture! [runtime "--version"] (fs/path output "wasmtime-version.txt")))
        (when (:container scenario)
          (swap! metadata assoc :container_environment (quadlet/preflight! output (:container scenario))))
        (doseq [variant (remove #{"podman"} (:order scenario))]
          (let [target (get lock (if (= variant "native") :native_target :wasm_target))
                library (str (fs/path output (str "libhttparse-" variant ".rlib")))
                binary (str (fs/path output (if (= variant "native") "firewall-native" "firewall.wasm")))
                parser-build [compiler "--edition=2018" "--crate-name" "httparse" "--crate-type" "rlib"
                              (str (fs/path work "httparse/src/lib.rs")) "--target" target "-C" "opt-level=2" "-C" "panic=abort" "-o" library]
                common [compiler "--edition=2024" "--crate-name" "mt_l7_firewall" (str (fs/path work "firewall/src/main.rs"))
                        "--target" target "--extern" (str "httparse=" library) "-C" "opt-level=2"]
                link (if (= variant "native") ["-C" (str "linker=" linker) "-C" "link-self-contained=yes"] [])
                build (into common (concat ["-C" "panic=abort" "-o" binary] link))
                case-output (fs/path output variant)
                command (if (= variant "native") [binary] [runtime "run" "-S" "inherit-network=y" binary])]
            (swap! metadata assoc-in [:builds variant] {:parser_command parser-build :command build :target target})
            (tcp/capture! parser-build (fs/path output (str variant "-parser-build.txt")))
            (tcp/capture! build (fs/path output (str variant "-build.txt")))
            (swap! metadata assoc-in [:builds variant :artifact_sha256] (tcp/sha256 binary))
            (when (= variant "native")
              (let [test-binary (str (fs/path output "http-unit-tests"))
                    ;; libtest uses unwinding: compile the parser again with the matching panic strategy.
                    test-library (str (fs/path output "libhttparse-test.rlib"))]
                ;; Build explicitly instead of mutating the recorded production command.
                (tcp/capture! [compiler "--edition=2018" "--crate-name" "httparse" "--crate-type" "rlib"
                               (str (fs/path work "httparse/src/lib.rs")) "--target" target "-o" test-library]
                              (fs/path output "unit-parser-build.txt"))
                (tcp/capture! (into [compiler "--edition=2024" "--test" (str (fs/path work "firewall/src/main.rs"))
                                    "--target" target "--extern" (str "httparse=" test-library) "-o" test-binary] link)
                              (fs/path output "unit-build.txt"))
                (tcp/capture! [test-binary "--test-threads=1"] (fs/path output "unit-tests.txt"))))
            (fs/create-dir case-output)
            (doseq [case cases]
              (let [result (run-case! command case case-output scenario)]
                (swap! summary update :cases conj (assoc result :variant variant))
                (println (str variant ": " (:id case) ": " (:status result))) (flush)))))
        (when (:container scenario)
          (let [image (quadlet/prepare-image! work output (fs/path output "firewall-native") (:container scenario))
                case-output (fs/path output "podman")]
            (swap! metadata assoc :container_image image)
            (tcp/check! (= (:binary_sha256 image) (get-in @metadata [:builds "native" :artifact_sha256]))
                        "Container and native artifacts differ")
            (try
              (fs/create-dir case-output)
              (doseq [case cases]
                (let [result (run-case! (quadlet/command-factory work tools image (:container scenario)) case case-output scenario)]
                  (swap! summary update :cases conj (assoc result :variant "podman"))
                  (println (str "podman: " (:id case) ": " (:status result))) (flush)
                  (tcp/check! (= "passed" (:status result))
                              (str "Podman case failed; remaining container cases not run: " (:id case)))))
              (swap! metadata assoc :container_recovery (quadlet/recovery-check! work tools image output scenario))
              (finally
                (tcp/capture! ["podman" "rmi" (:tag image)] (fs/path output "podman-image-cleanup.txt"))))))
        (let [pairs (group-by :case (:cases @summary)) variants (set (:variants scenario))]
          (tcp/check! (= (count cases) (count pairs)) "Missing HTTP cases")
          (doseq [[name results] pairs]
            (tcp/check! (and (= (count variants) (count results))
                             (= variants (set (map :variant results)))
                             (every? #(= "passed" (:status %)) results)
                             (= 1 (count (set (map :response_sha256 results)))))
                        (str "Firewall response parity failed: " name))))
        (swap! summary assoc :status "passed" :response_parity true))
      (catch Exception e (swap! summary assoc :error (tcp/trace e)))
      (finally
        (tcp/write-json! (fs/path output "metadata.json") (assoc @metadata :finished_at (tcp/utc)))
        (tcp/write-json! (fs/path output "summary.json") @summary)))
    (if (= "passed" (:status @summary)) 0 1)))

(defn -main [& args]
  (tcp/check! (and (= 4 (count args)) (= "--work" (first args)) (= "--tools" (nth args 2))) "Expected --work PATH --tools PATH")
  (System/exit (run-firewall! (fs/absolutize (second args)) (fs/absolutize (nth args 3)))))
