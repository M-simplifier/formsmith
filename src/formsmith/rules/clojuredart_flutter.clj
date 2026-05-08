(ns formsmith.rules.clojuredart-flutter
  (:require [clojure.string :as str]
            [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [formsmith.source :as source]
            [rewrite-clj.zip :as z]))

(defn- cljd-file? [context]
  (str/ends-with? (str (:file context)) ".cljd"))

(defn- flutter-alias [context]
  (source/alias-for (:source context) 'cljd.flutter))

(defn- deprecated-alpha-required? [context]
  (or (source/required? (:source context) 'cljd.flutter.alpha)
      (source/required? (:source context) 'cljd.flutter.alpha2)))

(defn- ns-form? [zloc]
  (and (helpers/list-form? zloc)
       (= 'ns (helpers/head-symbol zloc))))

(defn- child-key? [form]
  (or (= :child form)
      (= '.child form)))

(defn- widget-constructor-head? [head]
  (let [name-part (some-> head name)]
    (and (symbol? head)
         (namespace head)
         (not= "nest" name-part)
         (boolean (re-find #"^[A-Z]" (str/replace name-part #"\.$" ""))))))

(defn- widget-form? [zloc]
  (and (helpers/list-form? zloc)
       (widget-constructor-head? (helpers/head-symbol zloc))))

(defn- trailing-child-pair [zloc]
  (let [children (helpers/child-locs zloc)
        n (count children)]
    (when (>= n 3)
      (let [key-loc (nth children (- n 2))
            child-loc (nth children (dec n))]
        (when (child-key? (z/sexpr key-loc))
          {:children children
           :key-loc key-loc
           :child-loc child-loc})))))

(defn- without-trailing-child-source [{:keys [children]}]
  (let [kept (subvec (vec children) 0 (- (count children) 2))]
    (str "(" (str/join " " (map helpers/node-string kept)) ")")))

(defn- flatten-child-chain [zloc]
  (when (widget-form? zloc)
    (loop [loc zloc
           widgets []]
      (if-let [{:keys [child-loc] :as match} (trailing-child-pair loc)]
        (recur child-loc (conj widgets (without-trailing-child-source match)))
        (let [widgets (conj widgets (helpers/node-string loc))]
          (when (>= (count widgets) 3)
            widgets))))))

(defn- nest-source [alias widgets]
  (str "(" alias "/nest\n  " (str/join "\n  " widgets) ")"))

(defn- cljd-nest-match [zloc context]
  (when (cljd-file? context)
    (when-let [alias (flutter-alias context)]
      (when-let [widgets (flatten-child-chain zloc)]
        {:rule-id :clojuredart/widget-child-chain
         :message "Nested Flutter :child/.child widget chains can be flattened with cljd.flutter/nest"
         :replacement-source (nest-source alias widgets)
         :safety :syntax-safe}))))

(defn- deprecated-alpha-match [zloc context]
  (when (and (cljd-file? context)
             (ns-form? zloc)
             (deprecated-alpha-required? context))
    {:rule-id :clojuredart/deprecated-flutter-alpha
     :message "Deprecated cljd.flutter alpha namespaces should be migrated to cljd.flutter"
     :safety :unsafe
     :contract {:blocked-by ["The tool cannot prove which alpha APIs are still used or whether a direct namespace swap is sufficient."]
                :llm-task "Inspect cljd.flutter.alpha or cljd.flutter.alpha2 usages and migrate them to cljd.flutter with the smallest behavior-preserving change."
                :acceptance ["The namespace requires cljd.flutter instead of deprecated alpha namespaces."
                             "ClojureDart build or relevant widget tests pass."
                             "Formsmith check is clean on the touched .cljd files."]}}))

(defn- match [zloc context]
  (or (cljd-nest-match zloc context)
      (deprecated-alpha-match zloc context)))

(def rule
  {:id :clojuredart/flutter-ui
   :summary "Prefer canonical cljd.flutter UI forms such as f/nest for long widget child chains"
   :safety :syntax-safe
   :kinds #{:rewrite}
   :check (fn [zloc]
            (or (widget-form? zloc)
                (ns-form? zloc)))
   :apply (fn [zloc context]
            (if-let [{:keys [rule-id message replacement-source safety contract]} (match zloc context)]
              (let [before (helpers/node-string zloc)
                    applied? (and replacement-source
                                  (helpers/autofix-allowed? context zloc safety))
                    updated (if applied?
                              (helpers/replace-with-string zloc replacement-source)
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
                            :suggested-source replacement-source
                            :contract contract
                            :before before
                            :after after})})
              {:zloc zloc
               :finding nil}))})
