(ns formsmith.rules.core-idioms
  (:require [clojure.string :as str]
            [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [rewrite-clj.zip :as z]))

(def thread-first-heads
  '#{assoc clojure.core/assoc
     assoc-in clojure.core/assoc-in
     update clojure.core/update
     update-in clojure.core/update-in
     dissoc clojure.core/dissoc
     conj clojure.core/conj})

(def thread-last-heads
  '#{map clojure.core/map
     filter clojure.core/filter
     remove clojure.core/remove
     keep clojure.core/keep
     mapcat clojure.core/mapcat
     take clojure.core/take
     drop clojure.core/drop
     take-while clojure.core/take-while
     drop-while clojure.core/drop-while
     sort clojure.core/sort
     sort-by clojure.core/sort-by
     reverse clojure.core/reverse
     partition-by clojure.core/partition-by})

(defn- unqualify-core [sym]
  (if (= "clojure.core" (namespace sym))
    (symbol (name sym))
    sym))

(defn- list-sexpr? [form]
  (seq? form))

(defn- thread-first-form? [form]
  (and (list-sexpr? form)
       (contains? thread-first-heads (first form))
       (>= (count form) 2)))

(defn- thread-last-form? [form]
  (and (list-sexpr? form)
       (contains? thread-last-heads (first form))
       (>= (count form) 2)))

(defn- collect-thread-first [form]
  (when (thread-first-form? form)
    (let [head (unqualify-core (first form))
          target (second form)
          step (list* head (drop 2 form))]
      (if-let [{:keys [base steps]} (collect-thread-first target)]
        {:base base
         :steps (conj steps step)}
        {:base target
         :steps [step]}))))

