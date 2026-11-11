(ns mt.tcp-check-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is]]
            [mt.tcp-check :as tcp])
  (:import [java.net Socket]
           [java.io DataOutputStream]))

(deftest clojure-backend-transforms-binary-frames
  (let [backend (tcp/start-backend :echo 256)
        payload (byte-array [0 127 -128 -1])]
    (try
      (with-open [client (Socket. "127.0.0.1" (:port backend))]
        (.setSoTimeout client 1000)
        (tcp/write-frame! client payload)
        (is (= (vec (tcp/transformed payload)) (vec (tcp/read-frame client 256)))))
      (finally (tcp/stop-backend! backend)))
    (is (= [(vec payload)] @(:requests backend)))
    (is (empty? @(:errors backend)))))

(deftest oversized-backend-response-is-rejected
  (let [backend (tcp/start-backend :oversized 16)]
    (try
      (with-open [client (Socket. "127.0.0.1" (:port backend))]
        (.setSoTimeout client 1000)
        (tcp/write-frame! client (byte-array [1]))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Frame exceeds"
                             (tcp/read-frame client 16))))
      (finally (tcp/stop-backend! backend)))))

(deftest negative-length-is-rejected
  (with-open [server (tcp/listener)
              client (Socket. "127.0.0.1" (.getLocalPort server))
              peer (.accept server)]
    (.setSoTimeout peer 1000)
    (doto (DataOutputStream. (.getOutputStream client)) (.writeInt -1) (.flush))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Frame exceeds"
                         (tcp/read-frame peer 16)))))

(deftest earlier-results-are-never-overwritten
  (let [work (fs/create-temp-dir {:prefix "mt-immutable-test-"})
        results (fs/path work "results")]
    (fs/create-dir results)
    (spit (str (fs/path results "sentinel")) "earlier evidence")
    (is (thrown? java.nio.file.FileAlreadyExistsException
                 (tcp/run-probe! work (fs/path work "no-tools"))))
    (is (= "earlier evidence" (slurp (str (fs/path results "sentinel")))))))

(deftest cpu-inventory-is-parsed-without-procfs-file-streams
  (let [path (fs/create-temp-file {:prefix "mt-cpuinfo-test-"})]
    (spit (str path) "processor : 0\nmodel name : Test CPU\nprocessor : 1\n")
    (is (= "Test CPU" (tcp/cpu-model path)))))
