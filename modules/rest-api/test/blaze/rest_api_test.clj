(ns blaze.rest-api-test
  (:require
    [blaze.db.impl.search-param]
    [blaze.db.search-param-registry :as sr]
    [blaze.handler.util :as handler-util]
    [blaze.rest-api :as rest-api]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest testing]]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [reitit.ring]
    [taoensso.timbre :as log]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def search-param-registry (sr/init-search-param-registry))


(def config
  #:blaze.rest-api
      {})


(defn handler [key]
  (fn [_] key))


(defn router [auth-backends]
  (rest-api/router
    {:base-url "base-url-111523"
     :structure-definitions
     [{:kind "resource" :name "Patient"}
      {:kind "resource" :name "Measure"}]
     :auth-backends auth-backends
     :search-system-handler (handler ::search-system)
     :transaction-handler (handler ::transaction)
     :history-system-handler (handler ::history-system)
     :resource-patterns
     [#:blaze.rest-api.resource-pattern
         {:type :default
          :interactions
          {:read
           #:blaze.rest-api.interaction
               {:handler (handler ::read)}
           :vread
           #:blaze.rest-api.interaction
               {:handler (handler ::vread)}
           :update
           #:blaze.rest-api.interaction
               {:handler (handler ::update)}
           :delete
           #:blaze.rest-api.interaction
               {:handler (handler ::delete)}
           :history-instance
           #:blaze.rest-api.interaction
               {:handler (handler ::history-instance)}
           :history-type
           #:blaze.rest-api.interaction
               {:handler (handler ::history-type)}
           :create
           #:blaze.rest-api.interaction
               {:handler (handler ::create)}
           :search-type
           #:blaze.rest-api.interaction
               {:handler (handler ::search-type)}}}]
     :compartments
     [#:blaze.rest-api.compartment
         {:code "Patient"
          :search-handler (handler ::search-patient-compartment)}]
     :operations
     [#:blaze.rest-api.operation
         {:code "compact-db"
          :system-handler (handler ::compact-db)}
      #:blaze.rest-api.operation
         {:code "evaluate-measure"
          :resource-types ["Measure"]
          :type-handler (handler ::evaluate-measure-type)
          :instance-handler (handler ::evaluate-measure-instance)}]}
    (fn [_])))


(def minimal-router
  (rest-api/router
    {:base-url "base-url-111523"
     :structure-definitions [{:kind "resource" :name "Patient"}]
     :resource-patterns
     [#:blaze.rest-api.resource-pattern
         {:type :default
          :interactions
          {:read
           #:blaze.rest-api.interaction
               {:handler (handler ::read)}}}]}
    (fn [_])))


