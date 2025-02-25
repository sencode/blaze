(ns blaze.db.api-spec
  (:require
    [blaze.async.comp :as ac]
    [blaze.async.comp-spec]
    [blaze.db.api :as d]
    [blaze.db.search-param-registry-spec]
    [blaze.db.spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/fdef d/db
  :args (s/cat :node :blaze.db/node)
  :ret :blaze.db/db)


(s/fdef d/sync
  :args (s/cat :node :blaze.db/node :t :blaze.db/t)
  :ret ac/completable-future?)


(s/fdef d/transact
  :args (s/cat :node :blaze.db/node :tx-ops :blaze.db/tx-ops)
  :ret ac/completable-future?)


(s/fdef d/node
  :args (s/cat :db :blaze.db/db)
  :ret :blaze.db/node)


(s/fdef d/tx
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :t :blaze.db/t)
  :ret (s/nilable :blaze.db/tx))


(s/fdef d/resource-handle
  :args (s/cat :db :blaze.db/db :type :fhir.type/name :id :blaze.resource/id)
  :ret (s/nilable :blaze.db/resource-handle))


(s/fdef d/resource-handle?
  :args (s/cat :x any?)
  :ret boolean?)



;; ---- Type-Level Functions --------------------------------------------------

(s/fdef d/type-list
  :args (s/cat :db :blaze.db/db :type :fhir.type/name
               :start-id (s/? :blaze.resource/id))
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef d/type-total
  :args (s/cat :db :blaze.db/db :type :fhir.type/name)
  :ret nat-int?)


(s/fdef d/type-query
  :args (s/cat :db :blaze.db/db :type :fhir.type/name
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1)
               :start-id (s/? :blaze.resource/id))
  :ret (s/or :result (s/coll-of :blaze.db/resource-handle :kind sequential?)
             :anomaly ::anom/anomaly))


(s/fdef d/compile-type-query
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :type :fhir.type/name
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1))
  :ret (s/or :query :blaze.db/query :anomaly ::anom/anomaly))


(s/fdef d/compile-type-query-lenient
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :type :fhir.type/name
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1))
  :ret :blaze.db/query)



;; ---- System-Level Functions ------------------------------------------------

(s/fdef d/system-list
  :args (s/cat
          :db :blaze.db/db
          :start (s/? (s/cat :start-type :fhir.type/name
                             :start-id :blaze.resource/id)))
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef d/system-total
  :args (s/cat :db :blaze.db/db)
  :ret nat-int?)


(s/fdef d/system-query
  :args (s/cat :db :blaze.db/db
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1))
  :ret (s/or :result (s/coll-of :blaze.db/resource-handle :kind sequential?)
             :anomaly ::anom/anomaly))


(s/fdef d/compile-system-query
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1))
  :ret (s/or :query :blaze.db/query :anomaly ::anom/anomaly))



;; ---- Compartment-Level Functions -------------------------------------------

(s/fdef d/list-compartment-resource-handles
  :args (s/cat :db :blaze.db/db
               :code string?
               :id :blaze.resource/id
               :type :fhir.type/name
               :start-id (s/? :blaze.resource/id))
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef d/compartment-query
  :args (s/cat :db :blaze.db/db
               :code string?
               :id :blaze.resource/id
               :type :fhir.type/name
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1))
  :ret (s/or :result (s/coll-of :blaze.db/resource-handle :kind sequential?)
             :anomaly ::anom/anomaly))


(s/fdef d/compile-compartment-query
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :code string?
               :type :fhir.type/name
               :clauses (s/coll-of :blaze.db.query/clause :min-count 1))
  :ret (s/or :query :blaze.db/query :anomaly ::anom/anomaly))



;; ---- Common Query Functions ------------------------------------------------

(s/fdef d/execute-query
  :args (s/cat :db :blaze.db/db :query :blaze.db/query :args (s/* any?))
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef d/query-clauses
  :args (s/cat :query :blaze.db/query)
  :ret (s/coll-of :blaze.db.query/clause))



;; ---- Instance-Level History Functions --------------------------------------

(s/fdef d/instance-history
  :args (s/cat :db :blaze.db/db
               :type :fhir.type/name
               :id :blaze.resource/id
               :start-t (s/? (s/nilable :blaze.db/t))
               :since (s/? (s/nilable inst?)))
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef d/total-num-of-instance-changes
  :args (s/cat :db :blaze.db/db
               :type :fhir.type/name
               :id :blaze.resource/id
               :since (s/? (s/nilable inst?)))
  :ret nat-int?)



;; ---- Type-Level History Functions ------------------------------------------

(s/fdef d/type-history
  :args (s/cat :db :blaze.db/db
               :type :fhir.type/name
               :start-t (s/? (s/nilable :blaze.db/t))
               :start-id (s/? (s/nilable :blaze.resource/id))
               :since (s/? (s/nilable inst?)))
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef d/total-num-of-type-changes
  :args (s/cat :db :blaze.db/db
               :type :fhir.type/name
               :since (s/? (s/nilable inst?)))
  :ret nat-int?)



;; ---- System-Level History Functions ----------------------------------------

(s/fdef d/system-history
  :args (s/cat
          :db :blaze.db/db
          :more
          (s/? (s/cat
                 :start-t (s/nilable :blaze.db/t)
                 :more
                 (s/? (s/cat
                        :start-type (s/nilable :fhir.type/name)
                        :more
                        (s/? (s/cat
                               :start-id (s/nilable :blaze.resource/id)
                               :since (s/? (s/nilable inst?)))))))))
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef d/total-num-of-system-changes
  :args (s/cat :db :blaze.db/db :since (s/? (s/nilable inst?)))
  :ret nat-int?)



;; ---- Batch DB --------------------------------------------------------------

(s/fdef d/new-batch-db
  :args (s/cat :db :blaze.db/db)
  :ret :blaze.db/db)



;; ---- Pull ------------------------------------------------------------------

(s/fdef d/pull
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :resource-handle :blaze.db/resource-handle)
  :ret ac/completable-future?)


(s/fdef d/pull-content
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :resource-handle :blaze.db/resource-handle)
  :ret ac/completable-future?)


(s/fdef d/pull-many
  :args (s/cat :node-or-db (s/or :node :blaze.db/node :db :blaze.db/db)
               :resource-handles (s/coll-of :blaze.db/resource-handle
                                            :kind sequential?))
  :ret ac/completable-future?)
