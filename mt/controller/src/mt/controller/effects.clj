(ns mt.controller.effects
  (:import [java.time Duration]))

(defn fixture! [{:keys [mode]}]
  (case mode
    :fixture/succeed :completed
    :fixture/fail :failed))

(defn- invoke-effect [adapter effect]
  (try
    {:status (case (adapter effect)
               :completed :completed
               :failed :failed
               :uncertain :uncertain)}
    (catch InterruptedException _ {:status :uncertain :reason :interrupted})
    (catch Exception _ {:status :failed :reason :exception})))

(defn launch! [adapter effect timeout-ms report!]
  (let [result (volatile! {:status :uncertain})
        worker (.unstarted (Thread/ofVirtual) #(vreset! result (invoke-effect adapter effect)))
        supervisor (.unstarted
                    (Thread/ofVirtual)
                    (fn []
                      (try
                        (when (Thread/interrupted)
                          (throw (InterruptedException.)))
                        (.start worker)
                        (if (.join worker (Duration/ofMillis timeout-ms))
                          (report! @result)
                          (do
                            (.interrupt worker)
                            (report! {:status :uncertain :reason :timeout})))
                        (catch InterruptedException _
                          (.interrupt worker)
                          (report! {:status :uncertain :reason :shutdown})))))]
    (.start supervisor)
    {:worker worker :supervisor supervisor}))

(defn stopped? [{:keys [worker supervisor]}]
  (and (not (.isAlive supervisor)) (not (.isAlive worker))))

(defn cancel! [{:keys [worker supervisor]}]
  (if (.isAlive supervisor)
    (.interrupt supervisor)
    (.interrupt worker)))