(deftest router-test
  (testing "handlers"
    (are [path request-method handler]
      (= handler
         ((get-in
            (reitit/match-by-path (router []) path)
            [:result request-method :data :handler])
          {}))
      "" :get ::search-system
      "" :post ::transaction
      "/_history" :get ::history-system
      "/Patient" :get ::search-type
      "/Patient" :post ::create
      "/Patient/_history" :get ::history-type
      "/Patient/_search" :post ::search-type
      "/Patient/0" :get ::read
      "/Patient/0" :put ::update
      "/Patient/0" :delete ::delete
      "/Patient/0/_history" :get ::history-instance
      "/Patient/0/_history/42" :get ::vread
      "/Patient/0/Condition" :get ::search-patient-compartment
      "/Patient/0/Observation" :get ::search-patient-compartment
      "/$compact-db" :get ::compact-db
      "/$compact-db" :post ::compact-db
      "/Measure/$evaluate-measure" :get ::evaluate-measure-type
      "/Measure/$evaluate-measure" :post ::evaluate-measure-type
      "/Measure/0/$evaluate-measure" :get ::evaluate-measure-instance
      "/Measure/0/$evaluate-measure" :post ::evaluate-measure-instance
      "/Measure/0" :get ::read)

    (testing "of minimal router"
      (are [path request-method handler]
        (= handler
           ((get-in
              (reitit/match-by-path minimal-router path)
              [:result request-method :data :handler])
            {}))
        "/Patient/0" :get ::read)))

  (testing "middleware"
    (are [path request-method middleware]
      (= middleware
         (->> (get-in
                (reitit/match-by-path (router []) path)
                [:result request-method :data :middleware])
              (mapv (comp :name #(if (sequential? %) (first %) %)))))
      "" :get []
      "" :post [:resource :wrap-batch-handler]
      "/_history" :get []
      "/Patient" :get []
      "/Patient" :post [:resource]
      "/Patient/_history" :get []
      "/Patient/_search" :post []
      "/Patient/0" :get []
      "/Patient/0" :put [:resource]
      "/Patient/0" :delete []
      "/Patient/0/_history" :get []
      "/Patient/0/_history/42" :get []
      "/Patient/0/Condition" :get []
      "/Patient/0/Observation" :get []
      "/$compact-db" :get []
      "/$compact-db" :post []
      "/Measure/$evaluate-measure" :get []
      "/Measure/$evaluate-measure" :post []
      "/Measure/0/$evaluate-measure" :get []
      "/Measure/0/$evaluate-measure" :post []
      "/Measure/0" :get [])

    (testing "with auth backends"
      (are [path request-method middleware]
        (= middleware
           (->> (get-in
                  (reitit/match-by-path (router [:auth-backend]) path)
                  [:result request-method :data :middleware])
                (mapv (comp :name #(if (sequential? %) (first %) %)))))
        "" :get [:auth-guard]
        "" :post [:auth-guard :resource :wrap-batch-handler]
        "/$compact-db" :get [:auth-guard]
        "/$compact-db" :post [:auth-guard])))

  (testing "Patient instance POST is not allowed"
    (given @((reitit.ring/ring-handler (router []) handler-util/default-handler)
             {:uri "/Patient/0" :request-method :post})
      :status := 405
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"processing"
      [:body :issue 0 :diagnostics] := "Method POST not allowed on `/Patient/0` endpoint."))

  (testing "Patient type PUT is not allowed"
    (given @((reitit.ring/ring-handler (router []) handler-util/default-handler)
             {:uri "/Patient" :request-method :put})
      :status := 405
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"processing"
      [:body :issue 0 :diagnostics] := "Method PUT not allowed on `/Patient` endpoint."))

  (testing "Observations are not found"
    (given @((reitit.ring/ring-handler (router []) handler-util/default-handler)
             {:uri "/Observation" :request-method :get})
      :status := 404
      [:body :fhir/type] := :fhir/OperationOutcome
      [:body :issue 0 :severity] := #fhir/code"error"
      [:body :issue 0 :code] := #fhir/code"not-found")))


(deftest router-match-by-name-test
  (are [name params path]
    (= (reitit/match->path (reitit/match-by-name (router []) name params)) path)

    :Patient/type
    {}
    "/Patient"

    :Patient/instance
    {:id "23"}
    "/Patient/23"

    :Patient/versioned-instance
    {:id "23" :vid "42"}
    "/Patient/23/_history/42"))


(def ^:private copyright
  #fhir/markdown"Copyright 2019 The Samply Community\n\nLicensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\n\nhttp://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.")


(defn- search-param [name]
  (fn [params] (some #(when (= name (:name %)) %) params)))


(deftest capabilities-handler-test
  (testing "minimal config"
    (given
      (-> @((rest-api/capabilities-handler
              {:base-url "base-url-131713"
               :version "version-131640"
               :structure-definitions
               [{:kind "resource" :name "Patient"}]
               :search-param-registry search-param-registry})
            {})
          :body)
      :fhir/type := :fhir/CapabilityStatement
      :status := #fhir/code"active"
      :experimental := false
      :publisher := "The Samply Community"
      :copyright := copyright
      :kind := #fhir/code"instance"
      [:software :name] := "Blaze"
      [:software :version] := "version-131640"
      [:implementation :url] := #fhir/url"base-url-131713"
      :fhirVersion := #fhir/code"4.0.1"
      :format := [#fhir/code"application/fhir+json"
                  #fhir/code"application/xml+json"]))

  (testing "minimal config + search-system"
    (given
      (-> @((rest-api/capabilities-handler
              {:base-url "base-url-131713"
               :version "version-131640"
               :structure-definitions
               [{:kind "resource" :name "Patient"}]
               :search-param-registry search-param-registry
               :search-system-handler ::search-system})
            {})
          :body)
      :fhir/type := :fhir/CapabilityStatement
      [:rest 0 :interaction 0 :code] := #fhir/code"search-system"))

  (testing "minimal config + history-system"
    (given
      (-> @((rest-api/capabilities-handler
              {:base-url "base-url-131713"
               :version "version-131640"
               :structure-definitions
               [{:kind "resource" :name "Patient"}]
               :search-param-registry search-param-registry
               :history-system-handler ::history-system})
            {})
          :body)
      :fhir/type := :fhir/CapabilityStatement
      [:rest 0 :interaction 0 :code] := #fhir/code"history-system"))

  (testing "Patient interaction"
    (given
      (-> @((rest-api/capabilities-handler
              {:base-url "base-url-131713"
               :version "version-131640"
               :structure-definitions
               [{:kind "resource" :name "Patient"}]
               :search-param-registry search-param-registry
               :resource-patterns
               [#:blaze.rest-api.resource-pattern
                   {:type "Patient"
                    :interactions
                    {:read
                     #:blaze.rest-api.interaction
                         {:handler (fn [_])}}}]})
            {})
          :body)
      :fhir/type := :fhir/CapabilityStatement
      [:rest 0 :resource 0 :type] := #fhir/code"Patient"
      [:rest 0 :resource 0 :interaction 0 :code] := #fhir/code"read"))

  (testing "Observation interaction"
    (given
      (-> @((rest-api/capabilities-handler
              {:base-url "base-url-131713"
               :version "version-131640"
               :structure-definitions
               [{:kind "resource" :name "Observation"}]
               :search-param-registry search-param-registry
               :resource-patterns
               [#:blaze.rest-api.resource-pattern
                   {:type "Observation"
                    :interactions
                    {:read
                     #:blaze.rest-api.interaction
                         {:handler (fn [_])}}}]})
            {})
          :body)
      :fhir/type := :fhir/CapabilityStatement
      [:rest 0 :resource 0 :type] := #fhir/code"Observation"
      [:rest 0 :resource 0 :interaction 0 :code] := #fhir/code"read"
      [:rest 0 :resource 0 :searchParam (search-param "value-quantity") :type]
      := #fhir/code"quantity"
      [:rest 0 :resource 0 :searchParam (search-param "value-quantity") :documentation]
      := #fhir/markdown"Decimal values are truncated at two digits after the decimal point."))

  (testing "one operation"
    (given
      (-> @((rest-api/capabilities-handler
              {:base-url "base-url-131713"
               :version "version-131640"
               :structure-definitions
               [{:kind "resource" :name "Measure"}]
               :search-param-registry search-param-registry
               :resource-patterns
               [#:blaze.rest-api.resource-pattern
                   {:type "Measure"
                    :interactions
                    {:read
                     #:blaze.rest-api.interaction
                         {:handler (fn [_])}}}]
               :operations
               [#:blaze.rest-api.operation
                   {:code "evaluate-measure"
                    :def-uri
                    "http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure"
                    :resource-types ["Measure"]
                    :type-handler (fn [_])
                    :instance-handler (fn [_])}]})
            {})
          :body)
      :fhir/type := :fhir/CapabilityStatement
      [:rest 0 :resource 0 :type] := #fhir/code"Measure"
      [:rest 0 :resource 0 :operation 0 :name] := "evaluate-measure"
      [:rest 0 :resource 0 :operation 0 :definition] :=
      #fhir/canonical"http://hl7.org/fhir/OperationDefinition/Measure-evaluate-measure")))
