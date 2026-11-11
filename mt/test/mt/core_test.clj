(ns mt.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [mt.core :as mt]))

(deftest process-output-modes
  (testing "captured version output is returned as a string"
    (is (re-find #"babashka" (mt/command! ["bb" "--version"]))))
  (testing "successful inherited output does not fail after the remote run"
    (is (= "" (mt/command! ["bb" "-e" "nil"] {:out :inherit :err :inherit}))))
  (testing "nonzero exit remains a failure"
    (is (thrown? clojure.lang.ExceptionInfo
                 (mt/command! ["bb" "-e" "(System/exit 7)"])))))

(deftest remote-shell-boundary
  (is (= {:host "dut-alias" :root "/tmp/mt-tcp-feasibility"}
         (mt/options ["--ssh-host" "dut-alias"])))
  (doseq [host ["-oProxyCommand=bad" "dut;bad" "dut bad" "$(bad)" "dut:22"]]
    (is (thrown? clojure.lang.ExceptionInfo (mt/options ["--ssh-host" host]))))
  (doseq [root ["/" "/tmp/mt-tcp-feasibility/../elsewhere" "/tmp/mt-tcp-feasibility;bad"]]
    (is (thrown? clojure.lang.ExceptionInfo
                 (mt/options ["--ssh-host" "dut" "--remote-root" root])))))
