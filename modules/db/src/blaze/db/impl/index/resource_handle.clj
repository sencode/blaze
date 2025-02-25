(ns blaze.db.impl.index.resource-handle
  (:require
    [blaze.byte-string :as bs]
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.fhir.spec.type :as type]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defrecord ResourceHandle [^int tid id ^long t hash ^long num-changes op]
  type/FhirType
  (-type [_]
    ;; TODO: maybe cache this
    (keyword "fhir" (codec/tid->type tid))))


(defn- state->num-changes
  "A resource is new if num-changes is 1."
  [state]
  (bit-shift-right ^long state 8))


(defn- state->op [state]
  (cond
    (bit-test ^long state 1) :create
    (bit-test ^long state 0) :delete
    :else :put))


(defn resource-handle
  "Creates a new resource handle.

  The type of that handle will be the keyword `:fhir/<resource-type>`."
  [tid id t value-buffer]
  (let [hash (bs/from-byte-buffer value-buffer codec/hash-size)
        state (bb/get-long! value-buffer)]
    (ResourceHandle.
      tid
      id
      t
      hash
      (state->num-changes state)
      (state->op state))))


(defn resource-handle? [x]
  (instance? ResourceHandle x))
