(ns formsmith.rules.blank-idioms
  (:require [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [rewrite-clj.zip :as z]))

(def ^:private trim-heads
  '#{str/trim clojure.string/trim})

(def ^:private blank-heads
  '#{str/blank? clojure.string/blank?})

(defn- string-literal? [form]
  (and (string? form)
       (= "" form)))

(defn- trim-or-symbol-parts [zloc]
  (when (and (helpers/list-form? zloc)
             (contains? trim-heads (helpers/head-symbol zloc)))
    (let [[trim-head-loc or-loc extra-loc] (helpers/child-locs zloc)]
      (when (and trim-head-loc or-loc (nil? extra-loc)
                 (helpers/list-form? or-loc)
                 (= 'or (helpers/head-symbol or-loc)))
        (let [[_ symbol-loc fallback-loc or-extra-loc] (helpers/child-locs or-loc)
              symbol-form (some-> symbol-loc z/sexpr)
              fallback-form (some-> fallback-loc z/sexpr)]
          (when (and symbol-loc fallback-loc (nil? or-extra-loc)
                     (symbol? symbol-form)
                     (string-literal? fallback-form))
            {:symbol symbol-form
             :blank-head (symbol (namespace (helpers/head-symbol zloc)) "blank?")}))))))

(defn- trim-symbol-parts [zloc]
  (when (and (helpers/list-form? zloc)
             (contains? trim-heads (helpers/head-symbol zloc)))
    (let [[trim-head-loc symbol-loc extra-loc] (helpers/child-locs zloc)
          symbol-form (some-> symbol-loc z/sexpr)]
      (when (and trim-head-loc
                 symbol-loc
                 (nil? extra-loc)
                 (symbol? symbol-form))
        {:symbol symbol-form
         :blank-head (symbol (namespace (helpers/head-symbol zloc)) "blank?")}))))

(defn- seq-trim-when-match [zloc]
  (when (and (helpers/list-form? zloc)
             (= 'when (helpers/head-symbol zloc)))
    (let [[_ test-loc & body-locs] (helpers/child-locs zloc)]
      (when (and test-loc (not-empty body-locs)
                 (helpers/list-form? test-loc)
                 (= 'seq (helpers/head-symbol test-loc)))
        (let [[_ trim-loc seq-extra-loc] (helpers/child-locs test-loc)]
          (when (and trim-loc (nil? seq-extra-loc))
            (when-let [{:keys [symbol blank-head]} (trim-or-symbol-parts trim-loc)]
              {:rewritten (list* 'when-not (list blank-head symbol) (mapv z/sexpr body-locs))
               :rule-id :when/seq-trim-blank
               :message "when over seq(trim(or ...)) can be written as when-not blank?"
               :safety :semantic-pattern})))))))

(defn- blank-test-or-match [zloc]
  (when (and (helpers/list-form? zloc)
             (= 'if (helpers/head-symbol zloc)))
    (let [[_ test-loc then-loc else-loc extra-loc] (helpers/child-locs zloc)
          then-form (some-> then-loc z/sexpr)]
      (when (and test-loc then-loc else-loc (nil? extra-loc)
                 (true? then-form)
                 (helpers/list-form? test-loc)
                 (contains? blank-heads (helpers/head-symbol test-loc)))
        {:rewritten (list 'or (z/sexpr test-loc) (z/sexpr else-loc))
         :rule-id :if/blank-or
         :message "if with a blank? test and true branch can be written as or"
         :safety :semantic-pattern}))))

(defn- empty-trim-match [zloc]
  (when (and (helpers/list-form? zloc)
             (= 'empty? (helpers/head-symbol zloc)))
    (let [[_ trim-loc extra-loc] (helpers/child-locs zloc)]
      (when (and trim-loc (nil? extra-loc))
        (or (when-let [{:keys [symbol blank-head]} (trim-or-symbol-parts trim-loc)]
              {:rewritten (list blank-head symbol)
               :rule-id :empty/trim-or-blank
               :message "empty? over trim(or ... \"\") can be written as blank?"
               :safety :syntax-safe})
            (when-let [{:keys [symbol blank-head]} (trim-symbol-parts trim-loc)]
              {:rewritten (list blank-head symbol)
               :rule-id :empty/trim-blank
               :message "empty? over trim can usually be written as blank?"
               :safety :unsafe
               :contract {:blocked-by ["`empty?` over `trim` throws on nil, while `blank?` treats nil as blank."
                                       "The local static pass cannot prove the value is non-nil or that nil should be accepted."]
                          :llm-task "Determine the intended nil semantics for this value. If nil should be treated as blank, rewrite to blank?. If nil should remain invalid, keep the existing shape or add an explicit guard that preserves the exception behavior."
                          :acceptance ["Document or infer the intended nil behavior from callers and tests."
                                       "Apply the smallest local change that preserves that behavior."
                                       "Run affected tests and `formsmith check` on the touched paths."]}}))))))

(defn- match-blank-idiom [zloc]
  (or (seq-trim-when-match zloc)
      (blank-test-or-match zloc)
      (empty-trim-match zloc)))

(def rule
  {:id :blank/idioms
   :summary "Prefer blank?-centric idioms over trim-or and literal true branches"
   :safety :semantic-pattern
   :kinds #{:rewrite}
   :check (fn [zloc]
            (boolean (match-blank-idiom zloc)))
   :apply (fn [zloc context]
            (when-let [{:keys [rewritten rule-id message safety contract]} (match-blank-idiom zloc)]
              (let [before (helpers/node-string zloc)
                    applied? (helpers/autofix-allowed? context zloc safety)
                    updated (if applied?
                              (helpers/replace-with-form zloc rewritten)
                              zloc)
                    after (helpers/node-string updated)]
                {:zloc updated
                 :finding (finding/make
                           {:rule-id rule-id
                            :message message
                            :safety safety
                            :severity :warning
                            :source :formsmith
                            :file (:file context)
                            :line (helpers/line zloc)
                            :column (helpers/column zloc)
                            :applied? applied?
                            :kind :rewrite
                            :suggested-source (pr-str rewritten)
                            :contract contract
                            :before before
                            :after after})})))})
