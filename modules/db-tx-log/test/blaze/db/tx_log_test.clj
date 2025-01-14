(ns blaze.db.tx-log-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.tx-log :as tx-log]
    [blaze.fhir.hash :as hash]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [is deftest]]
    [java-time :as jt])
  (:import
    [java.time Instant]
    [java.io Closeable]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def patient-hash-0 (hash/generate {:fhir/type :fhir/Patient :id "0"}))


(deftest submit-test
  (let [tx-log (reify tx-log/TxLog
                 (-submit [_ _]
                   (ac/completed-future 1)))]
    (is (= 1 @(tx-log/submit
                tx-log
                [{:op "create"
                  :type "Patient"
                  :id "0"
                  :hash patient-hash-0}])))))


(def tx-data {:t 1
              :instant Instant/EPOCH
              :tx-cmds
              [{:op "create"
                :type "Patient"
                :id "0"
                :hash patient-hash-0}]})


(deftest new-queue-test
  (let [tx-log (reify tx-log/TxLog
                 (-new-queue [_ _]
                   (reify
                     tx-log/Queue
                     (-poll [_ _]
                       [tx-data])
                     Closeable
                     (close [_]))))]
    (with-open [queue (tx-log/new-queue tx-log 1)]
      (is (= [tx-data] (tx-log/poll queue (jt/millis 100)))))))
