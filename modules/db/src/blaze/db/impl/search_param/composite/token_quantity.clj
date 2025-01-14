(ns blaze.db.impl.search-param.composite.token-quantity
  (:require
    [blaze.anomaly :refer [if-ok when-ok]]
    [blaze.byte-string :as bs]
    [blaze.coll.core :as coll]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.protocols :as p]
    [blaze.db.impl.search-param.composite.common :as cc]
    [blaze.db.impl.search-param.quantity :as spq]
    [blaze.db.impl.search-param.util :as u]
    [blaze.fhir-path :as fhir-path]
    [cognitect.anomalies :as anom]))


(set! *warn-on-reflection* true)


(defn- prefix-with* [quantity-value token-value]
  (bs/concat token-value quantity-value))


(defn- prefix-with [{:keys [op] :as quantity-value} token-value]
  (if (identical? :eq op)
    (-> quantity-value
        (update :lower-bound prefix-with* token-value)
        (update :upper-bound prefix-with* token-value))
    (-> quantity-value
        (update :exact-value prefix-with* token-value))))


(def ^:private ^:const ^long prefix-length
  "Length of the prefix while scanning over indices consists of the token and
  the unit of the quantity."
  (* 2 codec/v-hash-size))


(defrecord SearchParamCompositeTokenQuantity
  [name url type base code c-hash main-expression c1 c2]
  p/SearchParam
  (-compile-value [_ _ value]
    (let [[v1 v2] (cc/split-value value)
          token-value (cc/compile-component-value c1 v1)]
      (if-ok [quantity-value (cc/compile-component-value c2 v2)]
        (prefix-with quantity-value token-value)
        (case (::spq/category quantity-value)
          ::spq/invalid-decimal-value
          (assoc quantity-value
            ::anom/message (spq/invalid-decimal-value-msg code v2))
          ::spq/unsupported-prefix
          (assoc quantity-value
            ::anom/message
            (spq/unsupported-prefix-msg
              code (::spq/unsupported-prefix quantity-value)))
          quantity-value))))

  (-resource-handles [_ context tid _ value]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (spq/resource-keys! context c-hash tid prefix-length value)))

  (-resource-handles [_ context tid _ value start-id]
    (coll/eduction
      (u/resource-handle-mapper context tid)
      (spq/resource-keys! context c-hash tid prefix-length value start-id)))

  (-matches? [_ context resource-handle _ values]
    (some #(spq/matches? context c-hash resource-handle prefix-length %) values))

  (-index-values [_ resolver resource]
    (when-ok [values (fhir-path/eval resolver main-expression resource)]
      (into [] (mapcat #(cc/index-values resolver % c1 c2)) values))))

