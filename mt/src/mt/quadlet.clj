(ns mt.quadlet
  "Rootless Quadlet lifecycle for the correctness suite, not a benchmark adapter."
  (:require [babashka.fs :as fs] [babashka.process :as process]
            [cheshire.core :as json] [clojure.string :as str] [mt.tcp-check :as tcp]))

(defn execute [argv]
  (let [proc (process/process argv {:out :string :err :string :shutdown process/destroy-tree})]
    (tcp/wait-process! proc 30000)))

(defn checked [argv]
  (let [result (execute argv)]
    (tcp/check! (zero? (:exit result)) (str "Command failed: " (str/join " " argv) "\n" (:err result)))
    (str/trim (:out result))))

(defn record! [path argv]
  (let [result (execute argv)]
    (tcp/write-json! path (assoc (select-keys result [:exit :out :err]) :command argv :timestamp (tcp/utc)))
    result))

(defn properties [text]
  (into {} (keep (fn [line] (when (str/includes? line "=") (str/split line #"=" 2))) (str/split-lines text))))

(defn unit-name [work case-name]
  (tcp/check! (re-matches #"[A-Za-z0-9_-]+" case-name) "Unsafe case name")
  (let [id (str (fs/file-name work))]
    (tcp/check! (re-matches #"http-[0-9]+-[a-f0-9-]+" id) "Unsafe run ID")
    (str "mt-fw-" id "-" (str/replace case-name "_" "-"))))

(defn systemd-word [argument]
  (tcp/check! (not (re-find #"[\x00-\x1f\x7f]" argument)) "Control character in Quadlet argument")
  (str "\"" (-> argument (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")
                 (str/replace "%" "%%") (str/replace "$" "$$")) "\""))

(defn render [template name image argv config]
  (tcp/check! (re-matches #"sha256:[a-f0-9]{64}" image) "Expected content-addressed image ID")
  (tcp/check! (re-matches #"mt-fw-[A-Za-z0-9-]+" name) "Unsafe Quadlet name")
  (let [result (reduce-kv (fn [text key value] (str/replace text (str "{{" (clojure.core/name key) "}}") value))
                          template {:name name :image image :exec (str/join " " (map systemd-word argv))
                                    :memory (str (:memory_bytes config)) :pids (str (:pids_limit config))})]
    (tcp/check! (not (str/includes? result "{{")) "Unresolved Quadlet template variable") result))

(defn preflight! [output config]
  (let [version (tcp/capture! ["podman" "--version"] (fs/path output "podman-version.txt"))
        info (json/parse-string (tcp/capture! ["podman" "info" "--format" "json"] (fs/path output "podman-info.json")) true)
        package (tcp/capture! ["rpm" "-q" "podman"] (fs/path output "podman-package.txt"))
        runtime-dir (System/getenv "XDG_RUNTIME_DIR")]
    (tcp/check! (= (str "podman version " (:version config)) version) "Installed Podman differs from lock")
    (tcp/check! (= (:package config) package) "Installed Podman RPM differs from lock")
    (tcp/check! (true? (get-in info [:host :security :rootless])) "Podman must run rootless")
    (tcp/check! (= "v2" (get-in info [:host :cgroupVersion])) "Quadlet requires cgroup v2")
    (tcp/check! (and runtime-dir (fs/directory? runtime-dir)) "An active user runtime directory is required")
    (tcp/check! (= "running" (checked ["systemctl" "--user" "is-system-running"])) "User-systemd manager is not running")
    (tcp/capture! ["rpm" "-q" "podman" "conmon" "crun" "containers-common" "netavark" "aardvark-dns" "passt"]
                  (fs/path output "container-packages.txt"))
    {:version version :package package :rootless true :network "host"
     :oci_runtime (get-in info [:host :ociRuntime]) :conmon (get-in info [:host :conmon])
     :cgroup_manager (get-in info [:host :cgroupManager]) :cgroup_version (get-in info [:host :cgroupVersion])
     :runtime_dir runtime-dir :configuration config}))

(defn prepare-image! [work output binary config]
  (let [context (fs/path output "podman-image")
        tag (str "localhost/mt-firewall:" (fs/file-name work))
        iid (fs/path output "podman-image.id")]
    (fs/create-dir context)
    (fs/copy binary (fs/path context "firewall"))
    (fs/copy (fs/path work "deployment/Containerfile") (fs/path context "Containerfile"))
    (tcp/capture! ["podman" "build" "--network=none" "--pull=never" "--timestamp=0" "--format=oci"
                   "--iidfile" (str iid) "--tag" tag "--file" (str (fs/path context "Containerfile")) (str context)]
                  (fs/path output "podman-build.txt"))
    (let [image (str/trim (slurp (str iid)))
          inspect (first (json/parse-string (tcp/capture! ["podman" "image" "inspect" image]
                                                        (fs/path output "podman-image-inspect.json")) true))
          check-name (str "mt-image-check-" (fs/file-name work))
          extracted (fs/path output "image-firewall")]
      (tcp/check! (re-matches #"sha256:[a-f0-9]{64}" image) "Invalid built image ID")
      (try
        (checked ["podman" "create" "--pull=never" "--network=host" "--name" check-name image])
        (checked ["podman" "cp" (str check-name ":/firewall") (str extracted)])
        (tcp/check! (= (tcp/sha256 binary) (tcp/sha256 extracted)) "Image changed the native executable")
        (finally (checked ["podman" "rm" "--force" "--ignore" check-name])))
      (tcp/capture! ["podman" "save" "--format=oci-archive" "--output" (str (fs/path output "firewall-image.oci.tar")) image]
                    (fs/path output "podman-save.txt"))
      {:image_id image :manifest_digest (:Digest inspect) :tag tag
       :binary_sha256 (tcp/sha256 extracted) :oci_archive_sha256 (tcp/sha256 (fs/path output "firewall-image.oci.tar"))
       :user (get-in inspect [:Config :User]) :config config})))

(defn cleanup! [context prefix]
  (let [{:keys [name unit_path output]} context
        unit (str name ".service")
        stop (record! (fs/path output (str prefix "-stop.json")) ["systemctl" "--user" "stop" unit])]
    (tcp/check! (or (zero? (:exit stop)) (re-find #"not (loaded|found)" (:err stop))) "Unable to stop owned Quadlet unit")
    (let [removed (record! (fs/path output (str prefix "-remove.json")) ["podman" "rm" "--force" "--ignore" name])]
      (tcp/check! (zero? (:exit removed)) "Unable to remove owned container"))
    (when (= prefix "recovery")
      (record! (fs/path output "recovery-journal.json") ["journalctl" "--user" "--unit" unit "--no-pager" "--output=short-iso"]))
    (let [reset (record! (fs/path output (str prefix "-reset-failed.json")) ["systemctl" "--user" "reset-failed" unit])]
      (tcp/check! (or (zero? (:exit reset)) (re-find #"not (loaded|found)" (:err reset)))
                  "Unable to clear owned unit's failed state"))
    (fs/delete-if-exists unit_path)
    (let [reloaded (record! (fs/path output (str prefix "-reload.json")) ["systemctl" "--user" "daemon-reload"])]
      (tcp/check! (zero? (:exit reloaded)) "Unable to unload owned Quadlet definition"))
    (let [state (execute ["systemctl" "--user" "is-active" unit])
          exists (execute ["podman" "container" "exists" name])]
      (tcp/check! (#{"inactive" "unknown"} (str/trim (:out state))) "Owned service remains active or failed after cleanup")
      (tcp/check! (= 1 (:exit exists)) "Owned container remains after cleanup")
      (tcp/write-json! (fs/path output (str prefix "-verified.json")) {:unit_inactive true :container_absent true :definition_absent (not (fs/exists? unit_path))}))))

(defn verify-inspect! [inspect image config]
  (tcp/check! (= (str/replace-first image "sha256:" "") (str/replace-first (:Image inspect) "sha256:" ""))
              "Container uses another image")
  (tcp/check! (= "host" (get-in inspect [:HostConfig :NetworkMode])) "Unexpected container network mode")
  (tcp/check! (= (:container_user config) (get-in inspect [:Config :User])) "Unexpected container UID/GID")
  (tcp/check! (= (:memory_bytes config) (get-in inspect [:HostConfig :Memory])) "Container memory guard not applied")
  (tcp/check! (= (:pids_limit config) (get-in inspect [:HostConfig :PidsLimit])) "Container PID guard not applied")
  (tcp/check! (true? (get-in inspect [:HostConfig :ReadonlyRootfs])) "Container root filesystem is writable")
  (tcp/check! (false? (get-in inspect [:HostConfig :Privileged])) "Container is privileged or inspection is incomplete")
  (tcp/check! (some #{"no-new-privileges"} (get-in inspect [:HostConfig :SecurityOpt])) "No-new-privileges is not enabled"))

(defn service! [context argv]
  (let [{:keys [name unit_path output image config template]} context
        unit (str name ".service")
        snapshots (atom [])
        ready-file (fs/path output "lifecycle-ready")
        started? (atom false)]
    (try
      (let [definition (render template name image argv config)]
        (fs/create-dirs (fs/parent unit_path))
        (fs/create-file unit_path) ; refuse to replace another run's definition
        (spit unit_path definition)
        (spit (str (fs/path output "firewall.container")) definition))
      (tcp/check! (zero? (:exit (record! (fs/path output "generation.json") ["systemctl" "--user" "daemon-reload"]))) "Quadlet generation failed")
      (tcp/capture! ["systemctl" "--user" "cat" unit] (fs/path output "generated.service"))
      (tcp/check! (zero? (:exit (record! (fs/path output "start.json") ["systemctl" "--user" "start" unit]))) "Quadlet start failed")
      (reset! started? true)
      (let [inspect (first (json/parse-string (tcp/capture! ["podman" "inspect" name] (fs/path output "container-inspect.json")) true))]
        (verify-inspect! inspect image config)
        (spit (str ready-file) (tcp/utc)))
      (let [deadline (+ (System/nanoTime) 25000000000)]
        (loop []
          (tcp/check! (< (System/nanoTime) deadline) "Quadlet service did not finish")
          (let [state (properties (checked ["systemctl" "--user" "show" unit "-p" "ActiveState" "-p" "SubState"
                                            "-p" "Result" "-p" "ExecMainStatus" "-p" "ControlGroup"]))]
            (swap! snapshots conj (assoc state "timestamp" (tcp/utc)))
            (if (#{"inactive" "failed"} (get state "ActiveState"))
              (tcp/check! (and (= "success" (get state "Result")) (= "0" (get state "ExecMainStatus"))) "Quadlet service failed")
              (do (Thread/sleep 25) (recur))))))
      0
      (catch Exception e
        (tcp/write-json! (fs/path output "failure.json") {:error (tcp/trace e) :started @started?})
        1)
      (finally
        (tcp/write-json! (fs/path output "service-states.json") @snapshots)
        (record! (fs/path output "journal.json") ["journalctl" "--user" "--unit" unit "--no-pager" "--output=short-iso"])
        (cleanup! context "cleanup")))))

(defn command-factory [work tools image config]
  (fn [case output _scenario]
    (let [name (unit-name work (:id case))
          directory (fs/path output (str (:id case) "-quadlet"))
          _ (fs/create-dir directory)
          context {:name name :image (:image_id image) :config config :output (str directory)
                   :unit_path (str (fs/path (System/getenv "XDG_RUNTIME_DIR") "containers/systemd" (str name ".container")))
                   :template (slurp (str (fs/path work "deployment/firewall.container.in")))}
          context-file (fs/path directory "context.json")]
      (tcp/write-json! context-file context)
      {:argv [(str (fs/path tools "bin/bb")) "--classpath" (str work) "-m" "mt.quadlet" "--context" (str context-file) "--"]
       :await-start (fn [proc deadline]
                      (loop []
                        (when-not (fs/exists? (fs/path directory "lifecycle-ready"))
                          (tcp/check! (.isAlive (:proc proc)) "Quadlet lifecycle exited before inspection")
                          (tcp/check! (< (System/nanoTime) deadline) "Quadlet inspection deadline exceeded")
                          (Thread/sleep 10) (recur))))
       :cleanup #(cleanup! context "recovery")})))

(defn recovery-check! [work tools image output scenario]
  (let [directory (fs/path output "podman-recovery")
        _ (fs/create-dir directory)
        case {:id "supervisor_recovery"}
        execution ((command-factory work tools image (:container scenario)) case directory scenario)
        port (with-open [server (tcp/listener)] (.getLocalPort server))
        argv (into (:argv execution)
                   (concat [(str "127.0.0.1:" port) "127.0.0.1:9" (str (:io-timeout-ms scenario))
                            (str (:accept-timeout-ms scenario)) (str (:max-head-bytes scenario))
                            (str (:max-headers scenario)) (str (:max-body-bytes scenario)) "3"] (:policy scenario)))
        proc (process/process argv {:out (fs/file directory "wrapper.stdout") :err (fs/file directory "wrapper.stderr")
                                    :shutdown process/destroy-tree})
        result (atom {:status "failed" :started_at (tcp/utc)})]
    (try
      ((:await-start execution) proc (+ (System/nanoTime) 20000000000))
      (tcp/check! (= "active" (checked ["systemctl" "--user" "is-active" (str (unit-name work (:id case)) ".service")]))
                  "Recovery test requires an active service")
      (process/destroy-tree proc)
      (tcp/wait-process! proc 3000)
      (swap! result assoc :wrapper_terminated true)
      (catch Exception e (swap! result assoc :error (tcp/trace e)))
      (finally
        (when (.isAlive (:proc proc)) (.destroyForcibly (:proc proc)) (.waitFor (:proc proc)))
        (try
          ((:cleanup execution))
          (when (:wrapper_terminated @result)
            (let [manager (checked ["systemctl" "--user" "is-system-running"])]
              (tcp/check! (= "running" manager) "User manager was not restored after interruption")
              (swap! result assoc :status "passed" :owned_resources_removed true :user_manager_state manager)))
          (catch Exception e (swap! result assoc :status "failed" :cleanup_error (tcp/trace e))))
        (tcp/write-json! (fs/path directory "summary.json") (assoc @result :finished_at (tcp/utc)))))
    (tcp/check! (= "passed" (:status @result)) "Interrupted Quadlet recovery failed")
    @result))

(defn -main [& args]
  (tcp/check! (and (>= (count args) 4) (= "--context" (first args)) (= "--" (nth args 2))) "Expected --context FILE -- FIREWALL_ARGS")
  (System/exit (service! (tcp/read-json (second args)) (vec (drop 3 args)))))
