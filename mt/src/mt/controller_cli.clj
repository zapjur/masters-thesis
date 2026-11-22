(ns mt.controller-cli
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.net HttpURLConnection Proxy URL]
           [java.nio.file.attribute PosixFilePermission]))

(defn config-path [args]
  (when-not (and (= 2 (count args)) (= "--config" (first args)))
    (throw (ex-info "Expected --config PATH" {})))
  (str (fs/absolutize (second args))))

(defn start! [repo args]
  (let [path (config-path args)
        java-home (System/getenv "JAVA_HOME")
        java (when java-home (fs/path java-home "bin/java"))]
    (when-not (fs/which "clojure")
      (throw (ex-info "Controller startup requires the Clojure CLI" {})))
    (when-not (and java (fs/executable? java)
                   (let [{:keys [exit err]} @(process/process [(str java) "-version"]
                                                              {:out :string :err :string})]
                     (and (zero? exit) (re-find #"(?m)^(?:openjdk|java) version \"25(?:[.\"])" err))))
      (throw (ex-info "Set JAVA_HOME to a Java 25 JDK before starting the controller" {})))
    (process/exec "sh" "-c" "cd \"$1\" && exec clojure -Srepro -M:controller --config \"$2\""
                  "mt-controller" (str (fs/path repo "mt")) path)))

(defn- private-text [path limit description]
  (when-not (and (fs/regular-file? path)
                 (not (fs/sym-link? path))
                 (= #{PosixFilePermission/OWNER_READ PosixFilePermission/OWNER_WRITE}
                    (fs/posix-file-permissions path))
                 (= (.getName (fs/owner path)) (System/getProperty "user.name"))
                 (<= (fs/size path) limit))
    (throw (ex-info (str "Expected a private " description " file with mode 0600") {})))
  (with-open [stream (io/input-stream path)]
    (let [bytes (.readNBytes stream (inc limit))]
      (when (> (alength bytes) limit)
        (throw (ex-info (str description " exceeds its size limit") {})))
      (String. bytes "UTF-8"))))

(defn status! [args]
  (let [{:keys [host port credential-file]} (edn/read-string
                                            (private-text (config-path args) 16384 "configuration"))]
    (when-not (and (= host "127.0.0.1") (integer? port) (<= 1 port 65535)
                   (string? credential-file) (fs/absolute? credential-file))
      (throw (ex-info "Invalid controller Management API configuration" {})))
    (let [token (str/replace (private-text credential-file 65 "credential") #"\n$" "")]
      (when-not (re-matches #"[0-9a-f]{64}" token)
        (throw (ex-info "Invalid controller credential format" {})))
      (let [^HttpURLConnection connection (.openConnection (URL. (str "http://127.0.0.1:" port "/api/v1/status"))
                                                            Proxy/NO_PROXY)]
        (try
          (.setConnectTimeout connection 1000)
          (.setReadTimeout connection 2000)
          (.setInstanceFollowRedirects connection false)
          (.setRequestProperty connection "Authorization" (str "Bearer " token))
          (case (long (.getResponseCode connection))
            200 (with-open [body (.getInputStream connection)]
                  (let [bytes (.readNBytes body 4097)]
                    (when (> (alength bytes) 4096)
                      (throw (ex-info "Controller status response exceeds the size limit" {})))
                    (println (json/generate-string (json/parse-string (String. bytes "UTF-8"))
                                                   {:pretty true}))))
            401 (throw (ex-info "Controller rejected the local credential" {}))
            503 (throw (ex-info "Controller is reachable but unavailable" {}))
            (throw (ex-info (str "Unexpected HTTP status from controller: " (.getResponseCode connection)) {})))
          (catch java.io.IOException _
            (throw (ex-info "Controller is unreachable at the configured loopback address" {})))
          (finally (.disconnect connection)))))))
