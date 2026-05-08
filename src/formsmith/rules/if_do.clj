(ns formsmith.rules.if-do
  (:require [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]))

(defn- if-do-parts [zloc]
  (when (and (helpers/list-form? zloc)
             (= 'if (helpers/head-symbol zloc)))
    (let [[_ _ then-loc else-loc extra-loc] (helpers/child-locs zloc)
          then-body-loc (some-> then-loc helpers/single-do-body-loc)
          else-body-loc (some-> else-loc helpers/single-do-body-loc)]
      (when (and then-loc
                 (nil? extra-loc)
                 (or then-body-loc else-body-loc))
        {:then-loc then-loc
         :else-loc else-loc
         :then-body-loc then-body-loc
         :else-body-loc else-body-loc}))))

(defn- rewritten-source [zloc {:keys [then-loc else-loc then-body-loc else-body-loc]}]
  (let [outer-source (helpers/node-string zloc)
        then-source (helpers/node-string then-loc)
        then-replacement (helpers/node-string (or then-body-loc then-loc))
        then-index (helpers/find-substring-index outer-source then-source)
        then-end (+ then-index (count then-source))]
    (if else-loc
      (let [else-source (helpers/node-string else-loc)
            else-replacement (helpers/node-string (or else-body-loc else-loc))
            else-index (helpers/find-substring-index outer-source else-source then-end)]
        (str (subs outer-source 0 then-index)
             then-replacement
             (subs outer-source then-end else-index)
             else-replacement
             (subs outer-source (+ else-index (count else-source)))))
      (str (subs outer-source 0 then-index)
           then-replacement
           (subs outer-source then-end)))))

(def rule
  {:id :if/redundant-do-branch
   :summary "Flatten single-expression do wrappers inside if branches"
   :safety :syntax-safe
   :kinds #{:rewrite}
   :check (fn [zloc]
            (boolean (if-do-parts zloc)))
   :apply (fn [zloc context]
            (when-let [{:keys [then-body-loc else-body-loc] :as parts} (if-do-parts zloc)]
              (let [before (helpers/node-string zloc)
                    applied? (helpers/autofix-allowed? context zloc :syntax-safe)
                    updated (if applied?
                              (helpers/replace-with-string zloc
                                                           (rewritten-source zloc parts))
                              zloc)
                    after (helpers/node-string updated)
                    message (cond
                              (and then-body-loc else-body-loc)
                              "Single-expression do wrappers in both if branches can be flattened"

                              then-body-loc
                              "Single-expression do wrapper in the then branch can be flattened"

                              :else
                              "Single-expression do wrapper in the else branch can be flattened")]
                {:zloc updated
                 :finding (finding/make
                           {:rule-id :if/redundant-do-branch
                            :message message
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
