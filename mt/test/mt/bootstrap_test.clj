(ns mt.bootstrap-test
  (:require [babashka.fs :as fs]
            [bootstrap :as bootstrap]
            [clojure.test :refer [deftest is]]))

(deftest corrupted-cached-archive-is-not-trusted
  (let [cache (fs/create-temp-dir {:prefix "mt-checksum-test-"})]
    (spit (str (fs/path cache "tool.tar.gz")) "corrupt archive")
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Cached archive checksum mismatch"
                         (bootstrap/archive! cache {:name "tool"
                                                    :url "https://example.invalid/tool.tar.gz"
                                                    :sha256 (apply str (repeat 64 "0"))})))))

(deftest bootstrap-validates-remote-shell-input
  (doseq [[host root] [["dut;command" "/tmp/mt-tcp-feasibility"]
                      ["dut" "/tmp/mt-tcp-feasibility/../other"]
                      ["dut" "/tmp/other"]]]
    (is (thrown? clojure.lang.ExceptionInfo
                 (bootstrap/options ["--ssh-host" host "--root" root
                                     "--lock" "lock.json" "--cache" "cache"])))))
