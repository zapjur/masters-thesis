(ns mt.controller.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [malli.core :as m])
  (:import [java.io PushbackReader StringReader]
           [java.nio.file Files LinkOption OpenOption]
           [java.nio.file.attribute PosixFileAttributes PosixFilePermission]))

(defn integer-between [low high]
  [:int {:min low :max high}])

(def schema
  [:map {:closed true}
   [:host [:= "127.0.0.1"]]
   [:port (integer-between 1 65535)]
   [:data-dir [:re #"^/[^\u0000]+$"]]
   [:database [:re #"^[A-Za-z0-9_-][A-Za-z0-9_.-]{0,127}$"]]
   [:credential-file [:re #"^/[^\u0000]+$"]]
   [:queue-capacity (integer-between 3 4096)]
   [:reserved-capacity (integer-between 2 4095)]
   [:effect-concurrency (integer-between 1 128)]
   [:command-timeout-ms (integer-between 500 60000)]
   [:effect-timeout-ms (integer-between 1 60000)]
   [:shutdown-timeout-ms (integer-between 1000 60000)]])

(defn validate [config]
  (when-not (and (m/validate schema config)
                 (< (:effect-concurrency config) (:reserved-capacity config)
                    (:queue-capacity config)))
    (throw (ex-info "Invalid controller configuration or queue limits" {})))
  config)

(defn- private-text [file limit description]
  (let [path (.toPath (io/file file))
        no-follow (into-array LinkOption [LinkOption/NOFOLLOW_LINKS])
        attributes (Files/readAttributes path PosixFileAttributes no-follow)]
    (when-not (and (.isRegularFile attributes)
                   (= (.getName (.owner attributes)) (System/getProperty "user.name"))
                   (= #{PosixFilePermission/OWNER_READ PosixFilePermission/OWNER_WRITE}
                      (set (.permissions attributes)))
                   (<= (.size attributes) limit))
      (throw (ex-info (str description " must be an owner-only file with mode 0600") {})))
    (with-open [input (Files/newInputStream path (into-array OpenOption [LinkOption/NOFOLLOW_LINKS]))]
      (let [bytes (.readNBytes input (inc limit))]
        (when (> (alength bytes) limit)
          (throw (ex-info (str description " exceeds its size limit") {})))
        (String. bytes "UTF-8")))))

(defn load-config [path]
  (with-open [reader (PushbackReader. (StringReader. (private-text path 16384 "Configuration")))]
    (let [config (edn/read {:eof ::eof} reader)]
      (when-not (= ::eof (edn/read {:eof ::eof} reader))
        (throw (ex-info "Expected a single controller configuration map" {})))
      (validate config))))

(defn credential [file]
  (let [token (str/replace (private-text file 65 "Credential") #"\n$" "")]
    (when-not (re-matches #"[0-9a-f]{64}" token)
      (throw (ex-info "Expected a 32-byte random credential encoded as lowercase hex" {})))
    token))
