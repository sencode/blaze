(ns blaze.interaction.delete-test
  "Specifications relevant for the FHIR update interaction:

  https://www.hl7.org/fhir/http.html#delete"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.executors :as ex]
    [blaze.interaction.delete]
    [blaze.interaction.delete-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def executor (ex/single-thread-executor))


(defn- handler [node]
  (-> (ig/init
        {:blaze.interaction/delete
         {:node node
          :executor executor}})
      (:blaze.interaction/delete)))


(defn- handler-with [txs]
  (fn [request]
    (with-open [node (mem-node-with txs)]
      @((handler node) request))))


(deftest handler-test
  (testing "Returns Not Found on non-existing resource"
    (let [{:keys [status body]}
          ((handler-with [])
           {:path-params {:id "0"}
            ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

      (is (= 404 status))

      (given body
        :fhir/type := :fhir/OperationOutcome
        [:issue 0 :severity] := #fhir/code"error"
        [:issue 0 :code] := #fhir/code"not-found")))


  (testing "Returns No Content on successful deletion"
    (let [{:keys [status headers body]}
          ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]]])
           {:path-params {:id "0"}
            ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

      (is (= 204 status))

      (testing "Transaction time in Last-Modified header"
        (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

      (testing "Version in ETag header"
        ;; 2 is the T of the transaction of the resource update
        (is (= "W/\"2\"" (get headers "ETag"))))

      (is (nil? body))))


  (testing "Returns No Content on already deleted resource"
    (let [{:keys [status headers body]}
          ((handler-with
             [[[:put {:fhir/type :fhir/Patient :id "0"}]]
              [[:delete "Patient" "0"]]])
           {:path-params {:id "0"}
            ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

      (is (= 204 status))

      (testing "Transaction time in Last-Modified header"
        (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

      (testing "Version in ETag header"
        ;; 2 is the T of the transaction of the resource update
        (is (= "W/\"2\"" (get headers "ETag"))))

      (is (nil? body)))))
