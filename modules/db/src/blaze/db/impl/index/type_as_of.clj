(ns blaze.db.impl.index.type-as-of
  "Functions for accessing the TypeAsOf index."
  (:require
    [blaze.byte-string :as bs]
    [blaze.coll.core :as coll]
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-handle :as rh]
    [blaze.db.impl.iterators :as i])
  (:import
    [clojure.lang IReduceInit]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(def ^:private ^:const ^long max-key-size
  (+ codec/tid-size codec/t-size codec/max-id-size))


(def ^:private ^:const ^long value-size
  (+ codec/hash-size codec/state-size))


(defn- key-valid? [^long tid ^long end-t]
  (fn [handle]
    (and (= ^long (:tid handle) tid) (< end-t ^long (:t handle)))))


(defn- decoder
  "Returns a function which decodes an resource handle out of a key and a value
  byte buffers from the TypeAsOf index.

  Closes over a shared byte array for id decoding, because the String
  constructor creates a copy of the id bytes anyway. Can only be used from one
  thread.

  The decode function creates only five objects, the resource handle, the String
  for the id, the byte array inside the String, the ByteString and the byte
  array inside the ByteString for the hash.

  Both byte buffers are changed during decoding and have to be reset accordingly
  after decoding."
  []
  (let [ib (byte-array codec/max-id-size)]
    (fn
      ([]
       [(bb/allocate-direct max-key-size)
        (bb/allocate-direct value-size)])
      ([kb vb]
       (let [tid (bb/get-int! kb)
             t (codec/descending-long (bb/get-long! kb))]
         (rh/resource-handle
           tid
           (let [id-size (bb/remaining kb)]
             (bb/copy-into-byte-array! kb ib 0 id-size)
             (codec/id ib 0 id-size))
           t vb))))))


(defn encode-key
  "Encodes the key of the TypeAsOf index from `tid`, `t` and `id`."
  [tid t id]
  (-> (bb/allocate (+ codec/tid-size codec/t-size (bs/size id)))
      (bb/put-int! tid)
      (bb/put-long! (codec/descending-long ^long t))
      (bb/put-byte-string! id)
      (bb/array)))


(defn- start-key [tid start-t start-id]
  (if start-id
    (encode-key tid start-t start-id)
    (-> (bb/allocate (+ codec/tid-size codec/t-size))
        (bb/put-int! tid)
        (bb/put-long! (codec/descending-long ^long start-t))
        (bb/array))))


(defn type-history
  "Returns a reducible collection of all versions between `start-t` (inclusive),
  `start-id` (optional, inclusive) and `end-t` (inclusive) of resources with
  `tid`.

  Versions are resource handles."
  ^IReduceInit
  [taoi tid start-t start-id end-t]
  (coll/eduction
    (take-while (key-valid? tid end-t))
    (i/kvs! taoi (decoder) (bs/from-byte-array (start-key tid start-t start-id)))))
