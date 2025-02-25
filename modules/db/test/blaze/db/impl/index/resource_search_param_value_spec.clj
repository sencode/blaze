(ns blaze.db.impl.index.resource-search-param-value-spec
  (:require
    [blaze.byte-string :refer [byte-string?]]
    [blaze.byte-string-spec]
    [blaze.db.impl.index.resource-search-param-value :as r-sp-v]
    [blaze.db.kv.spec]
    [clojure.spec.alpha :as s]))


(s/fdef r-sp-v/next-value!
  :args (s/cat :iter :blaze.db/kv-iterator
               :resource-handle :blaze.db/resource-handle
               :c-hash :blaze.db/c-hash
               :value (s/? (s/cat :prefix-value byte-string?
                                  :value byte-string?)))
  :ret (s/nilable byte-string?))


(s/fdef r-sp-v/next-value-prev!
  :args (s/cat :iter :blaze.db/kv-iterator
               :resource-handle :blaze.db/resource-handle
               :c-hash :blaze.db/c-hash
               :prefix-value byte-string?
               :value byte-string?)
  :ret (s/nilable byte-string?))


(s/fdef r-sp-v/index-entry
  :args (s/cat :tid :blaze.db/tid
               :id :blaze.db/id-byte-string
               :hash :blaze.resource/hash
               :c-hash :blaze.db/c-hash
               :value byte-string?)
  :ret :blaze.db.kv/put-entry)


(s/fdef r-sp-v/index-entry-special
  :args (s/cat :tid :blaze.db/tid
               :id :blaze.db/id-byte-string
               :hash :blaze.resource/hash
               :c-hash :blaze.db/c-hash
               :value byte-string?)
  :ret :blaze.db.kv/put-entry)
