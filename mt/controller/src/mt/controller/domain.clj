(ns mt.controller.domain
  (:require [malli.core :as m]))

(def modes [:enum :fixture/succeed :fixture/fail])
(def statuses [:enum :pending :active :completed :failed :uncertain])
(def effect-schema
  [:map {:closed true}
   [:id :uuid] [:command-id :uuid] [:mode modes] [:status statuses]])

(defn- event-schema [type fields]
  (into [:map {:closed true}
         [:id :uuid] [:at-ms [:int {:min 0}]]
         [:type [:= type]] [:command-id :uuid]]
        fields))

(def valid-command?
  (m/validator (event-schema :fixture/requested [[:mode modes]])))

(def ^:private event-validators
  {:fixture/requested valid-command?
   :effect/dispatched (m/validator (event-schema :effect/dispatched [[:effect-id :uuid]]))
   :effect/recovered (m/validator (event-schema :effect/recovered [[:effect-id :uuid]]))
   :effect/finished (m/validator
                     (event-schema :effect/finished
                                   [[:effect-id :uuid]
                                    [:status [:enum :completed :failed :uncertain]]
                                    [:reason {:optional true} [:enum :timeout :shutdown :interrupted :exception]]]))})

(def initial-state
  {:accepted 0 :effects {:pending 0 :active 0 :completed 0 :failed 0 :uncertain 0}})

(defn valid-state? [{:keys [accepted effects]}]
  (and (= (set (keys (:effects initial-state))) (set (keys effects)))
       (every? nat-int? (vals effects))
       (= accepted (reduce + (vals effects)))))

(defn effect-id [command-id]
  ;; One effect per fixture command. The ID is independent of time and admission.
  command-id)

(defn transition [state {:keys [type command-id mode status] target :effect-id :as event} current]
  (when-not ((get event-validators type (constantly false)) event)
    (throw (ex-info "Invalid event" {:rejected true})))
  (let [matching? (and current (= target (:id current)) (= command-id (:command-id current)))
        previous (:status current)
        effect (case type
                 :fixture/requested
                 (when (nil? current)
                   {:id (effect-id command-id)
                    :command-id command-id :mode mode :status :pending})

                 :effect/dispatched
                 (when (and matching? (= previous :pending))
                   (assoc current :status :active))

                 :effect/finished
                 (when (and matching? (= previous :active) status)
                   (assoc current :status status))

                 :effect/recovered
                 (when (and matching? (= previous :active))
                   (assoc current :status :uncertain)))]
    (when-not (and effect (m/validate effect-schema effect))
      (throw (ex-info "Invalid state transition" {:rejected true})))
    (let [updated (cond-> (update-in state [:effects (:status effect)] inc)
                    previous (update-in [:effects previous] dec)
                    (= type :fixture/requested) (update :accepted inc))]
      (when-not (valid-state? updated)
        (throw (ex-info "Operational state invariant violated" {})))
      {:state updated :effects [effect]})))
