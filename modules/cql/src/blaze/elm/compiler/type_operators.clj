(ns blaze.elm.compiler.type-operators
  "22. Type Operators"
  (:require
    [blaze.anomaly :refer [throw-anom]]
    [blaze.elm.compiler.core :as core]
    [blaze.elm.compiler.macros :refer [defbinop defunop]]
    [blaze.elm.date-time :as date-time]
    [blaze.elm.protocols :as p]
    [blaze.elm.quantity :as quantity]
    [blaze.elm.util :as elm-util]
    [blaze.fhir.spec :as fhir-spec]
    [cognitect.anomalies :as anom]))


;; 22.1. As
;;
;; The As operator allows the result of an expression to be cast as a given
;; target type. This allows expressions to be written that are statically typed
;; against the expected run-time type of the argument. If the argument is not of
;; the specified type, and the strict attribute is false (the default), the
;; result is null. If the argument is not of the specified type and the strict
;; attribute is true, an exception is thrown.
(defrecord AsExpression [operand matches-type?]
  core/Expression
  (-eval [_ context resource scope]
    (let [value (core/-eval operand context resource scope)]
      (when (matches-type? value)
        value))))


(defn- matches-elm-named-type-fn [type-name]
  (case type-name
    "Boolean" boolean?
    "Integer" int?
    "DateTime" date-time/temporal?
    "Quantity" quantity/quantity?
    (throw-anom
      ::anom/unsupported
      (format "Unsupported ELM type `%s` in As expression." type-name)
      :type-name type-name)))


(defn- matches-named-type-fn [type-name]
  (let [[type-ns type-name] (elm-util/parse-qualified-name type-name)]
    (case type-ns
      "http://hl7.org/fhir"
      (comp #{(keyword "fhir" type-name)} fhir-spec/fhir-type)
      "urn:hl7-org:elm-types:r1"
      (matches-elm-named-type-fn type-name)
      (throw-anom
        ::anom/unsupported
        (format "Unsupported type namespace `%s` in As expression." type-ns)
        :type-ns type-ns))))


(defn- matches-type-specifier-fn [as-type-specifier]
  (case (:type as-type-specifier)
    "NamedTypeSpecifier"
    (matches-named-type-fn (:name as-type-specifier))

    "ListTypeSpecifier"
    (let [pred (matches-type-specifier-fn (:elementType as-type-specifier))]
      (fn matches-type? [x]
        (every? pred x)))

    (throw-anom
      ::anom/unsupported
      (format "Unsupported type specifier type `%s` in As expression."
              (:type as-type-specifier))
      :type-specifier-type (:type as-type-specifier))))


(defn- matches-type-fn
  [{as-type :asType as-type-specifier :asTypeSpecifier :as expression}]
  (cond
    as-type
    (matches-named-type-fn as-type)

    as-type-specifier
    (matches-type-specifier-fn as-type-specifier)

    :else
    (throw-anom
      ::anom/fault
      "Invalid As expression without `as-type` and `as-type-specifier`."
      :expression expression)))


(defmethod core/compile* :elm.compiler.type/as
  [context {:keys [operand] :as expression}]
  (when-some [operand (core/compile* context operand)]
    (->AsExpression operand (matches-type-fn expression))))


;; 22.2. CanConvert


;; 22.3. CanConvertQuantity
(defbinop can-convert-quantity [x unit]
  (p/can-convert-quantity x unit))


;; 22.4. Children
(defrecord ChildrenOperatorExpression [source]
  core/Expression
  (-eval [_ context resource scope]
    (p/children (core/-eval source context resource scope))))


(defmethod core/compile* :elm.compiler.type/children
  [context {:keys [source]}]
  (when-let [source (core/compile* context source)]
    (->ChildrenOperatorExpression source)))


;; 22.5. Convert


;; 22.6. ConvertQuantity
(defbinop convert-quantity [x unit]
  (p/convert-quantity x unit))


;; 22.7. ConvertsToBoolean

;; 22.8. ConvertsToDate

;; 22.9. ConvertsToDateTime

;; 22.10. ConvertsToDecimal

;; 22.11. ConvertsToInteger

;; 22.12. ConvertsToQuantity

;; 22.13. ConvertsToRatio

;; 22.14. ConvertsToString

;; 22.15. ConvertsToTime

;; 22.16. Descendents
(defrecord DescendentsOperatorExpression [source]
  core/Expression
  (-eval [_ context resource scope]
    (p/descendents (core/-eval source context resource scope))))


(defmethod core/compile* :elm.compiler.type/descendents
  [context {:keys [source]}]
  (when-let [source (core/compile* context source)]
    (->DescendentsOperatorExpression source)))


;; 22.17. Is

;; 22.18. ToBoolean

;; 22.19. ToChars

;; 22.20. ToConcept

;; 22.21. ToDate
(defrecord ToDateOperatorExpression [operand]
  core/Expression
  (-eval [_ {:keys [now] :as context} resource scope]
    (p/to-date (core/-eval operand context resource scope) now)))


(defmethod core/compile* :elm.compiler.type/to-date
  [context {:keys [operand]}]
  (when-let [operand (core/compile* context operand)]
    (->ToDateOperatorExpression operand)))


;; 22.22. ToDateTime
(defrecord ToDateTimeOperatorExpression [operand]
  core/Expression
  (-eval [_ {:keys [now] :as context} resource scope]
    (p/to-date-time (core/-eval operand context resource scope) now)))


(defmethod core/compile* :elm.compiler.type/to-date-time
  [context {:keys [operand]}]
  (when-let [operand (core/compile* context operand)]
    (->ToDateTimeOperatorExpression operand)))


;; 22.23. ToDecimal
(defunop to-decimal [x]
  (p/to-decimal x))


;; 22.24. ToInteger
(defunop to-integer [x]
  (p/to-integer x))


;; 22.25. ToList
(defunop to-list [x]
  (if (nil? x) [] [x]))


;; 22.26. ToQuantity
(defunop to-quantity [x]
  (p/to-quantity x))


;; 22.28. ToString
(defunop to-string [x]
  (p/to-string x))
