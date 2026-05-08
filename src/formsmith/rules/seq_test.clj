(ns formsmith.rules.seq-test
  (:require [formsmith.analysis :as analysis]
            [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [rewrite-clj.zip :as z]))

(defn- seq-test-parts [zloc]
  (when (and (helpers/list-form? zloc)
             (= 'if (helpers/head-symbol zloc)))
    (let [[_ test-loc then-loc else-loc extra-loc] (helpers/child-locs zloc)]
      (when (and test-loc then-loc else-loc (nil? extra-loc)
                 (helpers/list-form? test-loc)
                 (= 'seq (helpers/head-symbol test-loc)))
        (let [[_ target-loc seq-extra-loc] (helpers/child-locs test-loc)
              target-form (some-> target-loc z/sexpr)
              then-form (z/sexpr then-loc)
              else-form (z/sexpr else-loc)]
          (when (and target-loc
                     (nil? seq-extra-loc)
                     (helpers/seq-bindable-form? target-form))
            {:target-loc target-loc
             :target-form target-form
             :then-form then-form
             :else-form else-form}))))))

(defn- self-returning-then? [{:keys [target-form then-form]}]
  (= target-form then-form))

(defn- seq-rewrite-match [zloc]
  (when-let [{:keys [target-form then-form else-form] :as parts} (seq-test-parts zloc)]
    (if (self-returning-then? parts)
      {:target-loc (:target-loc parts)
       :target-form target-form
       :rewritten (list 'or (list 'not-empty target-form) else-form)
       :rule-id :if/seq-not-empty-or
       :message "if that returns the tested form can be written with not-empty and or"}
      (let [contains-target? (helpers/form-occurs? then-form target-form)]
        (when (or (symbol? target-form) contains-target?)
          (when-let [binding-symbol (helpers/seq-binding-symbol target-form [then-form else-form])]
            {:target-loc (:target-loc parts)
             :target-form target-form
             :rewritten (list 'if-let
                              [binding-symbol (list 'not-empty target-form)]
                              (if (= binding-symbol target-form)
                                then-form
                                (helpers/replace-form then-form target-form binding-symbol))
                              else-form)
             :rule-id :if/seq-if-let
             :message "if that tests seq can be written as if-let with not-empty"}))))))

(defn- guarded-local-target? [context {:keys [target-loc target-form]}]
  (and (:guarded? context)
       (symbol? target-form)
       (analysis/local-usage? (:analysis context)
                              (:file context)
                              target-form
                              (helpers/line target-loc)
                              (helpers/column target-loc))))

(def rule
  {:id :if/seq-test
   :summary "Prefer not-empty based idioms over testing seq directly in if"
   :safety :semantic-pattern
   :kinds #{:rewrite}
   :check (fn [zloc]
            (boolean (seq-rewrite-match zloc)))
   :apply (fn [zloc context]
            (when-let [{:keys [rewritten rule-id message] :as match} (seq-rewrite-match zloc)]
              (let [before (helpers/node-string zloc)
                    applied? (or (helpers/autofix-allowed? context zloc :semantic-pattern)
                                 (and (not (helpers/comment-sensitive? zloc))
                                      (guarded-local-target? context match)))
                    updated (if applied?
                              (helpers/replace-with-form zloc rewritten)
                              zloc)
                    after (helpers/node-string updated)]
                {:zloc updated
                 :finding (finding/make
                           {:rule-id rule-id
                            :message message
                            :safety :semantic-pattern
                            :severity :warning
                            :source :formsmith
                            :file (:file context)
                            :line (helpers/line zloc)
                            :column (helpers/column zloc)
                            :applied? applied?
                            :kind :rewrite
                            :before before
                            :after after})})))})
