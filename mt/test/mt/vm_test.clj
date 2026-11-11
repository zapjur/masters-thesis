(ns mt.vm-test
  (:require [clojure.test :refer [deftest is]]
            [babashka.process :as process]
            [mt.firewall :as firewall]
            [mt.vm :as vm]))

(deftest guest-command-preserves-shell-sensitive-arguments
  (let [args ["plain" "two words" "it's literal" "$(false)" ";exit 9" ""]
        command (str "printf '%s\\n' " (clojure.string/join " " (map vm/shell-word args)))
        result @(process/process ["sh" "-c" command] {:out :string :err :string})]
    (is (zero? (:exit result)))
    (is (= (str (clojure.string/join "\n" args) "\n") (:out result)))))

(deftest variant-flags-are-independent-and-unique
  (is (= {:host "dut" :root "/tmp/mt-tcp-feasibility" :podman? true :vm? true}
         (firewall/options ["--with-vm" "--ssh-host" "dut" "--with-podman"])))
  (doseq [flag ["--with-vm" "--with-podman"]]
    (is (thrown? clojure.lang.ExceptionInfo (firewall/options ["--ssh-host" "dut" flag flag])))))

(deftest vm-application-endpoints-keep-host-fixtures-outside-guest
  (let [guest {:ssh ["ssh" "guest"] :http-port 45678
               :lock {:guest_firewall_port 8080 :guest_backend_host "10.0.2.2"}}
        execution ((vm/command-factory guest) {} nil {})]
    (is (= 45678 (:listen-port execution)))
    (is (= "0.0.0.0:8080" (:listen-address execution)))
    (is (= "10.0.2.2" (:backend-host execution)))
    (is (= ["ssh" "guest" "'/home/mt/firewall' '0.0.0.0:8080' '10.0.2.2:12345'"]
           ((:wrap-argv execution) ["0.0.0.0:8080" "10.0.2.2:12345"])))))
