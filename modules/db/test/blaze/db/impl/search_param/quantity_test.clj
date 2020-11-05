(ns blaze.db.impl.search-param.quantity-test
  (:require
    [blaze.db.bytes :as bytes]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param-spec]
    [blaze.db.search-param-registry :as sr]
    [blaze.fhir.hash :as hash]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [taoensso.timbre :as log]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def search-param-registry (sr/init-search-param-registry))


(def value-quantity-param
  (sr/get search-param-registry "value-quantity" "Observation"))


(deftest code-test
  (is (= "value-quantity" (:code value-quantity-param))))


(defn compile-quantity-value [value]
  (let [[[op lower-bound exact-value upper-bound]]
        (search-param/compile-values value-quantity-param [value])]
    [op (codec/hex lower-bound) (codec/hex exact-value) (codec/hex upper-bound)]))


(defn hex-quantity [unit value]
  (codec/hex (codec/quantity unit value)))


(deftest compile-value-test
  (are [value op lower-bound exact-value upper-bound]
    (= [op lower-bound exact-value upper-bound] (compile-quantity-value value))

    "23.4" :eq (hex-quantity nil 23.35M) (hex-quantity nil 23.40M) (hex-quantity nil 23.45M)
    "23.0|kg/m2" :eq (hex-quantity "kg/m2" 22.95M) (hex-quantity "kg/m2" 23.00M) (hex-quantity "kg/m2" 23.05M)
    "ge23" :ge (hex-quantity nil 22.5M) (hex-quantity nil 23.00M) (hex-quantity nil 23.5M)
    "0.1" :eq (hex-quantity nil 0.05M) (hex-quantity nil 0.10M) (hex-quantity nil 0.15M)
    "0" :eq (hex-quantity nil -0.5M) (hex-quantity nil 0.00M) (hex-quantity nil 0.5M)
    "0.0" :eq (hex-quantity nil -0.05M) (hex-quantity nil 0.00M) (hex-quantity nil 0.05M)))


