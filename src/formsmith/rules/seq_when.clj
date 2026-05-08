(ns formsmith.rules.seq-when
  (:require [formsmith.analysis :as analysis]
            [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [rewrite-clj.zip :as z]))

(defn- when-seq-parts [zloc]
  (when (and (helpers/list-form? zloc)
             (= 'when (helpers/head-symbol zloc)))
    (let [[_ test-loc & body-locs] (helpers/child-locs zloc)]
      (when (and test-loc (not-empty body-locs)
                 (helpers/list-form? test-loc)
                 (= 'seq (helpers/head-symbol test-loc)))
        (let [[_ target-loc seq-extra-loc] (helpers/child-locs test-loc)
              target-form (some-> target-loc z/sexpr)
              body-forms (mapv z/sexpr body-locs)]
          (when (and target-loc
                     (nil? seq-extra-loc)
                     (helpers/seq-bindable-form? target-form)
                     (some #(helpers/form-occurs? % target-form) body-forms))
            (when-let [binding-symbol (helpers/seq-binding-symbol target-form body-forms)]
              {:target-loc target-loc
               :target-form target-form
               :binding-symbol binding-symbol
               :body-forms (if (= binding-symbol target-form)
                             body-forms
                             (mapv #(helpers/replace-form % target-form binding-symbol)
                                   body-forms))})))))))

(defn- when-seq-match [zloc]
  (when-let [{:keys [target-loc target-form binding-symbol body-forms]} (when-seq-parts zloc)]
    {:target-loc target-loc
     :target-form target-form
     :rewritten (list* 'when-let [binding-symbol (list 'not-empty target-form)] body-forms)
     :rule-id :when/seq-when-let
     :message "when that tests seq can be written as when-let with not-empty"}))

(defn- guarded-local-target? [context {:keys [target-loc target-form]}]
  (and (:guarded? context)
       (symbol? target-form)
       (analysis/local-usage? (:analysis context)
                              (:file context)
                              target-form
                              (helpers/line target-loc)
                              (helpers/column target-loc))))

(def rule
  {:id :when/seq-test
   :summary "Prefer when-let with not-empty over testing seq directly in when"
   :safety :semantic-pattern
   :kinds #{:rewrite}
   :check (fn [zloc]
            (boolean (when-seq-match zloc)))
   :apply (fn [zloc context]
            (when-let [{:keys [rewritten rule-id message] :as match} (when-seq-match zloc)]
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
