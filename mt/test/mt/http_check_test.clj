(ns mt.http-check-test
  (:require [clojure.test :refer [deftest is]] [mt.http-check :as http] [mt.tcp-check :as tcp])
  (:import [java.net Socket]))

(deftest response-fixture-preserves-binary-data
  (let [wire (byte-array (concat (http/bytes "HTTP/1.1 200 OK\r\nContent-Length: 4\r\nConnection: close\r\n\r\n")
                                 [0 127 -128 -1]))
        parsed (http/response wire)]
    (is (= 200 (:status parsed)))
    (is (= [0 127 -128 -1] (vec (:body parsed))))))

(deftest response-assertions-reject-bad-framing
  (doseq [wire ["HTTP/1.1 200 OK\r\nContent-Length: 1\r\nConnection: close\r\n\r\nwrong"
                "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"]]
    (is (thrown? clojure.lang.ExceptionInfo (http/response (http/bytes wire))))))

(deftest fixture-head-reader-does-not-consume-body
  (with-open [server (tcp/listener) client (Socket. "127.0.0.1" (.getLocalPort server)) peer (.accept server)]
    (.setSoTimeout peer 1000)
    (.write (.getOutputStream client) (byte-array (concat (http/bytes (http/request "/allowed")) [42])))
    (.flush (.getOutputStream client))
    (is (= (http/request "/allowed") (http/read-head! peer 8192)))
    (is (= 42 (.read (.getInputStream peer))))))

(deftest boundary-cases-use-exact-request-sizes
  (is (= 8192 (count (http/wire-for {:head-size :exact} {:max-head-bytes 8192}))))
  (is (= 8193 (count (http/wire-for {:head-size :over} {:max-head-bytes 8192})))))