(deftest index-entries-test
  (testing "Observation value-quantity"
    (testing "with value, system and code"
      (let [observation
            {:fhir/type :fhir/Observation
             :id "id-155558"
             :status #fhir/code"final"
             :value
             {:fhir/type :fhir/Quantity
              :value 140M
              :code #fhir/code"mm[Hg]"
              :system #fhir/uri"http://unitsofmeasure.org"}}
            hash (hash/generate observation)
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
            (search-param/index-entries
              (sr/get search-param-registry "value-quantity" "Observation")
              hash observation [])]

        (testing "first search-param-value-key is about `value`"
          (is (bytes/=
                k0
                (codec/sp-value-resource-key
                  (codec/c-hash "value-quantity")
                  (codec/tid "Observation")
                  (codec/quantity nil 140M)
                  (codec/id-bytes "id-155558")
                  hash))))

        (testing "first resource-value-key is about `value`"
          (is (bytes/=
                k1
                (codec/resource-sp-value-key
                  (codec/tid "Observation")
                  (codec/id-bytes "id-155558")
                  hash
                  (codec/c-hash "value-quantity")
                  (codec/quantity nil 140M)))))

        (testing "second search-param-value-key is about `code value`"
          (is (bytes/=
                k2
                (codec/sp-value-resource-key
                  (codec/c-hash "value-quantity")
                  (codec/tid "Observation")
                  (codec/quantity "mm[Hg]" 140M)
                  (codec/id-bytes "id-155558")
                  hash))))

        (testing "second resource-value-key is about `code value`"
          (is (bytes/=
                k3
                (codec/resource-sp-value-key
                  (codec/tid "Observation")
                  (codec/id-bytes "id-155558")
                  hash
                  (codec/c-hash "value-quantity")
                  (codec/quantity "mm[Hg]" 140M)))))

        (testing "third search-param-value-key is about `system|code value`"
          (is (bytes/=
                k4
                (codec/sp-value-resource-key
                  (codec/c-hash "value-quantity")
                  (codec/tid "Observation")
                  (codec/quantity "http://unitsofmeasure.org|mm[Hg]" 140M)
                  (codec/id-bytes "id-155558")
                  hash))))

        (testing "third resource-value-key is about `system|code value`"
          (is (bytes/=
                k5
                (codec/resource-sp-value-key
                  (codec/tid "Observation")
                  (codec/id-bytes "id-155558")
                  hash
                  (codec/c-hash "value-quantity")
                  (codec/quantity "http://unitsofmeasure.org|mm[Hg]" 140M)))))))

    (testing "with value and unit"
      (let [observation
            {:fhir/type :fhir/Observation
             :id "id-155558"
             :status #fhir/code"final"
             :value
             {:fhir/type :fhir/Quantity
              :value 140M
              :unit "mmHg"}}
            hash (hash/generate observation)
            [[_ k0] [_ k1] [_ k2] [_ k3]]
            (search-param/index-entries
              (sr/get search-param-registry "value-quantity" "Observation")
              hash observation [])]

        (testing "first search-param-value-key is about `value`"
          (is (bytes/=
                k0
                (codec/sp-value-resource-key
                  (codec/c-hash "value-quantity")
                  (codec/tid "Observation")
                  (codec/quantity nil 140M)
                  (codec/id-bytes "id-155558")
                  hash))))

        (testing "first resource-value-key is about `value`"
          (is (bytes/=
                k1
                (codec/resource-sp-value-key
                  (codec/tid "Observation")
                  (codec/id-bytes "id-155558")
                  hash
                  (codec/c-hash "value-quantity")
                  (codec/quantity nil 140M)))))

        (testing "second search-param-value-key is about `unit value`"
          (is (bytes/=
                k2
                (codec/sp-value-resource-key
                  (codec/c-hash "value-quantity")
                  (codec/tid "Observation")
                  (codec/quantity "mmHg" 140M)
                  (codec/id-bytes "id-155558")
                  hash))))

        (testing "second resource-value-key is about `unit value`"
          (is (bytes/=
                k3
                (codec/resource-sp-value-key
                  (codec/tid "Observation")
                  (codec/id-bytes "id-155558")
                  hash
                  (codec/c-hash "value-quantity")
                  (codec/quantity "mmHg" 140M)))))))

    (testing "with value, unit and code where unit equals code"
      (let [observation
            {:fhir/type :fhir/Observation
             :id "id-155558"
             :status #fhir/code"final"
             :value
             {:fhir/type :fhir/Quantity
              :value 120M
              :unit "mm[Hg]"
              :code #fhir/code"mm[Hg]"}}
            hash (hash/generate observation)
            [[_ k0] [_ k1] [_ k2] [_ k3]]
            (search-param/index-entries
              (sr/get search-param-registry "value-quantity" "Observation")
              hash observation [])]

        (testing "first search-param-value-key is about `value`"
          (is (bytes/=
                k0
                (codec/sp-value-resource-key
                  (codec/c-hash "value-quantity")
                  (codec/tid "Observation")
                  (codec/quantity nil 120M)
                  (codec/id-bytes "id-155558")
                  hash))))

        (testing "first resource-value-key is about `value`"
          (is (bytes/=
                k1
                (codec/resource-sp-value-key
                  (codec/tid "Observation")
                  (codec/id-bytes "id-155558")
                  hash
                  (codec/c-hash "value-quantity")
                  (codec/quantity nil 120M)))))

        (testing "second search-param-value-key is about `code value`"
          (is (bytes/=
                k2
                (codec/sp-value-resource-key
                  (codec/c-hash "value-quantity")
                  (codec/tid "Observation")
                  (codec/quantity "mm[Hg]" 120M)
                  (codec/id-bytes "id-155558")
                  hash))))

        (testing "second resource-value-key is about `code value`"
          (is (bytes/=
                k3
                (codec/resource-sp-value-key
                  (codec/tid "Observation")
                  (codec/id-bytes "id-155558")
                  hash
                  (codec/c-hash "value-quantity")
                  (codec/quantity "mm[Hg]" 120M)))))))

    (testing "with value, unit and code where unit differs from code"
      (let [observation
            {:fhir/type :fhir/Observation
             :id "id-155558"
             :status #fhir/code"final"
             :value
             {:fhir/type :fhir/Quantity
              :value 120M
              :unit "mmHg"
              :code #fhir/code"mm[Hg]"}}
            hash (hash/generate observation)
            [[_ k0] [_ k1] [_ k2] [_ k3] [_ k4] [_ k5]]
            (search-param/index-entries
              (sr/get search-param-registry "value-quantity" "Observation")
              hash observation [])]

        (testing "first search-param-value-key is about `value`"
          (is (bytes/=
                k0
                (codec/sp-value-resource-key
                  (codec/c-hash "value-quantity")
                  (codec/tid "Observation")
                  (codec/quantity nil 120M)
                  (codec/id-bytes "id-155558")
                  hash))))

        (testing "first resource-value-key is about `value`"
          (is (bytes/=
                k1
                (codec/resource-sp-value-key
                  (codec/tid "Observation")
                  (codec/id-bytes "id-155558")
                  hash
                  (codec/c-hash "value-quantity")
                  (codec/quantity nil 120M)))))

        (testing "second search-param-value-key is about `code value`"
          (is (bytes/=
                k2
                (codec/sp-value-resource-key
                  (codec/c-hash "value-quantity")
                  (codec/tid "Observation")
                  (codec/quantity "mm[Hg]" 120M)
                  (codec/id-bytes "id-155558")
                  hash))))

        (testing "second resource-value-key is about `code value`"
          (is (bytes/=
                k3
                (codec/resource-sp-value-key
                  (codec/tid "Observation")
                  (codec/id-bytes "id-155558")
                  hash
                  (codec/c-hash "value-quantity")
                  (codec/quantity "mm[Hg]" 120M)))))

        (testing "third search-param-value-key is about `unit value`"
          (is (bytes/=
                k4
                (codec/sp-value-resource-key
                  (codec/c-hash "value-quantity")
                  (codec/tid "Observation")
                  (codec/quantity "mmHg" 120M)
                  (codec/id-bytes "id-155558")
                  hash))))

        (testing "third resource-value-key is about `unit value`"
          (is (bytes/=
                k5
                (codec/resource-sp-value-key
                  (codec/tid "Observation")
                  (codec/id-bytes "id-155558")
                  hash
                  (codec/c-hash "value-quantity")
                  (codec/quantity "mmHg" 120M)))))))))