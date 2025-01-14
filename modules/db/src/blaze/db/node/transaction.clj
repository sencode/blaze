(ns blaze.db.node.transaction
  (:require
    [blaze.db.impl.codec :as codec]
    [blaze.db.tx-log.local.references :as references]
    [blaze.fhir.hash :as hash]
    [blaze.fhir.spec :as fhir-spec]))


(defmulti prepare-op first)


(defmethod prepare-op :create
  [[op resource]]
  (let [hash (hash/generate resource)
        refs (references/extract-references resource)]
    {:hash-resource
     [hash resource]
     :blaze.db/tx-cmd
     (cond->
       {:op (name op)
        :type (name (fhir-spec/fhir-type resource))
        :id (:id resource)
        :hash hash}
       (seq refs)
       (assoc :refs refs))}))


(defmethod prepare-op :put
  [[op resource matches]]
  (let [hash (hash/generate resource)
        refs (references/extract-references resource)]
    {:hash-resource
     [hash resource]
     :blaze.db/tx-cmd
     (cond->
       {:op (name op)
        :type (name (fhir-spec/fhir-type resource))
        :id (:id resource)
        :hash hash}
       (seq refs)
       (assoc :refs refs)
       matches
       (assoc :if-match matches))}))


(defmethod prepare-op :delete
  [[_ type id]]
  (let [resource (codec/deleted-resource type id)
        hash (hash/generate resource)]
    {:hash-resource
     [hash resource]
     :blaze.db/tx-cmd
     {:op "delete"
      :type (name (fhir-spec/fhir-type resource))
      :id (:id resource)
      :hash hash}}))


(def ^:private split
  (juxt #(mapv :blaze.db/tx-cmd %)
        #(into {} (map :hash-resource) %)))


(defn prepare-ops
  "Splits each transaction operator into a collection of :blaze.db/tx-cmd and a
  map of hash to resource."
  [tx-ops]
  (split (mapv prepare-op tx-ops)))
