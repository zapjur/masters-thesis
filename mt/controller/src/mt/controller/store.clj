(ns mt.controller.store
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [mt.controller.domain :as domain]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import [java.nio.channels FileChannel OverlappingFileLockException]
           [java.nio.file Files LinkOption OpenOption StandardOpenOption]
           [java.nio.file.attribute PosixFilePermissions]
           [java.security MessageDigest]
           [java.util HexFormat UUID]))

(defn query [connection sql]
  (jdbc/execute! connection sql {:builder-fn rs/as-unqualified-lower-maps}))

(defn one [connection sql]
  (first (query connection sql)))

(defn migrate! [connection]
  (let [source (slurp (io/resource "mt/controller/001.edn"))
        migrations [{:version 1 :sql (edn/read-string source)
                     :checksum (.formatHex (HexFormat/of)
                                           (.digest (MessageDigest/getInstance "SHA-256")
                                                    (.getBytes source "UTF-8")))}]]
    (jdbc/with-transaction [tx connection]
      (when (and (not (one tx ["SELECT name FROM sqlite_schema WHERE name = 'schema_migrations' AND type = 'table'"]))
                 (one tx ["SELECT name FROM sqlite_schema WHERE name NOT GLOB 'sqlite_*' LIMIT 1"]))
        (throw (ex-info "Unrecognized database schema" {})))
      (query tx ["CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, checksum TEXT NOT NULL)"])
      (let [applied (query tx ["SELECT version, checksum FROM schema_migrations ORDER BY version"])
            expected (mapv #(select-keys % [:version :checksum]) migrations)]
        (when-not (= applied (vec (take (count applied) expected)))
          (throw (ex-info "Incompatible or newer controller database schema" {})))
        (doseq [{:keys [version checksum sql]} (drop (count applied) migrations)]
          (doseq [statement sql] (query tx [statement]))
          (when (= version 1)
            (query tx ["INSERT INTO operational_state VALUES (1, ?)" (pr-str domain/initial-state)]))
          (query tx ["INSERT INTO schema_migrations VALUES (?, ?)" version checksum]))))))

;; Avoid opening a second lock descriptor in this JVM for the same store.
(defonce ^:private owned-paths (atom #{}))

(defn- claim-path! [path]
  (locking owned-paths
    (when (contains? @owned-paths path)
      (throw (ex-info "Controller database already has an owner" {})))
    (swap! owned-paths conj path)))

(defn close! [{:keys [connection lock-channel path writer]}]
  (let [close-resources! (fn []
                           (try (.close connection)
                                (finally
                                  (try (.close lock-channel)
                                       (finally (swap! owned-paths disj path))))))]
    (if-let [thread (and writer @writer)]
      (if (.isAlive thread)
        (.start (Thread/ofVirtual) (fn [] (.join thread) (close-resources!)))
        (close-resources!))
      (close-resources!))))

(defn- verify-settings! [connection]
  (doseq [sql ["PRAGMA journal_mode=WAL" "PRAGMA foreign_keys=ON"
               "PRAGMA synchronous=FULL" "PRAGMA fullfsync=ON" "PRAGMA busy_timeout=250"]]
    (query connection [sql]))
  (doseq [[pragma column expected] [["journal_mode" :journal_mode "wal"]
                                    ["foreign_keys" :foreign_keys 1]
                                    ["synchronous" :synchronous 2]
                                    ["fullfsync" :fullfsync 1]
                                    ["busy_timeout" :timeout 250]]]
    (when-not (= expected (get (one connection [(str "PRAGMA " pragma)]) column))
      (throw (ex-info "Unexpected SQLite setting" {:pragma pragma})))))

(defn open! [{:keys [data-dir database]}]
  (let [directory (.toPath (.getCanonicalFile (io/file data-dir)))
        private-mode (PosixFilePermissions/fromString "rwx------")
        file (.resolve directory database)
        path (str file)
        lock-path (.resolve directory (str database ".owner"))
        no-follow (into-array LinkOption [LinkOption/NOFOLLOW_LINKS])]
    (Files/createDirectories directory (into-array java.nio.file.attribute.FileAttribute
                                                   [(PosixFilePermissions/asFileAttribute private-mode)]))
    (when-not (and (= directory (.getParent file))
                   (= private-mode (Files/getPosixFilePermissions directory no-follow))
                   (= (.getName (Files/getOwner directory (make-array LinkOption 0)))
                      (System/getProperty "user.name")))
      (throw (ex-info "Database requires an owner-only data directory with mode 0700" {})))
    (when (and (Files/exists file no-follow)
               (not (and (Files/isRegularFile file no-follow)
                         (= 1 (Files/getAttribute file "unix:nlink" no-follow)))))
      (throw (ex-info "Database must be a regular file with a single link" {})))
    (claim-path! path)
    (try
      (let [channel (FileChannel/open lock-path (into-array OpenOption [StandardOpenOption/CREATE
                                                                       StandardOpenOption/WRITE
                                                                       LinkOption/NOFOLLOW_LINKS]))]
        (try
          (when-not (try (.tryLock channel) (catch OverlappingFileLockException _ nil))
            (throw (ex-info "Controller database already has an owner" {})))
          (let [connection (jdbc/get-connection (jdbc/get-datasource {:dbtype "sqlite" :dbname path}))]
            (try
              (verify-settings! connection)
              (migrate! connection)
              {:connection connection :lock-channel channel :path path :writer (atom nil)}
              (catch Throwable error
                (.close connection)
                (throw error))))
          (catch Throwable error
            (.close channel)
            (throw error))))
      (catch Throwable error
        (swap! owned-paths disj path)
        (throw error)))))

(defn state [connection]
  (let [value (some-> (one connection ["SELECT body FROM operational_state WHERE id = 1"])
                      :body edn/read-string)]
    (when-not (domain/valid-state? value)
      (throw (ex-info "Invalid persisted operational state" {})))
    value))

(defn read-effect [{:keys [id command_id mode status]}]
  {:id (UUID/fromString id) :command-id (UUID/fromString command_id)
   :mode (keyword mode) :status (keyword status)})

(defn next-effect [connection status]
  (some-> (one connection ["SELECT * FROM effect_intents WHERE status = ? ORDER BY created_sequence LIMIT 1"
                           (name status)])
          read-effect))

(defn apply-event! [connection {:keys [id command-id at-ms type] :as event}]
  (jdbc/with-transaction [tx connection]
    (when (one tx ["SELECT id FROM execution_events WHERE id = ?" (str id)])
      (throw (ex-info "Duplicate event ID" {:rejected true})))
    (let [current (some-> (one tx ["SELECT * FROM effect_intents WHERE command_id = ?" (str command-id)])
                          read-effect)
          {:keys [state effects]} (domain/transition (state tx) event current)]
      (query tx ["INSERT INTO execution_events (id, command_id, type, at_ms, body) VALUES (?, ?, ?, ?, ?)"
                 (str id) (str command-id) (subs (str type) 1) at-ms (pr-str event)])
      (let [sequence (:sequence (one tx ["SELECT last_insert_rowid() AS sequence"]))]
        (doseq [{:keys [id command-id mode status]} effects]
          (query tx ["INSERT INTO effect_intents VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET status = excluded.status, last_sequence = excluded.last_sequence"
                     (str id) (str command-id) sequence sequence (subs (str mode) 1) (name status)])))
      (query tx ["UPDATE operational_state SET body = ? WHERE id = 1" (pr-str state)])
      state)))
