(ns mt.vm
  "Run-owned transient KVM guest. Functional controls, not benchmark allocations."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]
            [mt.tcp-check :as tcp]))

(def virsh ["virsh" "-c" "qemu:///session"])

(defn shell-word [s]
  (str "'" (str/replace (str s) "'" "'\"'\"'") "'"))

(defn free-port []
  (with-open [socket (tcp/listener)] (.getLocalPort socket)))

(defn domain-xml [{:keys [name disk seed nvram serial ssh-port http-port lock]}]
  ;; Paths and names come exclusively from validated mt paths and UUID run IDs.
  (str "<domain type='kvm' xmlns:qemu='http://libvirt.org/schemas/domain/qemu/1.0'>"
       "<name>" name "</name><memory unit='MiB'>" (:memory_mib lock) "</memory>"
       "<vcpu>" (:vcpus lock) "</vcpu><cpu mode='host-passthrough'/>"
       "<os><type arch='x86_64' machine='" (:machine lock) "'>hvm</type>"
       "<loader readonly='yes' type='pflash'>/usr/share/edk2/ovmf/OVMF_CODE.fd</loader>"
       "<nvram template='/usr/share/edk2/ovmf/OVMF_VARS.fd'>" nvram "</nvram>"
       "<boot dev='hd'/></os><features><acpi/><apic/></features>"
       "<on_poweroff>destroy</on_poweroff><on_reboot>destroy</on_reboot><on_crash>destroy</on_crash>"
       "<devices><emulator>/usr/libexec/qemu-kvm</emulator>"
       "<disk type='file' device='disk'><driver name='qemu' type='qcow2'/>"
       "<source file='" disk "'/><target dev='vda' bus='virtio'/></disk>"
       "<disk type='file' device='cdrom'><driver name='qemu' type='raw'/>"
       "<source file='" seed "'/><target dev='sda' bus='sata'/><readonly/></disk>"
       "<serial type='file'><source path='" serial "'/><target port='0'/></serial>"
       "<memballoon model='none'/></devices><qemu:commandline>"
       "<qemu:arg value='-netdev'/><qemu:arg value='user,id=mt-net,"
       "hostfwd=tcp:127.0.0.1:" ssh-port "-:22,hostfwd=tcp:127.0.0.1:" http-port "-:" (:guest_firewall_port lock) "'/>"
       "<qemu:arg value='-device'/><qemu:arg value='virtio-net-pci,netdev=mt-net,bus=pcie.0,addr=0x10'/></qemu:commandline></domain>"))

(defn ssh-command [guest command]
  (into (:ssh guest) [(str/join " " (map shell-word command))]))

