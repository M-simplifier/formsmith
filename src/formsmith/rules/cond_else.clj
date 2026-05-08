(ns formsmith.rules.cond-else
  (:require [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [rewrite-clj.zip :as z]))

(defn- cond-else-parts [zloc]
  (when (and (helpers/list-form? zloc)
             (= 'cond (helpers/head-symbol zloc)))
    (let [children (helpers/child-locs zloc)
          last-test-loc (nth children (- (count children) 2) nil)
          last-expr-loc (nth children (dec (count children)) nil)]
      (when (and last-test-loc
                 last-expr-loc
                 (>= (count children) 3)
                 (true? (z/sexpr last-test-loc)))
        {:last-test-loc last-test-loc
         :replacement ":else"}))))

(def rule
  {:id :cond/true-else
   :summary "Prefer :else for the final cond catch-all clause"
   :safety :syntax-safe
   :kinds #{:rewrite}
   :check (fn [zloc]
            (boolean (cond-else-parts zloc)))
   :apply (fn [zloc context]
            (when-let [{:keys [last-test-loc replacement]} (cond-else-parts zloc)]
              (let [before (helpers/node-string zloc)
                    applied? (helpers/autofix-allowed? context zloc :syntax-safe)
                    updated (if applied?
                              (z/up
                               (helpers/replace-with-string last-test-loc replacement))
                              zloc)
                    after (helpers/node-string updated)]
                {:zloc updated
                 :finding (finding/make
                           {:rule-id :cond/true-else
                            :message "The final cond catch-all should use :else"
                            :safety :syntax-safe
                            :severity :warning
                            :source :formsmith
                            :file (:file context)
                            :line (helpers/line zloc)
                            :column (helpers/column zloc)
                            :applied? applied?
                            :kind :rewrite
                            :before before
                            :after after
                            :replacement replacement})})))})
