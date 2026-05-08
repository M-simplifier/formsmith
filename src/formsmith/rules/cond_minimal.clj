(ns formsmith.rules.cond-minimal
  (:require [clojure.string :as str]
            [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [rewrite-clj.zip :as z]))

(defn- match-cond-minimal [zloc]
  (when (and (helpers/list-form? zloc)
             (= 'cond (helpers/head-symbol zloc)))
    (let [[head-loc test-loc expr-loc catchall-loc else-loc extra-loc] (helpers/child-locs zloc)]
      (cond
        (and head-loc test-loc expr-loc (nil? catchall-loc))
        {:kind :single-clause-when
         :head-loc head-loc
         :test-loc test-loc
         :expr-loc expr-loc
         :rule-id :cond/single-clause-when
         :message "Single-clause cond can be written as when"}

        (and head-loc test-loc expr-loc catchall-loc else-loc (nil? extra-loc)
             (#{:else true} (z/sexpr catchall-loc)))
        {:kind :two-clause-if
         :head-loc head-loc
         :test-loc test-loc
         :expr-loc expr-loc
         :catchall-loc catchall-loc
         :else-loc else-loc
         :rule-id :cond/two-clause-if
         :message "Two-clause cond with a catch-all can be written as if"}

        :else nil))))

(defn- clean-head-separator [source]
  (str/replace source #"^[ \t]+(\r?\n)" "$1"))

(defn- rewritten-source [zloc {:keys [kind head-loc test-loc expr-loc catchall-loc else-loc]}]
  (let [outer-source (helpers/node-string zloc)
        head-source (helpers/node-string head-loc)
        test-source (helpers/node-string test-loc)
        expr-source (helpers/node-string expr-loc)
        head-index (helpers/find-substring-index outer-source head-source 1)
        test-index (helpers/find-substring-index outer-source test-source (+ head-index (count head-source)))
        expr-index (helpers/find-substring-index outer-source expr-source (+ test-index (count test-source)))
        between-head-test (clean-head-separator
                           (subs outer-source (+ head-index (count head-source)) test-index))
        between-test-expr (subs outer-source (+ test-index (count test-source)) expr-index)]
    (case kind
      :single-clause-when
      (str "(when"
           between-head-test
           test-source
           between-test-expr
           expr-source
           (subs outer-source (+ expr-index (count expr-source))))

      :two-clause-if
      (let [catchall-source (helpers/node-string catchall-loc)
            else-source (helpers/node-string else-loc)
            catchall-index (helpers/find-substring-index outer-source catchall-source (+ expr-index (count expr-source)))
            else-index (helpers/find-substring-index outer-source else-source (+ catchall-index (count catchall-source)))
            between-expr-catchall (subs outer-source (+ expr-index (count expr-source)) catchall-index)]
        (str "(if"
             between-head-test
             test-source
             between-test-expr
             expr-source
             between-expr-catchall
             else-source
             (subs outer-source (+ else-index (count else-source))))))))

(def rule
  {:id :cond/minimal-form
   :summary "Prefer when/if over minimal cond forms"
   :safety :syntax-safe
   :kinds #{:rewrite}
   :check (fn [zloc]
            (boolean (match-cond-minimal zloc)))
   :apply (fn [zloc context]
            (when-let [{:keys [rule-id message] :as parts} (match-cond-minimal zloc)]
              (let [before (helpers/node-string zloc)
                    applied? (helpers/autofix-allowed? context zloc :syntax-safe)
                    updated (if applied?
                              (helpers/replace-with-string zloc
                                                           (rewritten-source zloc parts))
                              zloc)
                    after (helpers/node-string updated)]
                {:zloc updated
                 :finding (finding/make
                           {:rule-id rule-id
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
