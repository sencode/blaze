(ns blaze.db.impl.index.tx-success
  "Functions for accessing the TxSuccess index.

  The TxSuccess index contains the real point in time, as java.time.Instant,
  successful transactions happened. In other words, this index maps each t which
  is just a monotonically increasing number to a real point in time."
  (:require
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.kv :as kv]
    [cheshire.core :as cheshire])
  (:import
    [java.time Instant]))


(defn- decode-tx [bytes t]
  (let [{:keys [inst]} (cheshire/parse-cbor bytes keyword)]
    {:blaze.db/t t
     :blaze.db.tx/instant (Instant/ofEpochMilli inst)}))


(defn- encode-key [t]
  (-> (bb/allocate Long/BYTES)
      (bb/put-long! t)
      (bb/array)))


(defn tx
  "Returns the transaction with `t` using `kv-store` or nil of none was found.

  Errored transactions are returned by `blaze.db.impl.index.tx-error/tx-error`."
  [kv-store t]
  (some-> (kv/get kv-store :tx-success-index (encode-key t))
          (decode-tx t)))


(defn last-t
  "Returns the last known `t` or nil if the store is empty."
  [kv-store]
  (with-open [snapshot (kv/new-snapshot kv-store)
              iter (kv/new-iterator snapshot :tx-success-index)]
    (kv/seek-to-first! iter)
    (when (kv/valid? iter)
      (let [buf (bb/allocate-direct Long/BYTES)]
        (kv/key! iter buf)
        (bb/get-long! buf)))))


(defn- encode-tx
  "A map is encoded in CBOR format to be able to add additional data later."
  [instant]
  (cheshire/generate-cbor {:inst (inst-ms instant)}))


(defn index-entry [t instant]
  [:tx-success-index (encode-key t) (encode-tx instant)])
