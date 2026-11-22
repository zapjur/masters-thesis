(ns mt.controller.http
  (:require [clojure.data.json :as json]
            [malli.core :as m]
            [mt.controller.coordinator :as coordinator]
            [reitit.ring :as router]
            [ring.adapter.jetty :as jetty])
  (:import [java.security MessageDigest]
           [java.util.concurrent Semaphore]
           [org.eclipse.jetty.server ConnectionLimit]))

(def health-schema
  [:map [:ready :boolean] [:version :string] [:lifecycle :string] [:storage_ready :boolean]])
(def status-schema
  (into health-schema [[:queue [:map [:capacity :int] [:occupancy :int] [:reserved :int]]]
    [:effect_capacity :int]
    [:effect_slots_used :int]
    [:effects [:map [:pending :int] [:active :int] [:completed :int] [:failed :int] [:uncertain :int]]]]))

(defn digest [value]
  (.digest (MessageDigest/getInstance "SHA-256") (.getBytes value "UTF-8")))

(defn response [status data]
  {:status status :headers {"Content-Type" "application/json; charset=utf-8"
                            "Cache-Control" "no-store"
                            "Connection" "close"}
   :body (json/write-str data)})

(defn error [status code]
  (response status {:error {:code code}}))

(defn view [coord schema fields]
  (let [data (select-keys (coordinator/status coord) fields)]
    (if (m/validate schema data)
      (response (if (:ready data) 200 503) data)
      (error 500 "invalid_status"))))

(defn handler [coord token]
  (let [expected (digest (str "Bearer " token))
        permits (Semaphore. 8)
        health-fields [:ready :version :lifecycle :storage_ready]
        dispatch (router/ring-handler
                  (router/router
                   [["/api/v1/health" {:get (fn [_] (view coord health-schema health-fields))}]
                    ["/api/v1/status" {:get (fn [_] (view coord status-schema
                                                         (into health-fields [:queue :effect_capacity :effect_slots_used :effects])))}]])
                  (router/create-default-handler
                   {:not-found (fn [_] (error 404 "not_found"))
                    :method-not-allowed (fn [_] (error 405 "method_not_allowed"))
                    :not-acceptable (fn [_] (error 406 "not_acceptable"))}))]
    (fn [{:keys [headers] :as request}]
      (if-not (.tryAcquire permits)
        (error 503 "overloaded")
        (try
          (cond
            (not (MessageDigest/isEqual expected (digest (get headers "authorization" ""))))
            (assoc-in (error 401 "unauthorized") [:headers "WWW-Authenticate"] "Bearer")

            (or (contains? headers "transfer-encoding")
                (not (contains? #{"0" nil} (get headers "content-length"))))
            (error 413 "unexpected_body")

            :else (dispatch request))
          (finally (.release permits)))))))

(defn start! [coord {:keys [host port shutdown-timeout-ms]} token]
  (jetty/run-jetty (handler coord token)
                   {:host host :port port :join? false
                    :acceptor-threads 1 :selector-threads 1
                    :min-threads 8 :max-threads 16 :max-queued-requests 64
                    :request-header-size 4096 :response-header-size 4096
                    :max-idle-time 2000 :send-server-version? false
                    :configurator (fn [server]
                                    (.setStopTimeout server shutdown-timeout-ms)
                                    (.addBean server (ConnectionLimit. 32 server)))}))
