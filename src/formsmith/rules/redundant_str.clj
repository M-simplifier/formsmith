(ns formsmith.rules.redundant-str
  (:require [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [rewrite-clj.zip :as z]))

(defn- match-redundant-str [zloc]
  (when (and (helpers/list-form? zloc)
             (= 'str (helpers/head-symbol zloc))
             (not (helpers/inside-thread-macro-step? zloc)))
    (let [[_ string-loc extra-loc] (helpers/child-locs zloc)
          value (some-> string-loc z/sexpr)]
      (when (and string-loc
                 (nil? extra-loc)
                 (string? value))
        {:replacement-source (helpers/node-string string-loc)}))))

(def rule
  {:id :str/redundant-string-literal
  :summary "Remove redundant str around a single string literal"
   :safety :syntax-safe
   :kinds #{:rewrite}
   :check (fn [zloc]
            (boolean (match-redundant-str zloc)))
   :apply (fn [zloc context]
            (when-let [{:keys [replacement-source]} (match-redundant-str zloc)]
              (let [before (helpers/node-string zloc)
                    applied? (helpers/autofix-allowed? context zloc :syntax-safe)
                    updated (if applied?
                              (helpers/replace-with-string zloc replacement-source)
                              zloc)
                    after (helpers/node-string updated)]
                {:zloc updated
                 :finding (finding/make
                           {:rule-id :str/redundant-string-literal
                            :message "A single string literal does not need str"
                            :safety :syntax-safe
                            :severity :warning
                            :source :formsmith
                            :file (:file context)
                            :line (helpers/line zloc)
                            :column (helpers/column zloc)
                            :applied? applied?
                            :kind :rewrite
                            :before before
                            :after after})})))})
