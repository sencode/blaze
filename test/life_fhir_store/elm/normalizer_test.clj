(ns life-fhir-store.elm.normalizer-test
  "Section numbers are according to
  https://cql.hl7.org/04-logicalspecification.html."
  (:require
    [clojure.test :refer :all]
    [juxt.iota :refer [given]]
    [life-fhir-store.elm.normalizer :refer [normalize]]))


(def expression-1
  {:type "Implies" :operand ["A" "B"]})


(def normalized-expression-1
  {:type "Or" :operand [{:type "Not" :operand "A"} "B"]})


(def expression-2
  {:type "Xor" :operand ["A" "B"]})


(def normalized-expression-2
  {:type "Or"
   :operand
   [{:type "And"
     :operand [{:type "Not" :operand "A"} "B"]}
    {:type "And"
     :operand ["A" {:type "Not" :operand "B"}]}]})



;; 2. Structured Values

;; 2.3. Property
(deftest normalize-property-test
  (testing "Normalizes the source of a Property expression"
    (given (normalize {:type "Property" :source expression-1})
      :source := normalized-expression-1)))



;; 10. Queries

;; 10.1. Query
(deftest normalize-query-test
  (testing "Normalizes the expression of a AliasedQuerySource in a Query expression"
    (given (normalize {:type "Query" :source [{:expression expression-1}]})
      :source := [{:expression normalized-expression-1}]))

  (testing "Normalizes the expression of a LetClause in a Query expression"
    (given (normalize {:type "Query" :let [{:expression expression-1}]})
      :let := [{:expression normalized-expression-1}]))

  (testing "Normalizes the expression of a RelationshipClause in a Query expression"
    (given (normalize {:type "Query" :relationship [{:expression expression-1}]})
      :relationship := [{:expression normalized-expression-1}]))

  (testing "Normalizes the where expression of a Query expression"
    (given (normalize {:type "Query" :where expression-1})
      :where := normalized-expression-1))

  (testing "Normalizes the expression of the ReturnClause in a Query expression"
    (given (normalize {:type "Query" :return {:expression expression-1}})
      :return := {:expression normalized-expression-1}))

  ;; TODO: SortClause
  )



;; 12. Comparison Operators

;; 12.1. Equal
(deftest normalize-equal-test
  (testing "Normalizes both operands of an Equal expression"
    (given (normalize {:type "Equal" :operand [expression-1 expression-2]})
      :operand := [normalized-expression-1 normalized-expression-2])))


;; 12.2. Equivalent
(deftest normalize-equivalent-test
  (testing "Normalizes both operands of an Equivalent expression"
    (given (normalize {:type "Equivalent" :operand [expression-1 expression-2]})
      :operand := [normalized-expression-1 normalized-expression-2])))


;; 12.3. Greater
(deftest normalize-greater-test
  (testing "Normalizes both operands of a Greater expression"
    (given (normalize {:type "Greater" :operand [expression-1 expression-2]})
      :operand := [normalized-expression-1 normalized-expression-2])))


;; 12.4. GreaterOrEqual
(deftest normalize-greater-or-equal-test
  (testing "Normalizes both operands of a GreaterOrEqual expression"
    (given (normalize {:type "GreaterOrEqual" :operand [expression-1 expression-2]})
      :operand := [normalized-expression-1 normalized-expression-2])))


;; 12.5. Less
(deftest normalize-less-test
  (testing "Normalizes both operands of a Less expression"
    (given (normalize {:type "Less" :operand [expression-1 expression-2]})
      :operand := [normalized-expression-1 normalized-expression-2])))


;; 12.6. LessOrEqual
(deftest normalize-less-or-equal-test
  (testing "Normalizes both operands of a LessOrEqual expression"
    (given (normalize {:type "LessOrEqual" :operand [expression-1 expression-2]})
      :operand := [normalized-expression-1 normalized-expression-2])))


;; 12.7. NotEqual
(deftest normalize-not-equal-test
  (testing "Normalizes both operands of a NotEqual expression"
    (given (normalize {:type "NotEqual" :operand [expression-1 expression-2]})
      :operand := [normalized-expression-1 normalized-expression-2])))



;; 13. Logical Operators

;; 13.1. And
(deftest normalize-and-test
  (testing "Normalizes both operands of an And expression"
    (given (normalize {:type "And" :operand [expression-1 expression-2]})
      :operand := [normalized-expression-1 normalized-expression-2])))


;; 13.2 Implies
(deftest normalize-implies-test
  (testing "A implies B normalizes to not A or B"
    (given (normalize {:type "Implies" :operand ["A" "B"]})
      :operand := [{:type "Not" :operand "A"} "B"])))


;; 13.3. Not
(deftest normalize-not-test
  (testing "Normalizes the operand of a Not expression"
    (given (normalize {:type "Not" :operand expression-1})
      :operand := normalized-expression-1)))


;; 13.4. Or
(deftest normalize-or-test
  (testing "Normalizes both operands of an Or expression"
    (given (normalize {:type "Or" :operand [expression-1 expression-2]})
      :operand := [normalized-expression-1 normalized-expression-2])))


;; 13.5 Xor
(deftest normalize-xor-test
  (testing "A xor B normalizes to (not A and B) or (A and not B)"
    (given (normalize {:type "Xor" :operand ["A" "B"]})
      :type := "Or"
      :operand := [{:type "And"
                    :operand [{:type "Not" :operand "A"} "B"]}
                   {:type "And"
                    :operand ["A" {:type "Not" :operand "B"}]}])))



;; 14. Nullological Operators

;; 14.2. Coalesce
(deftest normalize-or-test
  (testing "Normalizes all operands of an Coalesce expression"
    (given (normalize {:type "Coalesce" :operand [expression-1 expression-2]})
      :operand := [normalized-expression-1 normalized-expression-2])))


;; 14.3. IsFalse
(deftest normalize-is-false-test
  (testing "Normalizes the operand of an IsFalse expression"
    (given (normalize {:type "IsFalse" :operand expression-1})
      :operand := normalized-expression-1)))


;; 14.4. IsNull
(deftest normalize-is-null-test
  (testing "Normalizes the operand of an IsNull expression"
    (given (normalize {:type "IsNull" :operand expression-1})
      :operand := normalized-expression-1)))


;; 14.5. IsTrue
(deftest normalize-is-true-test
  (testing "Normalizes the operand of an IsTrue expression"
    (given (normalize {:type "IsTrue" :operand expression-1})
      :operand := normalized-expression-1)))
