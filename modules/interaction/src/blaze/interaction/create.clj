(ns blaze.interaction.create
  "FHIR create interaction.

  https://www.hl7.org/fhir/http.html#create"
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.async.comp :as ac]
    [blaze.db.api :as d]
    [blaze.db.spec]
    [blaze.fhir.response.create :as response]
    [blaze.handler.util :as handler-util]
    [blaze.interaction.create.spec]
    [blaze.luid :refer [luid]]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(defn- resource-type-mismatch-msg [type body]
  (format "Resource type `%s` doesn't match the endpoint type `%s`."
          (-> body :fhir/type name) type))


(defn- validate-resource [type body]
  (cond
    (nil? body)
    (throw-anom
      ::anom/incorrect
      "Missing HTTP body."
      :fhir/issue "invalid")

    (not= type (-> body :fhir/type name))
    (throw-anom
      ::anom/incorrect
      (resource-type-mismatch-msg type body)
      :fhir/issue "invariant"
      :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH")

    :else body))


(defn- handler-intern [node executor]
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        :keys [headers body]
        ::reitit/keys [router]}]
    (let [return-preference (handler-util/preference headers "return")
          id (luid)]
      (-> (ac/supply (validate-resource type body))
          (ac/then-apply #(assoc % :id id))
          (ac/then-compose #(d/transact node [[:create %]]))
          ;; it's important to switch to the executor here, because otherwise
          ;; the central indexing thread would execute response building.
          (ac/then-apply-async identity executor)
          (ac/then-compose
            #(response/build-created-response
               router return-preference % type id))
          (ac/exceptionally handler-util/error-response)))))


(defn handler [node executor]
  (-> (handler-intern node executor)
      (wrap-observe-request-duration "create")))


(defmethod ig/pre-init-spec :blaze.interaction/create [_]
  (s/keys :req-un [:blaze.db/node ::executor]))


(defmethod ig/init-key :blaze.interaction/create
  [_ {:keys [node executor]}]
  (log/info "Init FHIR create interaction handler")
  (handler node executor))
