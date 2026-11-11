(ns mt.core
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str])
  (:import [java.security MessageDigest]
           [java.time Instant]
           [java.util UUID]))

(def repo (fs/parent (fs/parent (fs/parent (fs/parent *file*)))))

(defn command! [args & [options]]
  (let [result @(process/process args (merge {:out :string :err :string} options))]
    (when-not (zero? (:exit result))
      (throw (ex-info (str "Command failed: " (first args)) result)))
    ;; Inherited output is represented by a stream, not a captured string.
    (if (string? (:out result)) (str/trim (:out result)) "")))

(defn sha256 [path]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (fs/read-all-bytes path))]
    (apply str (map #(format "%02x" (bit-and 0xff %)) digest))))

(defn options [args]
  (when (or (odd? (count args))
            (not-every? #{"--ssh-host" "--remote-root"} (take-nth 2 args)))
    (throw (ex-info "Expected --ssh-host ALIAS and optional --remote-root /tmp/mt-tcp-feasibility..." {})))
  (let [opts (apply hash-map args)
        host (get opts "--ssh-host")
        root (get opts "--remote-root" "/tmp/mt-tcp-feasibility")]
    (when-not (and host (re-matches #"[A-Za-z0-9][A-Za-z0-9_.-]*" host))
      (throw (ex-info "Use an existing OpenSSH alias without shell characters" {})))
    (when-not (and (re-matches #"/tmp/mt-tcp-feasibility[A-Za-z0-9_./-]*" root)
                   (not (some #{".."} (str/split root #"/"))))
      (throw (ex-info "Remote root must be a safe path under /tmp/mt-tcp-feasibility" {})))
    {:host host :root root}))

(defn feasibility! [args]
  (let [{:keys [host root]} (options args)
        id (str "tcp-" (.toEpochMilli (Instant/now)) "-" (UUID/randomUUID))
        output (fs/path repo "experiments/local" id)
        work (str root "/runs/" id)
        source-files ["implementations/feasibility/tcp-forwarder/src/main.rs"
                      "mt/src/mt/tcp_check.clj"
                      "implementations/feasibility/tcp-forwarder/Cargo.toml"
                      "experiments/scenarios/tcp-feasibility.edn"
                      "infra/feasibility/toolchains.lock.json"
                      "infra/feasibility/bootstrap.clj"
                      "infra/ansible/playbooks/prepare-tcp-feasibility.yml"
                      "mt/src/mt/core.clj" "mt/bb.edn"]
        scenario (edn/read-string (slurp (str (fs/path repo "experiments/scenarios/tcp-feasibility.edn"))))
        provenance {:run_id id :started_at (str (Instant/now))
                    :git_commit (command! ["git" "-C" (str repo) "rev-parse" "HEAD"])
                    :git_dirty (not (str/blank? (command! ["git" "-C" (str repo) "status" "--porcelain"])))
                    :controller_babashka (command! ["bb" "--version"])
                    :source_sha256 (into {} (map (fn [p] [p (sha256 (fs/path repo p))]) source-files))}
        ssh ["ssh" "-o" "BatchMode=yes" "-o" "ConnectTimeout=10" host]]
    (when-not (and (= (:order scenario) [:native :wasm])
                   (= (:variants scenario) [:native :wasm])
                   (= (:repetitions scenario) 1)
                   (= (:kind scenario) :functional-feasibility)
                   (every? pos-int? (map scenario [:io-timeout-ms :accept-timeout-ms :max-bytes :process-timeout-seconds])))
      (throw (ex-info "Invalid TCP feasibility scenario" {})))
    (fs/create-dirs output)
    (doseq [[name data] [["scenario.json" scenario] ["provenance.json" provenance]]]
      (spit (str (fs/path output name)) (str (json/generate-string data {:pretty true}) "\n")))
    (spit (str (fs/path output "source-hashes.edn")) (pr-str (:source_sha256 provenance)))
    (doseq [p source-files]
      (let [target (fs/path output "source" p)]
        (fs/create-dirs (fs/parent target))
        (fs/copy (fs/path repo p) target)))
    (println "Artifacts:" (str output))
    (try
      (command! (conj ssh (str "mkdir -p " root "/runs && mkdir " work)))
      (command! ["scp" "-q" "-o" "BatchMode=yes"
                 (str (fs/path repo "implementations/feasibility/tcp-forwarder/src/main.rs"))
                 (str (fs/path repo "infra/feasibility/toolchains.lock.json"))
                 (str (fs/path output "scenario.json")) (str (fs/path output "provenance.json"))
                 (str host ":" work "/")])
      (command! ["scp" "-q" "-o" "BatchMode=yes"
                 (str (fs/path repo "mt/src/mt/tcp_check.clj")) (str host ":" work "/check.clj")])
      (command! (conj ssh (str root "/tools/bin/bb " work "/check.clj --work " work " --tools " root "/tools"))
                {:out :inherit :err :inherit})
      (catch Exception e
        (spit (str (fs/path output "controller-error.edn"))
              (pr-str {:time (str (Instant/now)) :message (.getMessage e)
                       :exit (:exit (ex-data e))}))
        (throw e))
      (finally
        ;; Collect partial evidence even when compilation or a case fails. Never reuse an ID.
        (command! ["scp" "-q" "-r" "-o" "BatchMode=yes" (str host ":" work "/results") (str output)])))
    (println "TCP feasibility checks passed; these are not benchmark measurements.")))

(def usage
  (str "Usage: mt COMMAND [OPTIONS]\n\n"
       "Commands:\n"
       "  feasibility tcp   Check shared native/WASIp2 TCP forwarding\n"
       "  firewall check    Check the shared HTTP firewall\n\n"
       "Options:\n"
       "  --ssh-host ALIAS   Existing OpenSSH alias for the DUT\n"
       "  --remote-root DIR  Prepared DUT directory (default: /tmp/mt-tcp-feasibility)\n"
       "  --with-podman      Include Podman/Quadlet in firewall check\n"
       "  -h, --help         Show this help\n"))

(defn -main [& args]
  (cond
    (or (empty? args) (= args ["--help"]) (= args ["-h"]) (= args ["help"])) (println usage)
    (= (take 2 args) ["feasibility" "tcp"]) (feasibility! (drop 2 args))
    (= (take 2 args) ["firewall" "check"]) ((requiring-resolve 'mt.firewall/check!) (drop 2 args))
    :else (throw (ex-info usage {}))))
