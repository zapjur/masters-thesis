;; Controller-side preparation. Requires Babashka, curl, tar, and OpenSSH.
(ns bootstrap
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.security MessageDigest]))

(defn sha256 [path]
  (with-open [in (clojure.java.io/input-stream (fs/file path))]
    (let [digest (MessageDigest/getInstance "SHA-256")
          buffer (byte-array 65536)]
      (loop []
        (let [n (.read in buffer)]
          (when (pos? n) (.update digest buffer 0 n) (recur))))
      (apply str (map #(format "%02x" (bit-and 255 %)) (.digest digest))))))

(defn command! [args]
  (let [result @(process/process args {:out :string :err :string})]
    (when-not (zero? (:exit result))
      (throw (ex-info (str "Preparation command failed: " (first args))
                      (select-keys result [:exit :out :err]))))
    (str/trim (:out result))))

(defn remote! [host command]
  (command! ["ssh" "-o" "BatchMode=yes" "-o" "ConnectTimeout=10" host command]))

(defn upload! [host source destination]
  (command! ["scp" "-q" "-r" "-o" "BatchMode=yes" (str source) (str host ":" destination)]))

(defn options [args]
  (when (or (odd? (count args))
            (not-every? #{"--ssh-host" "--root" "--lock" "--cache"} (take-nth 2 args)))
    (throw (ex-info "Expected --ssh-host ALIAS --root PATH --lock FILE --cache PATH" {})))
  (let [opts (apply hash-map args)
        host (get opts "--ssh-host")
        root (get opts "--root")]
    (when-not (and host (re-matches #"[A-Za-z0-9][A-Za-z0-9_.-]*" host)
                   root (re-matches #"/tmp/mt-tcp-feasibility[A-Za-z0-9_./-]*" root)
                   (not (some #{".."} (str/split root #"/"))))
      (throw (ex-info "Use an OpenSSH alias and a safe /tmp/mt-tcp-feasibility root" {})))
    (when-not (and (get opts "--lock") (get opts "--cache"))
      (throw (ex-info "--lock and --cache are required" {})))
    {:host host :root root :lock (get opts "--lock") :cache (get opts "--cache")}))

(defn archive! [cache artifact]
  (let [archive (fs/path cache (last (str/split (:url artifact) #"/")))]
    (when-not (fs/exists? archive)
      (let [partial (str archive ".partial")]
        (command! ["curl" "--fail" "--silent" "--show-error" "--location"
                   "--connect-timeout" "15" "--max-time" "300" (:url artifact) "-o" partial])
        (when-not (= (:sha256 artifact) (sha256 partial))
          (throw (ex-info "Downloaded archive checksum mismatch" {:artifact (:name artifact)})))
        (fs/move partial archive)))
    (when-not (= (:sha256 artifact) (sha256 archive))
      (throw (ex-info "Cached archive checksum mismatch" {:artifact (:name artifact)})))
    archive))

(defn prepare! [{:keys [host root lock cache]}]
  (let [lock-data (json/parse-string (slurp lock) true)
        cache (fs/absolutize cache)]
    (fs/create-dirs cache)
    (remote! host (str "umask 077\nmkdir -p " root "/tools/bin " root "/install"))
    (doseq [artifact (:artifacts lock-data)]
      (let [name (:name artifact)
            _ (when-not (re-matches #"[A-Za-z0-9_.-]+" name)
                (throw (ex-info "Invalid artifact name" {:name name})))
            _ (when-not (re-matches #"[a-f0-9]{64}" (:sha256 artifact))
                (throw (ex-info "Invalid artifact checksum" {:name name})))
            marker (str root "/tools/" name ".installed")
            installed (remote! host (str "if test -f " marker "; then cat " marker "; fi"))]
        (if (seq installed)
          (when-not (= installed (:sha256 artifact))
            (throw (ex-info "Tool version differs; use a new remote root" {:artifact name})))
          (let [archive (archive! cache artifact)
                extracted (fs/create-temp-dir {:dir cache :prefix (str name "-")})]
            ;; Archive integrity is checked before using the controller's tar.
            (command! ["tar" "-xf" (str archive) "-C" (str extracted)])
            (case name
              "babashka" (do (upload! host (fs/path extracted "bb") (str root "/tools/bin/bb"))
                             (remote! host (str "chmod 700 " root "/tools/bin/bb")))
              "wasmtime" (let [top (first (fs/list-dir extracted))]
                           (upload! host (fs/path top "wasmtime") (str root "/tools/bin/wasmtime"))
                           (remote! host (str "chmod 700 " root "/tools/bin/wasmtime")))
              (let [top (first (fs/list-dir extracted))
                    package-name (str (fs/file-name top))]
                (when-not (re-matches #"[A-Za-z0-9_.-]+" package-name)
                  (throw (ex-info "Unsafe archive root" {})))
                (upload! host top (str root "/install/"))
                (remote! host (str "bash " root "/install/" package-name
                                   "/install.sh --prefix=" root "/tools --disable-ldconfig"))))
            (remote! host (str "printf '%s\\n' " (:sha256 artifact) " > " marker))
            (println (str "installed artifact=" name))
            (flush)))))
    (upload! host lock (str root "/tools/lock.json"))
    (doseq [executable ["rustc" "wasmtime" "bb"]]
      (println (remote! host (str root "/tools/bin/" executable " --version"))))))

(when (= *file* (System/getProperty "babashka.file"))
  (prepare! (options *command-line-args*)))