(defn- thread-first-match [zloc]
  (when (and (helpers/list-form? zloc)
             (not (helpers/inside-thread-macro-step? zloc)))
    (let [form (z/sexpr zloc)]
      (when-let [{:keys [base steps]} (collect-thread-first form)]
        (when (>= (count steps) 2)
          {:rule-id :thread/first-chain
           :message "Nested thread-first data updates can be written as a -> pipeline"
           :safety :syntax-safe
           :replacement-source (pr-str (list* '-> base steps))})))))

(defn- collect-thread-last [form]
  (when (thread-last-form? form)
    (let [head (unqualify-core (first form))
          target (last form)
          step (list* head (butlast (rest form)))]
      (if-let [{:keys [base steps]} (collect-thread-last target)]
        {:base base
         :steps (conj steps step)}
        {:base target
         :steps [step]}))))

(defn- thread-last-match [zloc]
  (when (and (helpers/list-form? zloc)
             (not (helpers/inside-thread-macro-step? zloc)))
    (let [form (z/sexpr zloc)]
      (when-let [{:keys [base steps]} (collect-thread-last form)]
        (when (>= (count steps) 2)
          {:rule-id :thread/last-chain
           :message "Nested sequence transformations can be written as a ->> pipeline"
           :safety :syntax-safe
           :replacement-source (pr-str (list* '->> base steps))})))))

(defn- map-call [form]
  (when (and (list-sexpr? form)
             (= 3 (count form)))
    (case (first form)
      map {:f (second form) :coll (nth form 2) :replacement-head 'mapv}
      clojure.core/map {:f (second form) :coll (nth form 2) :replacement-head 'mapv}
      filter {:f (second form) :coll (nth form 2) :replacement-head 'filterv}
      clojure.core/filter {:f (second form) :coll (nth form 2) :replacement-head 'filterv}
      nil)))

(defn- vector-realization-match [zloc]
  (when (helpers/list-form? zloc)
    (let [form (z/sexpr zloc)
          head (first form)]
      (or
       (when (and (= 2 (count form))
                  (contains? '#{vec clojure.core/vec} head))
         (when-let [{:keys [f coll replacement-head]} (map-call (second form))]
           {:rule-id (if (= replacement-head 'mapv)
                       :collection/mapv-over-vec-map
                       :collection/filterv-over-vec-filter)
            :message (if (= replacement-head 'mapv)
                       "vec over map can be written as mapv"
                       "vec over filter can be written as filterv")
            :safety :syntax-safe
            :replacement-source (pr-str (list replacement-head f coll))}))
       (when (and (= 3 (count form))
                  (contains? '#{into clojure.core/into} head)
                  (= [] (second form)))
         (when-let [{:keys [f coll replacement-head]} (map-call (nth form 2))]
           {:rule-id (if (= replacement-head 'mapv)
                       :collection/mapv-over-into-map
                       :collection/filterv-over-into-filter)
            :message (if (= replacement-head 'mapv)
                       "into [] over map can be written as mapv"
                       "into [] over filter can be written as filterv")
            :safety :syntax-safe
            :replacement-source (pr-str (list replacement-head f coll))}))))))

(defn- some?-symbol? [form]
  (contains? '#{some? clojure.core/some?} form))

(defn- keep-over-filter-map-match [zloc]
  (when (helpers/list-form? zloc)
    (let [form (z/sexpr zloc)]
      (when (and (= 3 (count form))
                 (contains? '#{filter clojure.core/filter} (first form))
                 (some?-symbol? (second form)))
        (when-let [{:keys [f coll replacement-head]} (map-call (nth form 2))]
          (when (= 'mapv replacement-head)
            {:rule-id :collection/keep-over-filter-map
             :message "filter some? over map can be written as keep"
             :safety :syntax-safe
             :replacement-source (pr-str (list 'keep f coll))}))))))

(defn- keyword-accessor? [form target key]
  (or (and (list-sexpr? form)
           (= 2 (count form))
           (= key (first form))
           (= target (second form)))
      (and (list-sexpr? form)
           (= 3 (count form))
           (contains? '#{get clojure.core/get} (first form))
           (= target (second form))
           (= key (nth form 2)))))

(defn- get-in-accessor? [form target path]
  (and (list-sexpr? form)
       (= 3 (count form))
       (contains? '#{get-in clojure.core/get-in} (first form))
       (= target (second form))
       (= path (nth form 2))))

(defn- update-function-call [value-form accessor?]
  (when (and (list-sexpr? value-form)
             (= 2 (count value-form))
             (symbol? (first value-form))
             (accessor? (second value-form)))
    (first value-form)))

(defn- update-over-assoc-match [zloc]
  (when (helpers/list-form? zloc)
    (let [form (z/sexpr zloc)
          head (first form)]
      (or
       (when (and (= 4 (count form))
                  (contains? '#{assoc clojure.core/assoc} head)
                  (symbol? (second form))
                  (keyword? (nth form 2)))
         (let [target (second form)
               key (nth form 2)
               value-form (nth form 3)]
           (when-let [f (update-function-call value-form #(keyword-accessor? % target key))]
             {:rule-id :map/update-over-assoc
              :message "assoc of a function applied to the current value can be written as update"
              :safety :syntax-safe
              :replacement-source (pr-str (list 'update target key f))})))
       (when (and (= 4 (count form))
                  (contains? '#{assoc-in clojure.core/assoc-in} head)
                  (symbol? (second form))
                  (vector? (nth form 2)))
         (let [target (second form)
               path (nth form 2)
               value-form (nth form 3)]
           (when-let [f (update-function-call value-form #(get-in-accessor? % target path))]
             {:rule-id :map/update-in-over-assoc-in
              :message "assoc-in of a function applied to the current nested value can be written as update-in"
              :safety :syntax-safe
              :replacement-source (pr-str (list 'update-in target path f))})))))))

(defn- keyword-binding [binding-form value-form]
  (when (symbol? binding-form)
    (let [sym-name (name binding-form)]
      (cond
        (and (list-sexpr? value-form)
             (= 2 (count value-form))
             (keyword? (first value-form))
             (= sym-name (name (first value-form)))
             (symbol? (second value-form)))
        {:map (second value-form)
         :binding binding-form}

        (and (list-sexpr? value-form)
             (= 3 (count value-form))
             (contains? '#{get clojure.core/get} (first value-form))
             (symbol? (second value-form))
             (keyword? (nth value-form 2))
             (= sym-name (name (nth value-form 2))))
        {:map (second value-form)
         :binding binding-form}

        :else nil))))

(defn- all-keyword-bindings [bindings]
  (when (and (vector? bindings)
             (even? (count bindings))
             (pos? (count bindings)))
    (let [pairs (partition 2 bindings)
          matches (mapv (fn [[binding value]]
                          (keyword-binding binding value))
                        pairs)]
      (when (every? some? matches)
        (let [maps (set (map :map matches))]
          (when (= 1 (count maps))
            {:map (first maps)
             :bindings (mapv :binding matches)}))))))

(defn- destructuring-let-match [zloc]
  (when (and (helpers/list-form? zloc)
             (= 'let (helpers/head-symbol zloc)))
    (let [[_ bindings-loc & body-locs] (helpers/child-locs zloc)
          bindings (some-> bindings-loc z/sexpr)]
      (when-let [{map-sym :map bindings :bindings} (all-keyword-bindings bindings)]
        (let [body-source (str/join " " (map helpers/node-string body-locs))
              replacement-source (str "(let [{:keys " (pr-str bindings) "} "
                                      (pr-str map-sym)
                                      "] "
                                      body-source
                                      ")")]
          {:rule-id :binding/assoc-destructure
           :message "Repeated keyword lookups from one map can be written with associative destructuring"
           :safety :syntax-safe
           :replacement-source replacement-source})))))

(defn- def-fn-match [zloc]
  (when (and (helpers/list-form? zloc)
             (= 'def (helpers/head-symbol zloc)))
    (let [[_ name-loc value-loc extra-loc] (helpers/child-locs zloc)
          name-form (some-> name-loc z/sexpr)
          value-form (some-> value-loc z/sexpr)]
      (when (and (nil? extra-loc)
                 (symbol? name-form)
                 (list-sexpr? value-form)
                 (= 'fn (first value-form))
                 (vector? (second value-form)))
        {:rule-id :defn/over-def-fn
         :message "def of an anonymous fn can be written as defn"
         :safety :syntax-safe
         :replacement-source (pr-str (list* 'defn name-form (rest value-form)))}))))

(defn- run-source [f coll]
  (pr-str (list 'run! f coll)))

(defn- lazy-side-effect-match [zloc]
  (when (helpers/list-form? zloc)
    (let [form (z/sexpr zloc)]
      (when (and (= 2 (count form))
                 (contains? '#{doall dorun clojure.core/doall clojure.core/dorun} (first form)))
        (when-let [{:keys [f coll replacement-head]} (map-call (second form))]
          (when (= 'mapv replacement-head)
            {:rule-id :lazy/doall-map-side-effect
             :message "doall/dorun over map often indicates a side-effecting traversal that should be reviewed"
             :safety :unsafe
             :suggested-source (run-source f coll)
             :contract {:blocked-by ["doall returns a realized sequence while run! returns nil, so this is not behavior-preserving in all contexts."]
                        :llm-task "If the mapped function is used only for side effects, rewrite the traversal to run! or doseq and preserve any required return value explicitly."
                        :acceptance ["The rewritten code makes side effects explicit."
                                     "No caller depends on the realized lazy sequence."
                                     "Tests covering the traversal still pass."]}}))))))

(defn- doto-candidate-match [zloc]
  (when (and (helpers/list-form? zloc)
             (= 'let (helpers/head-symbol zloc)))
    (let [[_ bindings-loc & body-locs] (helpers/child-locs zloc)
          bindings (some-> bindings-loc z/sexpr)
          body-forms (mapv z/sexpr body-locs)]
      (when (and (vector? bindings)
                 (= 2 (count bindings))
                 (symbol? (first bindings))
                 (not-empty body-forms)
                 (= (last body-forms) (first bindings)))
        (let [target (first bindings)
              method-calls (butlast body-forms)]
          (when (and (not-empty method-calls)
                     (every? (fn [form]
                               (and (list-sexpr? form)
                                    (symbol? (first form))
                                    (str/starts-with? (name (first form)) ".")
                                    (= target (second form))))
                             method-calls))
            {:rule-id :interop/doto-candidate
             :message "Object construction followed by method calls and returning the object may want doto"
             :safety :unsafe
             :suggested-source "(doto init-expr (.method args...))"
             :contract {:blocked-by ["The tool cannot prove whether intermediate method return values, exceptions, reflection, or local layout should be preserved."]
                        :llm-task "Review whether this let block is only configuring and returning one object. If so, rewrite it to doto."
                        :acceptance ["The constructed object remains the returned value."
                                     "Method call order is unchanged."
                                     "Reflection or type hints remain valid."]}}))))))

(defn- first-match [zloc]
  (or (thread-first-match zloc)
      (vector-realization-match zloc)
      (keep-over-filter-map-match zloc)
      (thread-last-match zloc)
      (update-over-assoc-match zloc)
      (destructuring-let-match zloc)
      (def-fn-match zloc)
      (lazy-side-effect-match zloc)
      (doto-candidate-match zloc)))

(def rule
  {:id :core/idioms
   :summary "Prefer canonical core Clojure idioms for threading, collection realization, map updates, destructuring, defn, and side-effect review"
   :safety :syntax-safe
   :kinds #{:rewrite}
   :check (fn [zloc]
            (and (helpers/list-form? zloc)
                 (boolean (first-match zloc))))
   :apply (fn [zloc context]
            (if-let [{:keys [rule-id message safety replacement-source suggested-source contract]} (first-match zloc)]
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
                            :suggested-source (or replacement-source suggested-source)
                            :contract contract
                            :before before
                            :after after})})
              {:zloc zloc
               :finding nil}))})
