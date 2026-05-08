(ns formsmith.rules.negated-condition
  (:require [clojure.set :as set]
            [formsmith.analysis :as analysis]
            [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [rewrite-clj.zip :as z]))

(def ^:private negation-heads
  '#{not clojure.core/not})

(def ^:private proof-id
  :core/negated-condition)

(def ^:private local-binding-heads
  '#{binding if-let if-some let let* loop loop* when-let when-some with-local-vars with-open})

(def ^:private comprehension-heads
  '#{doseq for})

(defn- core-symbol? [sym name]
  (= (symbol "clojure.core" name) sym))

(defn- analyzed-core-var? [context loc name]
  (when-let [analysis (:analysis context)]
    (analysis/var-usage? analysis
                         (:file context)
                         (symbol name)
                         'clojure.core
                         (helpers/line loc)
                         (helpers/column loc))))

(defn- source-core-var? [context loc name]
  (let [sym (some-> loc z/sexpr)]
    (or (core-symbol? sym name)
        (analyzed-core-var? context loc name))))

(defn- source-core-operator? [context loc name]
  (let [sym (some-> loc z/sexpr)]
    (case name
      "if" (= 'if sym)
      (source-core-var? context loc name))))

(defn- destructuring-symbols [form]
  (into #{}
        (filter symbol?)
        (tree-seq coll? seq form)))

(defn- binding-vector-symbols [bindings]
  (if (vector? bindings)
    (->> bindings
         (partition-all 2)
         (map first)
         (mapcat destructuring-symbols)
         set)
    #{}))

(defn- comprehension-binding-symbols [bindings]
  (if (vector? bindings)
    (loop [[binding expr & more] (seq bindings)
           symbols #{}]
      (cond
        (nil? binding)
        symbols

        (= :let binding)
        (recur more
               (set/union symbols
                          (binding-vector-symbols expr)))

        (keyword? binding)
        (recur more symbols)

        :else
        (recur more
               (set/union symbols
                          (destructuring-symbols binding)))))
    #{}))

(defn- fn-tail-symbols [forms]
  (let [forms (if (symbol? (first forms))
                (rest forms)
                forms)]
    (if (vector? (first forms))
      (destructuring-symbols (first forms))
      (->> forms
           (keep #(when (and (seq? %) (vector? (first %)))
                    (destructuring-symbols (first %))))
           (apply set/union #{})))))

(defn- defn-tail-symbols [forms]
  (->> forms
       rest
       (drop-while #(or (string? %) (map? %)))
       fn-tail-symbols))

(defn- letfn-binding-symbols [bindings]
  (if (vector? bindings)
    (->> bindings
         (keep (fn [binding]
                 (when (seq? binding)
                   (cond-> (fn-tail-symbols (rest binding))
                     (symbol? (first binding)) (conj (first binding))))))
         (apply set/union #{}))
    #{}))

(defn- catch-symbols [form]
  (let [exception-symbol (nth form 2 nil)]
    (if (symbol? exception-symbol)
      #{exception-symbol}
      #{})))

(defn- as-thread-symbols [form]
  (let [binding-symbol (nth form 2 nil)]
    (if (symbol? binding-symbol)
      #{binding-symbol}
      #{})))

(defn- local-binding-symbols [form]
  (if (seq? form)
    (let [head (first form)]
      (cond
        (contains? local-binding-heads head)
        (binding-vector-symbols (second form))

        (contains? comprehension-heads head)
        (comprehension-binding-symbols (second form))

        (= 'letfn head)
        (letfn-binding-symbols (second form))

        (contains? '#{fn fn*} head)
        (fn-tail-symbols (rest form))

        (contains? '#{defn defn- defmacro} head)
        (defn-tail-symbols (rest form))

        (= 'catch head)
        (catch-symbols form)

        (= 'as-> head)
        (as-thread-symbols form)

        :else
        #{}))
    #{}))

(defn- ancestor-binding-symbols [zloc]
  (loop [loc (z/up zloc)
         symbols #{}]
    (if loc
      (recur (z/up loc)
             (set/union symbols
                        (try
                          (local-binding-symbols (z/sexpr loc))
                          (catch Exception _
                            #{}))))
      symbols)))

(defn- unqualified-symbol [value]
  (when (and (symbol? value) (nil? (namespace value)))
    value))

(defn- resolution-sensitive-symbols
  [{:keys [head-loc head-name negation-head-loc replacement-head]}]
  (let [replacement-symbol (symbol replacement-head)]
    (cond-> #{}
      (unqualified-symbol (some-> negation-head-loc z/sexpr))
      (conj (z/sexpr negation-head-loc))

      (and (not= "if" head-name)
           (unqualified-symbol (some-> head-loc z/sexpr)))
      (conj (z/sexpr head-loc))

      (unqualified-symbol replacement-symbol)
      (conj replacement-symbol))))

(defn- locally-shadowed? [zloc match]
  (boolean
   (seq (set/intersection (ancestor-binding-symbols zloc)
                          (resolution-sensitive-symbols match)))))

(defn- negated-test-parts [test-loc]
  (when (and (helpers/list-form? test-loc)
             (contains? negation-heads (helpers/head-symbol test-loc)))
    (let [[negation-head-loc inner-test-loc extra-loc] (helpers/child-locs test-loc)]
      (when (and inner-test-loc (nil? extra-loc))
        {:negation-head-loc negation-head-loc
         :inner-test-loc inner-test-loc}))))

(defn- match-negated-condition [zloc]
  (let [head-loc (helpers/child-loc zloc 0)
        head (helpers/head-symbol zloc)
        test-loc (helpers/child-loc zloc 1)]
    (when-let [{:keys [negation-head-loc inner-test-loc]}
               (some-> test-loc negated-test-parts)]
      (case head
        when {:rule-id :when/not-condition
              :message "when with a negated test can be written as when-not"
              :head-loc head-loc
              :head-name "when"
              :replacement-name "when-not"
              :replacement-head "when-not"
              :test-loc test-loc
              :negation-head-loc negation-head-loc
              :inner-test-loc inner-test-loc}
        clojure.core/when {:rule-id :when/not-condition
                           :message "when with a negated test can be written as when-not"
                           :head-loc head-loc
                           :head-name "when"
                           :replacement-name "when-not"
                           :replacement-head "clojure.core/when-not"
                           :test-loc test-loc
                           :negation-head-loc negation-head-loc
                           :inner-test-loc inner-test-loc}
        if {:rule-id :if/not-condition
            :message "if with a negated test can be written as if-not"
            :head-loc head-loc
            :head-name "if"
            :replacement-name "if-not"
            :replacement-head "if-not"
            :test-loc test-loc
            :negation-head-loc negation-head-loc
            :inner-test-loc inner-test-loc}
        nil))))

(defn- certified-match? [context {:keys [head-loc head-name negation-head-loc]}]
  (and (source-core-operator? context head-loc head-name)
       (source-core-var? context negation-head-loc "not")))

(defn- certify-match [match]
  (assoc match
         :replacement-head (str "clojure.core/" (:replacement-name match))
         :safety :syntax-safe
         :tier :certified-fix
         :proof proof-id))

(defn- annotate-match [context zloc match]
  (cond
    (certified-match? context match)
    (certify-match match)

    (locally-shadowed? zloc match)
    (assoc match :skip? true)

    :else
    (assoc match :safety :syntax-safe)))

(defn- rewritten-source [zloc {:keys [replacement-head test-loc inner-test-loc]}]
  (let [outer-source (helpers/node-string zloc)
        head-loc (helpers/child-loc zloc 0)
        head-source (helpers/node-string head-loc)
        test-source (helpers/node-string test-loc)
        inner-source (helpers/node-string inner-test-loc)
        head-index (helpers/find-substring-index outer-source head-source)
        test-index (helpers/find-substring-index outer-source
                                                test-source
                                                (+ head-index (count head-source)))]
    (str (subs outer-source 0 head-index)
         replacement-head
         (subs outer-source (+ head-index (count head-source)) test-index)
         inner-source
         (subs outer-source (+ test-index (count test-source))))))

(def rule
  {:id :condition/negated-form
   :summary "Prefer when-not/if-not over wrapping tests in not"
   :safety :syntax-safe
   :kinds #{:rewrite}
   :check (fn [zloc]
            (boolean (match-negated-condition zloc)))
   :apply (fn [zloc context]
            (when-let [{:keys [rule-id message replacement-head safety tier proof] :as match}
                       (some->> (match-negated-condition zloc)
                                (annotate-match context zloc))]
              (if (:skip? match)
                {:zloc zloc :finding nil}
                (let [before (helpers/node-string zloc)
                      applied? (helpers/autofix-allowed? context zloc safety)
                      updated (if applied?
                                (helpers/replace-with-string zloc
                                                             (rewritten-source zloc match))
                                zloc)
                      after (helpers/node-string updated)]
                  {:zloc updated
                   :finding (finding/make
                             {:rule-id rule-id
                              :message message
                              :safety safety
                              :tier tier
                              :proof proof
                              :severity :warning
                              :source :formsmith
                              :file (:file context)
                              :line (helpers/line zloc)
                              :column (helpers/column zloc)
                              :applied? applied?
                              :kind :rewrite
                              :replacement replacement-head
                              :before before
                              :after after})}))))})
