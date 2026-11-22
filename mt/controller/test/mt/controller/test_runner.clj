(ns mt.controller.test-runner
  (:require [clojure.test :as t]
            [mt.controller.foundation-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (t/run-tests 'mt.controller.foundation-test)]
    (shutdown-agents)
    (when (pos? (+ fail error)) (System/exit 1))))
