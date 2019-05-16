(ns blaze.handler.fhir.capabilities
  "FHIR capabilities endpoint.

  https://www.hl7.org/fhir/http.html#capabilities"
  (:require
    [blaze.middleware.exception :refer [wrap-exception]]
    [blaze.middleware.json :refer [wrap-json]]
    [clojure.spec.alpha :as s]
    [ring.util.response :as ring]))


(defn- resource [{:keys [id]}]
  {:type id
   :interaction
   [{:code "read"}
    {:code "update"}]})


(defn handler-intern [base-uri version structure-definitions]
  (fn [_]
    (ring/response
      {:resourceType "CapabilityStatement"
       :status "active"
       :kind "instance"
       :date "2019-05-15T00:00:00Z"
       :software
       {:name "Blaze"
        :version version}
       :implementation
       {:description (str "Blaze running at " base-uri "/fhir")
        :url (str base-uri "/fhir")}
       :fhirVersion "4.0.0"
       :format ["application/fhir+json"]
       :rest
       [{:mode "server"
         :resource
         (into
           []
           (comp
             (filter #(= "resource" (:kind %)))
             (map resource))
           (vals structure-definitions))
         :interaction
         [{:code "transaction"}]}]})))


(s/def :handler.fhir/capabilities fn?)


(s/fdef handler
  :args (s/cat :version string? :structure-definitions map?)
  :ret :handler.fhir/capabilities)

(defn handler
  ""
  [base-uri version structure-definitions]
  (-> (handler-intern base-uri version structure-definitions)
      (wrap-json)
      (wrap-exception)))