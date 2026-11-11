(ns mt.firewall
  (:require [babashka.fs :as fs]
            [bootstrap :as bootstrap]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [mt.core :as core])
  (:import [java.time Instant] [java.util UUID]))

(defn options [args]
  (when (> (count (filter #{"--with-podman"} args)) 1)
    (throw (ex-info "--with-podman may appear only once" {})))
  (assoc (core/options (remove #{"--with-podman"} args)) :podman? (boolean (some #{"--with-podman"} args))))

(defn check! [args]
  (let [{:keys [host root podman?]} (options args)
        repo core/repo
        id (str "http-" (.toEpochMilli (Instant/now)) "-" (UUID/randomUUID))
        output (fs/path repo "experiments/local" id)
        work (str root "/runs/" id)
        source-files ["implementations/firewall/src/main.rs" "implementations/firewall/src/http.rs"
                      "implementations/firewall/Cargo.toml" "implementations/firewall/dependencies.lock.json"
                      "experiments/scenarios/firewall-correctness.edn" "infra/feasibility/toolchains.lock.json"
                      "infra/feasibility/bootstrap.clj" "mt/src/mt/core.clj" "mt/src/mt/firewall.clj"
                      "mt/src/mt/http_check.clj" "mt/src/mt/tcp_check.clj" "mt/src/mt/quadlet.clj" "mt/bb.edn"
                      "infra/containers/firewall/Containerfile" "infra/containers/podman.lock.json"
                      "infra/quadlet/firewall.container.in" "infra/ansible/playbooks/prepare-podman.yml"]
        base-scenario (edn/read-string (slurp (str (fs/path repo "experiments/scenarios/firewall-correctness.edn"))))
        scenario (cond-> base-scenario
                   podman? (assoc :variants [:native :wasm :podman] :order [:native :wasm :podman]
                                  :container (json/parse-string (slurp (str (fs/path repo "infra/containers/podman.lock.json"))) true)))
        dependency (json/parse-string (slurp (str (fs/path repo "implementations/firewall/dependencies.lock.json"))) true)
        cache (fs/path repo ".cache/http")
        ssh ["ssh" "-o" "BatchMode=yes" "-o" "ConnectTimeout=10" host]]
    (when-not (and (= (:order base-scenario) [:native :wasm]) (= (:variants base-scenario) [:native :wasm])
                   (= (:kind scenario) :functional-correctness) (= (:concurrency scenario) :sequential)
                   (= (:max-connections scenario) 3) (= (:repetitions scenario) 1)
                   (every? pos-int? (map scenario [:io-timeout-ms :accept-timeout-ms :max-head-bytes
                                                   :max-headers :max-body-bytes :process-timeout-seconds])))
      (throw (ex-info "Invalid firewall correctness scenario" {})))
    (fs/create-dirs output)
    (doseq [[name data] [["scenario.json" scenario]
                        ["provenance.json" {:run_id id :started_at (str (Instant/now))
                                            :git_commit (core/command! ["git" "-C" (str repo) "rev-parse" "HEAD"])
                                            :git_dirty (not (str/blank? (core/command! ["git" "-C" (str repo) "status" "--porcelain"])))
                                            :controller_babashka (core/command! ["bb" "--version"])
                                            :source_sha256 (into {} (map (fn [p] [p (core/sha256 (fs/path repo p))]) source-files))}]]]
      (spit (str (fs/path output name)) (str (json/generate-string data {:pretty true}) "\n")))
    (doseq [p source-files]
      (let [destination (fs/path output "source" p)]
        (fs/create-dirs (fs/parent destination)) (fs/copy (fs/path repo p) destination)))
    (println "Artifacts:" (str output))
    (try
      (fs/create-dirs cache)
      (let [archive (bootstrap/archive! cache (assoc dependency :name "httparse"))
            extracted (fs/create-temp-dir {:dir cache :prefix "source-"})]
        (core/command! ["tar" "-xf" (str archive) "-C" (str extracted)])
        (fs/create-dirs (fs/path output "dependencies"))
        (fs/copy archive (fs/path output "dependencies" (fs/file-name archive)))
        (core/command! (conj ssh (str "mkdir -p " root "/runs && mkdir " work " && mkdir " work "/mt " work "/deployment")))
        (core/command! ["scp" "-q" "-r" "-o" "BatchMode=yes"
                        (str (fs/path output "source/implementations/firewall")) (str host ":" work "/firewall")])
        (core/command! ["scp" "-q" "-r" "-o" "BatchMode=yes"
                        (str (fs/path extracted "httparse-1.10.1")) (str host ":" work "/httparse")])
        (core/command! ["scp" "-q" "-o" "BatchMode=yes"
                        (str (fs/path repo "mt/src/mt/http_check.clj")) (str (fs/path repo "mt/src/mt/tcp_check.clj"))
                        (str (fs/path repo "mt/src/mt/quadlet.clj"))
                        (str host ":" work "/mt/")])
        (core/command! ["scp" "-q" "-o" "BatchMode=yes"
                        (str (fs/path repo "infra/containers/firewall/Containerfile"))
                        (str (fs/path repo "infra/quadlet/firewall.container.in"))
                        (str host ":" work "/deployment/")])
        (core/command! ["scp" "-q" "-o" "BatchMode=yes"
                        (str (fs/path repo "infra/feasibility/toolchains.lock.json"))
                        (str (fs/path output "scenario.json")) (str (fs/path output "provenance.json"))
                        (str host ":" work "/")])
        (core/command! (conj ssh (str root "/tools/bin/bb --classpath " work " -m mt.http-check --work " work " --tools " root "/tools"))
                       {:out :inherit :err :inherit}))
      (catch Exception e
        (spit (str (fs/path output "controller-error.edn"))
              (pr-str {:time (str (Instant/now)) :message (.getMessage e) :exit (:exit (ex-data e))}))
        (throw e))
      (finally
        (core/command! ["scp" "-q" "-r" "-o" "BatchMode=yes" (str host ":" work "/results") (str output)])))
    (println "Firewall correctness checks passed; these are not benchmark measurements.")))
