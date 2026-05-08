(ns formsmith.rules.empty-let
  (:require [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [rewrite-clj.zip :as z]))

(defn- empty-let-parts [zloc]
  (when (and (helpers/list-form? zloc)
             (= 'let (helpers/head-symbol zloc)))
    (let [[_ bindings-loc first-body-loc second-body-loc :as children] (helpers/child-locs zloc)]
      (when (and (= :vector (z/tag bindings-loc))
                 (empty? (z/sexpr bindings-loc))
                 first-body-loc)
        {:bindings-loc bindings-loc
         :first-body-loc first-body-loc
         :body-count (- (count children) 2)
         :multi-body? (some? second-body-loc)}))))

(defn- single-body-source [zloc first-body-loc]
  (let [body-source (helpers/node-string first-body-loc)
        indent-delta (max 0 (- (helpers/column first-body-loc)
                               (helpers/column zloc)))]
    (helpers/outdent-block body-source indent-delta)))

(defn- multi-body-source [zloc {:keys [bindings-loc]}]
  (let [outer-source (helpers/node-string zloc)
        bindings-source (helpers/node-string bindings-loc)
        bindings-index (helpers/find-substring-index outer-source bindings-source)
        tail (subs outer-source (+ bindings-index (count bindings-source)))]
    (str "(do" tail)))

(defn- rewritten-source [zloc {:keys [first-body-loc multi-body?] :as parts}]
  (if multi-body?
    (multi-body-source zloc parts)
    (single-body-source zloc first-body-loc)))

(def rule
  {:id :let/empty-bindings
   :summary "Remove empty let binding blocks"
   :safety :syntax-safe
   :kinds #{:rewrite}
   :check (fn [zloc]
            (boolean (empty-let-parts zloc)))
   :apply (fn [zloc context]
            (when-let [parts (empty-let-parts zloc)]
              (let [before (helpers/node-string zloc)
                    applied? (helpers/autofix-allowed? context zloc :syntax-safe)
                    updated (if applied?
                              (helpers/replace-with-string zloc
                                                           (rewritten-source zloc parts))
                              zloc)
                    after (helpers/node-string updated)]
                {:zloc updated
                 :finding (finding/make
                           {:rule-id :let/empty-bindings
                            :message "Empty let bindings can be removed"
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