(defn cleanup! [guest]
  (let [output (:output guest) name (:name guest)
        active (tcp/capture! (into virsh ["list" "--name"]) (fs/path output "cleanup-before.txt"))]
    (when (some #{name} (str/split-lines active))
      (tcp/capture! (into virsh ["destroy" name]) (fs/path output "destroy.txt")))
    (let [remaining (tcp/capture! (into virsh ["list" "--all" "--name"]) (fs/path output "cleanup-after.txt"))]
      (tcp/check! (not (some #{name} (str/split-lines remaining))) "Owned VM remains after cleanup"))
    ;; Seed contains ephemeral SSH private keys. Never collect it as a raw result.
    (when (fs/exists? (:secrets guest)) (fs/delete-tree (:secrets guest)))
    (tcp/write-json! (fs/path output "cleanup.json") {:status "passed" :domain name :time (tcp/utc)})
    (when-let [hook (:shutdown-hook guest)]
      (try (.removeShutdownHook (Runtime/getRuntime) hook) (catch IllegalStateException _ nil)))))

(defn prepare! [work output tools binary lock]
  (let [dir (fs/path output "vm-environment") _ (fs/create-dir dir)
        secrets (fs/path work "vm-secrets") _ (fs/create-dir secrets {:posix-file-permissions "rwx------"})
        name (str "mt-" (fs/file-name work))
        ssh-port (free-port) http-port (free-port)
        key (str (fs/path secrets "client")) host-key (str (fs/path secrets "host"))
        known (str (fs/path secrets "known_hosts"))
        context {:name name :output dir :secrets secrets :http-port http-port :lock lock
               :ssh ["ssh" "-T" "-o" "BatchMode=yes" "-o" "IdentitiesOnly=yes"
                     "-o" "StrictHostKeyChecking=yes" "-o" (str "UserKnownHostsFile=" known)
                     "-o" "ConnectTimeout=2" "-o" "ConnectionAttempts=1"
                     "-i" key "-p" (str ssh-port) "mt@127.0.0.1"]}
        hook (Thread. #(try (cleanup! context) (catch Exception e (binding [*out* *err*] (println (tcp/trace e))))))
        guest (assoc context :shutdown-hook hook)]
    (.addShutdownHook (Runtime/getRuntime) hook)
    (try
      (doseq [package (:packages lock)]
        (tcp/check! (= package (tcp/capture! ["rpm" "-q" package] (fs/path dir (str package ".txt")))) "VM package pin mismatch"))
      (tcp/capture! (into virsh ["version"]) (fs/path dir "libvirt-version.txt"))
      (tcp/capture! (into virsh ["domcapabilities" "--virttype" "kvm" "--arch" "x86_64" "--machine" (:machine lock)])
                    (fs/path dir "capabilities.xml"))
      (let [cache (fs/path tools "vm-cache") _ (fs/create-dirs cache)
            image (fs/path cache (str (:image_sha256 lock) ".qcow2"))]
        (when-not (fs/exists? image)
          (let [partial (fs/path secrets "download.qcow2")
                child (process/process ["curl" "--fail" "--location" "--max-time" "600"
                                        "--output" (str partial) (:image_url lock)]
                                       {:out (fs/file dir "download.stdout") :err (fs/file dir "download.stderr")
                                        :shutdown process/destroy-tree})]
            (tcp/check! (zero? (:exit (tcp/wait-process! child 610000))) "Guest image download failed")
            (tcp/check! (= (:image_sha256 lock) (tcp/sha256 partial)) "Downloaded image checksum mismatch")
            (fs/move partial image)))
        (tcp/check! (= (:image_sha256 lock) (tcp/sha256 image)) "Cached image checksum mismatch")
        (doseq [path [key host-key]]
          (tcp/capture! ["ssh-keygen" "-q" "-t" "ed25519" "-N" "" "-f" path]
                        (fs/path dir (str (fs/file-name path) "-keygen.txt"))))
        (spit known (str "[127.0.0.1]:" ssh-port " " (str/trim (slurp (str host-key ".pub"))) "\n"))
        (spit (str (fs/path secrets "meta-data")) (str "instance-id: " name "\nlocal-hostname: mt-guest\n"))
        (spit (str (fs/path secrets "user-data"))
              (str "#cloud-config\nusers:\n  - name: mt\n    shell: /bin/bash\n    lock_passwd: true\n    ssh_authorized_keys:\n      - "
                   (str/trim (slurp (str key ".pub")))
                   "\nssh_pwauth: false\ndisable_root: true\nssh_keys:\n  ed25519_private: |\n"
                   (str/join "\n" (map #(str "    " %) (str/split-lines (slurp host-key))))
                   "\n  ed25519_public: " (str/trim (slurp (str host-key ".pub")))
                   "\nruncmd:\n  - [sh, -c, 'systemctl stop firewalld.service || true']\n"))
        (let [seed (fs/path secrets "seed.iso") disk (fs/path dir "overlay.qcow2")
              xml (fs/path dir "domain.xml")]
          (tcp/capture! ["xorriso" "-as" "mkisofs" "-quiet" "-output" (str seed) "-volid" "cidata" "-joliet" "-rock"
                         (str (fs/path secrets "user-data")) (str (fs/path secrets "meta-data"))]
                        (fs/path dir "seed-build.txt"))
          (tcp/capture! ["qemu-img" "create" "-f" "qcow2" "-F" "qcow2" "-b" (str image) (str disk)]
                        (fs/path dir "overlay-build.txt"))
          (spit (str xml) (domain-xml {:name name :disk disk :seed seed :nvram (fs/path dir "nvram.fd")
                                     :serial (fs/path dir "serial.log") :ssh-port ssh-port :http-port http-port :lock lock}))
          (tcp/capture! (into virsh ["create" (str xml)]) (fs/path dir "create.txt"))
          (tcp/capture! (into virsh ["dumpxml" name]) (fs/path dir "live-domain.xml"))
          (tcp/capture! (into virsh ["qemu-monitor-command" name "{\"execute\":\"query-kvm\"}"])
                        (fs/path dir "kvm.json"))
          (tcp/check! (true? (get-in (tcp/read-json (fs/path dir "kvm.json")) [:return :enabled])) "KVM is not enabled")
          (let [deadline (+ (System/currentTimeMillis) (* 1000 (:boot_timeout_seconds lock)))]
            (loop []
              (let [probe @(process/process (ssh-command guest ["true"]) {:out :string :err :string})]
                (when-not (zero? (:exit probe))
                  (tcp/check! (< (System/currentTimeMillis) deadline) (str "Guest SSH readiness timed out: " (:err probe)))
                  (Thread/sleep 500) (recur)))))
          (tcp/capture! (ssh-command guest ["uname" "-a"]) (fs/path dir "guest-kernel.txt"))
          (tcp/capture! (ssh-command guest ["cat" "/etc/os-release"]) (fs/path dir "guest-os.txt"))
          (tcp/capture! (ssh-command guest ["cat" "/proc/cpuinfo"]) (fs/path dir "guest-cpu.txt"))
          (tcp/capture! (ssh-command guest ["ip" "-details" "address" "show"]) (fs/path dir "guest-network.txt"))
          (tcp/capture! (ssh-command guest ["sh" "-c" "for device in /sys/class/net/*/device/driver; do readlink -f \"$device\"; done"])
                        (fs/path dir "guest-network-driver.txt"))
          (let [copy ["scp" "-q" "-o" "BatchMode=yes" "-o" "IdentitiesOnly=yes"
                      "-o" "StrictHostKeyChecking=yes" "-o" (str "UserKnownHostsFile=" known)
                      "-i" key "-P" (str ssh-port) (str binary) "mt@127.0.0.1:firewall"]]
            (tcp/capture! copy (fs/path dir "copy-binary.txt")))
          (tcp/check! (= (tcp/sha256 binary)
                         (first (str/split (tcp/capture! (ssh-command guest ["sha256sum" "/home/mt/firewall"])
                                                        (fs/path dir "guest-binary-sha256.txt")) #"\s+"))) "Guest binary differs")
          (tcp/write-json! (fs/path dir "environment.json") {:domain name :lock lock :binary_sha256 (tcp/sha256 binary)
                                                             :host_ssh_port ssh-port :host_http_port http-port :kvm true})
          guest))
      (catch Exception e
        (try (cleanup! guest) (catch Exception cleanup
                               (tcp/write-json! (fs/path dir "cleanup-error.json") {:error (tcp/trace cleanup)})))
        (throw e)))))

(defn recovery-check! [guest]
  (let [child (process/process (ssh-command guest ["sleep" "60"])
                               {:out (fs/file (:output guest) "recovery.stdout")
                                :err (fs/file (:output guest) "recovery.stderr")
                                :shutdown process/destroy-tree})]
    (try
      (tcp/capture! (ssh-command guest ["true"]) (fs/path (:output guest) "recovery-probe.txt"))
      (tcp/check! (.isAlive (:proc child)) "Recovery supervisor exited early")
      (process/destroy-tree child)
      (tcp/wait-process! child 3000)
      (tcp/capture! (into virsh ["domstate" (:name guest)]) (fs/path (:output guest) "recovery-domain-state.txt"))
      (cleanup! guest)
      (tcp/write-json! (fs/path (:output guest) "recovery.json")
                       {:status "passed" :ssh_supervisor_terminated true :owned_domain_removed true})
      (finally (when (.isAlive (:proc child)) (process/destroy-tree child))))))

(defn command-factory [guest]
  (fn [_ _ _]
    {:argv (:ssh guest)
     ;; All app arguments are generated numeric socket addresses, limits, and fixed policy.
     ;; One shell command is built after run-case! has resolved the ephemeral backend port.
     :wrap-argv (fn [args] (ssh-command guest (into ["/home/mt/firewall"] args)))
     ;; SLIRP accepts the host socket before a guest process is listening. Wait
     ;; for the guest listener without consuming a VNF connection-budget slot.
     ;; The normal allowed/denied application probes still follow this check.
     :await-start (fn [proc deadline]
                    (loop []
                      (tcp/check! (.isAlive (:proc proc)) "Guest VNF exited before listening")
                      (tcp/check! (< (System/nanoTime) deadline) "Guest listener readiness timed out")
                      (let [probe @(process/process (ssh-command guest ["ss" "-ltnH" "sport = :8080"])
                                                     {:out :string :err :string})]
                        (tcp/check! (zero? (:exit probe)) "Guest socket inspection failed")
                        (when (str/blank? (:out probe)) (Thread/sleep 10) (recur)))))
     :listen-port (:http-port guest)
     :listen-address (str "0.0.0.0:" (get-in guest [:lock :guest_firewall_port]))
     :backend-host (get-in guest [:lock :guest_backend_host])}))
