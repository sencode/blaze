(ns blaze.db.impl.protocols)


(defprotocol Node
  (-db [node])

  (-sync [node t])

  (-submit-tx [node tx-ops])

  (-tx-result [node t]))


(defprotocol Db
  (-node [db])

  (-as-of [db t])

  (-basis-t [db])

  (-as-of-t [db])

  (-resource-handle [db tid id])

  (-type-list [db tid] [db tid start-id])

  (-type-total [db tid])

  (-system-list [_] [_ start-tid start-id])

  (-system-total [db])

  (-compartment-resource-handles
    [db compartment tid]
    [db compartment tid start-id])

  (-execute-query [db query] [db query arg1])

  (-instance-history [db tid id start-t since])

  (-total-num-of-instance-changes [_ tid id since])

  (-type-history [db type start-t start-id since])

  (-total-num-of-type-changes [db type since])

  (-system-history [db start-t start-tid start-id since])

  (-total-num-of-system-changes [db since])

  (-new-batch-db [db]))


(defprotocol Tx
  (-tx [tx t]))


(defprotocol QueryCompiler
  (-compile-type-query [compiler type clauses])

  (-compile-type-query-lenient [compiler type clauses])

  (-compile-system-query [compiler clauses])

  (-compile-compartment-query [compiler code type clauses])

  (-compile-compartment-query-lenient [compiler code type clauses]))


(defprotocol Query
  (-execute [query context] [query context arg1])

  (-clauses [query]))


(defprotocol SearchParam
  (-compile-value [search-param modifier value] "Can return an anomaly.")
  (-resource-handles
    [search-param context tid modifier compiled-value]
    [search-param context tid modifier compiled-value start-id])
  (-compartment-keys [search-param context compartment tid compiled-value])
  (-matches? [search-param context resource-handle modifier compiled-values])
  (-compartment-ids [_ resolver resource])
  ;; TODO: remove if not needed anymore for the data search param
  (-index-entries [_ resolver hash resource linked-compartments])
  (-index-values [_ resolver resource])
  (-compile-index-values [_ values]))


(defprotocol Pull
  (-pull [pull resource-handle])

  (-pull-content [pull resource-handle]))
