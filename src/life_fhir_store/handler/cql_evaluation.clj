(ns life-fhir-store.handler.cql-evaluation
  "A CQL evaluation handler modeled after the evaluate endpoint of

  https://github.com/DBCG/cql_execution_service"
  (:require
    [cheshire.core :as json]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]
    [life-fhir-store.cql :as cql]
    [life-fhir-store.datomic.pull :as pull]
    [life-fhir-store.elm.compiler :as compiler]
    [life-fhir-store.elm.evaluator :as evaluator]
    [life-fhir-store.elm.util :as elm-util]
    [life-fhir-store.middleware.cors :refer [wrap-cors]]
    [life-fhir-store.middleware.exception :refer [wrap-exception]]
    [life-fhir-store.middleware.json :refer [wrap-json]]
    [manifold.deferred :as md]
    [ring.util.response :as ring])
  (:import
    [java.time OffsetDateTime]))


(s/fdef pull-pattern
  :args (s/cat :structure-definitions (s/map-of string? :life/structure-definition)
               :type-name string?))

(defn- pull-pattern [structure-definitions type-name]
  (if-let [structure-definition (structure-definitions type-name)]
    (pull/pull-pattern structure-definitions structure-definition)
    [(keyword type-name "id")]))


(defn- bundle [structure-definitions {:keys [type result]}]
  (case (:type type)
    "ListTypeSpecifier"
    #_(let [[_ type-name] (elm-util/parse-qualified-name (:name (:elementType type)))
          pattern (pull-pattern structure-definitions type-name)]
      {:result
       (json/generate-string
         (into
           []
           (comp
             (take 2)
             (map
               (fn [entity]
                 (let [db (d/entity-db entity)]
                   (d/pull db pattern (:db/id entity)))))
             (map #(assoc % :resourceType type-name)))
           result)
         {:key-fn name
          :pretty true})
       :resultType "Bundle"})
    nil
    "NamedTypeSpecifier"
    {:result result
     :resultType (second (elm-util/parse-qualified-name (:name type)))}))


(defn- to-error [deferred]
  (-> deferred
      (md/chain'
        (fn [result]
          (if (::anom/category result)
            (md/error-deferred result)
            (md/success-deferred result))))))


(defn- handler-intern [conn cache structure-definitions]
  (fn [{:keys [body]}]
    (if-let [code (:code body)]
      (-> (md/let-flow'
            [elm (to-error (md/future (cql/translate code)))
             expression-defs (to-error (md/future (compiler/compile-library elm {})))
             results (evaluator/evaluate (d/db conn) (OffsetDateTime/now) expression-defs)]
            (ring/response
              (mapcat
                (fn [[name result]]
                  (if (instance? Exception result)
                    [{:name name
                     :error (.getMessage ^Exception result)
                      :location "[?:?]"}]
                    (if-let [bundle (bundle structure-definitions result)]
                      [(assoc bundle
                         :name name
                         :location "[?:?]")]
                      [])))
                results)))
          (md/catch'
            (fn [e]
              (ring/response
                [{:translation-error
                  (cond
                    (::anom/category e)
                    (or (::anom/message e)
                        (name (::anom/category e)))

                    (instance? Exception e)
                    (.getMessage ^Exception e)

                    :else
                    "Unknown error")}]))))
      (ring/response [{:translation-error "Missing CQL code"}]))))


(s/def :handler/cql-evaluation fn?)


(s/fdef handler
  :args (s/cat :conn ::ds/conn :cache some? :structure-definitions map?)
  :ret :handler/cql-evaluation)

(defn handler
  "Takes a Datomic `conn` and aa `cache` atom and returns a CQL evaluation
  Ring handler."
  [conn cache structure-definitions]
  (-> (handler-intern conn cache structure-definitions)
      (wrap-exception)
      (wrap-json)
      (wrap-cors)))
