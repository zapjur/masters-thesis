(ns mt.controller.system
  (:require [integrant.core :as ig]
            [mt.controller.config :as config]
            [mt.controller.coordinator :as coordinator]
            [mt.controller.effects :as effects]
            [mt.controller.http :as http]
            [mt.controller.store :as store]))

(defmethod ig/init-key ::config [_ settings] (config/validate settings))
(defmethod ig/init-key ::credential [_ {:keys [config]}]
  (config/credential (:credential-file config)))

(defmethod ig/init-key ::storage [_ {:keys [config]}] (store/open! config))
(defmethod ig/halt-key! ::storage [_ storage] (store/close! storage))

(defmethod ig/init-key ::coordinator [_ {:keys [config storage]}]
  (coordinator/start! storage config))
(defmethod ig/halt-key! ::coordinator [_ coordinator] (coordinator/stop! coordinator))

(defmethod ig/init-key ::effects [_ {:keys [coordinator run-effect]}]
  (reset! (:run-effect coordinator) run-effect)
  coordinator)
(defmethod ig/halt-key! ::effects [_ coordinator] (coordinator/quiesce! coordinator))

(defmethod ig/init-key ::management [_ {:keys [config credential coordinator]}]
  {:server (http/start! coordinator config credential) :coordinator coordinator})
(defmethod ig/halt-key! ::management [_ {:keys [server coordinator]}]
  (coordinator/quiesce! coordinator)
  (.stop server))

(defn components [settings run-effect]
  {::config settings
   ::credential {:config (ig/ref ::config)}
   ::storage {:config (ig/ref ::config) :credential (ig/ref ::credential)}
   ::coordinator {:config (ig/ref ::config) :storage (ig/ref ::storage)}
   ::effects {:coordinator (ig/ref ::coordinator) :run-effect run-effect}
   ::management {:config (ig/ref ::config) :credential (ig/ref ::credential)
                 :coordinator (ig/ref ::coordinator) :effects (ig/ref ::effects)}})

(defn start! [settings run-effect]
  (try
    (ig/init (components settings run-effect))
    (catch clojure.lang.ExceptionInfo error
      (when-let [partial (:system (ex-data error))]
        (ig/halt! partial))
      (throw error))))

(defn -main [& args]
  (when-not (and (= 2 (count args)) (= "--config" (first args)))
    (throw (ex-info "Expected --config PATH" {})))
  (when-not (= 25 (.feature (Runtime/version)))
    (throw (ex-info "Controller requires Java 25. Set JAVA_HOME to a Java 25 JDK." {})))
  (try
    (let [system (start! (config/load-config (second args)) effects/fixture!)]
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. (fn []
                                   (ig/halt! system)
                                   (coordinator/diagnostic! {:type :controller/stopped}))))
      (coordinator/diagnostic! {:type :controller/ready})
      (.join (:server (::management system))))
    (catch Exception error
      (let [cause (or (ex-cause error) error)]
        (coordinator/diagnostic! {:type :controller/startup-error
                                  :error (str (class cause))})
        (binding [*out* *err*]
          (println "Controller startup failed. Check the configuration, credential permissions, listener, and database ownership."))
        (System/exit 1)))))
