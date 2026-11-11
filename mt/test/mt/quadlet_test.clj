(ns mt.quadlet-test
  (:require [clojure.test :refer [deftest is]] [clojure.string :as str]
            [mt.firewall :as firewall] [mt.quadlet :as quadlet]))

(def config {:memory_bytes 268435456 :pids_limit 64 :container_user "65532:65532"})
(def image (str "sha256:" (apply str (repeat 64 "a"))))

(deftest quadlet-restricts-lifecycle-and-escapes-exec
  (let [template (slurp "../infra/quadlet/firewall.container.in")
        text (quadlet/render template "mt-fw-test" image ["allow:GET:/%25$foo" "argument\"quote"] config)]
    (doseq [setting ["Network=host" "Pull=never" "Restart=no" "ReadOnly=true" "DropCapability=all"
                     "NoNewPrivileges=true" "--memory=268435456" "--pids-limit=64"]]
      (is (str/includes? text setting)))
    (is (not (str/includes? text "[Install]")))
    (is (str/includes? text "%%25$$foo"))
    (is (str/includes? text "argument\\\"quote"))
    (is (thrown? clojure.lang.ExceptionInfo
                 (quadlet/render template "mt-fw-test" image ["argument\nExec=bad"] config)))))

(deftest runtime-inspection-must-match-real-limits-and-image
  (let [inspect {:Image image :Config {:User "65532:65532"}
                 :HostConfig {:NetworkMode "host" :Memory 268435456 :PidsLimit 64
                              :ReadonlyRootfs true :Privileged false :SecurityOpt ["no-new-privileges"]}}]
    (is (nil? (quadlet/verify-inspect! inspect image config)))
    (doseq [bad [(assoc inspect :Image (str "sha256:" (apply str (repeat 64 "b"))))
                 (assoc-in inspect [:HostConfig :Memory] 0)
                 (assoc-in inspect [:HostConfig :PidsLimit] 0)
                 (assoc-in inspect [:HostConfig :SecurityOpt] [])
                 (assoc-in inspect [:HostConfig :NetworkMode] "bridge")
                 (assoc-in inspect [:HostConfig :Privileged] true)]]
      (is (thrown? clojure.lang.ExceptionInfo (quadlet/verify-inspect! bad image config))))))

(deftest explicit-container-opt-in
  (is (true? (:podman? (firewall/options ["--with-podman" "--ssh-host" "dut"]))))
  (is (false? (:podman? (firewall/options ["--ssh-host" "dut"]))))
  (is (thrown? clojure.lang.ExceptionInfo
               (firewall/options ["--with-podman" "--with-podman" "--ssh-host" "dut"]))))
