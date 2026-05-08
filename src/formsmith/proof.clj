(ns formsmith.proof)

(def artifacts
  {:core/negated-condition
   {:file "proofs/Formsmith/Core.lean"
    :theorems ['Formsmith/when_not_over_not_equiv
               'Formsmith/if_not_over_not_equiv]}})

(defn known-proof? [proof-id]
  (contains? artifacts proof-id))
