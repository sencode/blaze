(ns blaze.fhir.operation.evaluate-measure.measure-test
  (:require
    [blaze.bundle :as bundle]
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.fhir.operation.evaluate-measure.measure :refer [evaluate-measure]]
    [blaze.fhir.operation.evaluate-measure.measure-spec]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.fhir.spec.type :as type]
    [blaze.log]
    [blaze.luid :refer [luid]]
    [cheshire.core :as json]
    [cheshire.parse :refer [*use-bigdecimals?*]]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant OffsetDateTime ZoneOffset Year]
    [java.util Base64]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def now (OffsetDateTime/ofInstant Instant/EPOCH (ZoneOffset/ofHours 0)))


(def router
  (reitit/router
    [["/Patient/{id}" {:name :Patient/instance}]
     ["/MeasureReport/{id}/_history/{vid}" {:name :MeasureReport/versioned-instance}]]
    {:syntax :bracket}))


(defn- node-with [{:keys [entry]}]
  (mem-node-with [(bundle/tx-ops entry)]))


(defn- slurp-resource [name]
  (slurp (io/resource (str "blaze/fhir/operation/evaluate_measure/" name))))


(defn- b64-encode [s]
  (.encodeToString (Base64/getEncoder) (.getBytes s)))


(defn- library-entry [query]
  {:resource
   {:fhir/type :fhir/Library
    :id "0"
    :url #fhir/uri"0"
    :content
    [{:fhir/type :fhir/Attachment
      :contentType #fhir/code"text/cql"
      :data (type/->Base64Binary (b64-encode query))}]}
   :request
   {:method #fhir/code"PUT"
    :url #fhir/uri"Library/0"}})


(defn- parse-json [s]
  (binding [*use-bigdecimals?* true] (json/parse-string s keyword)))


(defn- read-data [name]
  (let [raw (slurp-resource (str name "-data.json"))
        bundle (fhir-spec/conform-json (parse-json raw))
        library (library-entry (slurp-resource (str name "-query.cql")))]
    (update bundle :entry conj library)))


(defn- evaluate
  ([name]
   (evaluate name "population"))
  ([name report-type]
   (with-open [node (node-with (read-data name))]
     (let [db (d/db node)
           period [(Year/of 2000) (Year/of 2020)]]
       (evaluate-measure now db router
                         @(d/pull node (d/resource-handle db "Measure" "0"))
                         {:period period :report-type report-type})))))


(defn- first-population [result]
  (if (::anom/category result)
    (prn result)
    (-> result
        :resource
        :group first
        :population first)))


(defn- first-stratifier-strata [result]
  (if (::anom/category result)
    (prn result)
    (-> result
        :resource
        :group first
        :stratifier first
        :stratum)))


(defn- new-ids []
  (atom (map str (range))))


(defn- take-from! [xs]
  #(let [x (first @xs)] (swap! xs rest) x))


(deftest integration-test
  (are [name count] (= count (:count (first-population (evaluate name))))
    "q1" 1
    "q2" 1
    "q3" 1
    "q4" 1
    "q5" 1
    "q6" 1
    "q7" 1
    "q8" 1
    "q9" 1
    "q10" 1
    "q11" 1
    "q12" 1
    "q13" 1
    "q14" 1
    "q15" 1
    "q16" 1
    "q17" 2
    "q18-specimen-bmi" 1
    "q24" 1
    "q28-relationship-procedure-condition" 1
    "q33-incompatible-quantities" 1)

  (with-redefs [luid (take-from! (new-ids))]
    (let [result (evaluate "q1" "subject-list")]
      (testing "MeasureReport is valid"
        (is (s/valid? :blaze/resource (:resource result))))

      (given (first-population result)
        :count := 1
        [:subjectResults :reference] := "List/0")

      (given (second (first (:tx-ops result)))
        :fhir/type := :fhir/List
        :id := "0"
        [:entry 0 :item :reference] := "Patient/0")))

  (let [result (evaluate "q19-stratifier-ageclass")]
    (testing "MeasureReport is valid"
      (is (s/valid? :blaze/resource (:resource result))))

    (testing "MeasureReport type is `summary`"
      (is (= #fhir/code"summary" (-> result :resource :type))))

    (given (first-stratifier-strata result)
      [0 :value :text] := "10"
      [0 :population 0 :count] := 1
      [1 :value :text] := "70"
      [1 :population 0 :count] := 2))

  (with-redefs [luid (take-from! (new-ids))]
    (let [result (evaluate "q19-stratifier-ageclass" "subject-list")]
      (testing "MeasureReport is valid"
        (is (s/valid? :blaze/resource (:resource result))))

      (testing "MeasureReport type is `subject-list`"
        (is (= #fhir/code"subject-list" (-> result :resource :type))))

      (given (first-stratifier-strata result)
        [0 :value :text] := "10"
        [0 :population 0 :count] := 1
        [0 :population 0 :subjectResults :reference] := "List/1"
        [1 :value :text] := "70"
        [1 :population 0 :count] := 2
        [1 :population 0 :subjectResults :reference] := "List/2")

      (given (:tx-ops result)
        [1 1 :fhir/type] := :fhir/List
        [1 1 :id] := "1"
        [1 1 :status] := #fhir/code"current"
        [1 1 :mode] := #fhir/code"working"
        [1 1 :entry 0 :item :reference] := "Patient/0"
        [2 1 :fhir/type] := :fhir/List
        [2 1 :id] := "2"
        [2 1 :entry 0 :item :reference] := "Patient/1"
        [2 1 :entry 1 :item :reference] := "Patient/2")))

  (given (first-stratifier-strata (evaluate "q20-stratifier-city"))
    [0 :value :text] := "Jena"
    [0 :population 0 :count] := 3
    [1 :value :text] := "Leipzig"
    [1 :population 0 :count] := 1)

  (given (first-stratifier-strata (evaluate "q21-stratifier-city-of-only-women"))
    [0 :value :text] := "Jena"
    [0 :population 0 :count] := 2)

  (is (= ::anom/incorrect (::anom/category (evaluate "q22-stratifier-multiple-cities-fail"))))

  (given (first-stratifier-strata (evaluate "q23-stratifier-ageclass-and-gender"))
    [0 :component 0 :code :text] := "age-class"
    [0 :component 0 :value :text] := "10"
    [0 :component 1 :code :text] := "gender"
    [0 :component 1 :value :text] := "male"
    [0 :population 0 :count] := 1
    [1 :component 0 :value :text] := "70"
    [1 :component 1 :value :text] := "female"
    [1 :population 0 :count] := 2
    [2 :component 0 :value :text] := "70"
    [2 :component 1 :value :text] := "male"
    [2 :population 0 :count] := 1)

  (with-redefs [luid (take-from! (new-ids))]
    (let [result (evaluate "q23-stratifier-ageclass-and-gender" "subject-list")]
      (testing "MeasureReport is valid"
        (is (s/valid? :blaze/resource (:resource result))))

      (given (first-stratifier-strata result)
        [0 :component 0 :code :text] := "age-class"
        [0 :component 0 :value :text] := "10"
        [0 :component 1 :code :text] := "gender"
        [0 :component 1 :value :text] := "male"
        [0 :population 0 :count] := 1
        [0 :population 0 :subjectResults :reference] := "List/1"
        [1 :component 0 :value :text] := "70"
        [1 :component 1 :value :text] := "female"
        [1 :population 0 :count] := 2
        [1 :population 0 :subjectResults :reference] := "List/3"
        [2 :component 0 :value :text] := "70"
        [2 :component 1 :value :text] := "male"
        [2 :population 0 :count] := 1
        [2 :population 0 :subjectResults :reference] := "List/2")

      (given (:tx-ops result)
        [1 1 :fhir/type] := :fhir/List
        [1 1 :id] := "1"
        [1 1 :entry 0 :item :reference] := "Patient/0"
        [2 1 :fhir/type] := :fhir/List
        [2 1 :id] := "2"
        [2 1 :entry 0 :item :reference] := "Patient/1"
        [3 1 :fhir/type] := :fhir/List
        [3 1 :id] := "3"
        [3 1 :entry 0 :item :reference] := "Patient/2"
        [3 1 :entry 1 :item :reference] := "Patient/3")))

  (given (first-stratifier-strata (evaluate "q25-stratifier-collection"))
    [0 :value :text] := "Organization/collection-0"
    [0 :population 0 :count] := 1
    [1 :value :text] := "Organization/collection-1"
    [1 :population 0 :count] := 1)

  (given (first-stratifier-strata (evaluate "q26-stratifier-bmi"))
    [0 :value :text] := "37"
    [0 :population 0 :count] := 1
    [1 :value :text] := "null"
    [1 :population 0 :count] := 2)

  (given (first-stratifier-strata (evaluate "q27-stratifier-calculated-bmi"))
    [0 :value :text] := "26.8"
    [0 :population 0 :count] := 1
    [1 :value :text] := "null"
    [1 :population 0 :count] := 2)

  (given (first-stratifier-strata (evaluate "q29-stratifier-sample-material-type"))
    [0 :value :text] := "liquid"
    [0 :population 0 :count] := 1
    [1 :value :text] := "tissue"
    [1 :population 0 :count] := 1)

  (given (first-stratifier-strata (evaluate "q30-stratifier-with-missing-expression"))
    [0 :value :text] := "null"
    [0 :population 0 :count] := 2)

  (given (first-stratifier-strata (evaluate "q31-stratifier-storage-temperature"))
    [0 :value :text] := "temperature2to10"
    [0 :population 0 :count] := 1
    [1 :value :text] := "temperatureGN"
    [1 :population 0 :count] := 1)

  (given (first-stratifier-strata (evaluate "q32-stratifier-underweight"))
    [0 :value :text] := "false"
    [0 :population 0 :count] := 2
    [1 :value :text] := "true"
    [1 :population 0 :count] := 1))

(comment
  (log/set-level! :trace)
  (evaluate "q33-incompatible-quantities")
  )
