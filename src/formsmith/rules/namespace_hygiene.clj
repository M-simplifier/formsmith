(ns formsmith.rules.namespace-hygiene
  (:require [clojure.string :as str]
            [formsmith.finding :as finding]
            [formsmith.fs :as fs]
            [formsmith.rules.helpers :as helpers]
            [rewrite-clj.zip :as z]))

(def canonical-aliases
  {'clojure.string 'str
   'clojure.set 'set
   'clojure.edn 'edn
   'clojure.walk 'walk
   'clojure.java.io 'io
   'clojure.data.json 'json
   'rewrite-clj.zip 'z
   'cljfmt.core 'cljfmt
   'clojure.test 't
   'reagent.core 'r
   're-frame.core 'rf
   'io.factorhouse.hsx.core 'hsx
   'io.factorhouse.rfx.core 'rfx
   'cljd.flutter 'f
   'integrant.core 'ig
   'malli.core 'm
   'ring.util.response 'response
   'reitit.ring 'ring})

(defn- ns-form? [zloc]
  (and (helpers/list-form? zloc)
       (= 'ns (helpers/head-symbol zloc))))

(defn- option-value [options target]
  (loop [[option value & more] options]
    (cond
      (nil? option) nil
      (= target option) value
      :else (recur more))))

(defn- require-entry [entry]
  (when (vector? entry)
    (let [target (first entry)
          options (rest entry)]
      (when (symbol? target)
        {:target target
         :alias (option-value options :as)
         :refer (option-value options :refer)}))))

(defn- require-clauses [ns-form]
  (->> ns-form
       (drop 2)
       (filter #(and (seq? %) (= :require (first %))))))

(defn- require-entries [ns-form]
  (->> (require-clauses ns-form)
       (mapcat rest)
       (keep require-entry)
       vec))

(defn- use-clauses [ns-form]
  (->> ns-form
       (drop 2)
       (filter #(and (seq? %) (= :use (first %))))))

(defn- bad-alias [{:keys [target alias]}]
  (when-let [expected (canonical-aliases target)]
    (when (and alias (not= expected alias))
      {:target target
       :actual alias
       :expected expected})))

(defn- refer-all? [{:keys [refer]}]
  (= :all refer))

(defn- duplicate-requires [entries]
  (->> entries
       (map :target)
       frequencies
       (filter (fn [[_ n]] (> n 1)))
       (map first)
       vec))

(defn- source-path? [file]
  (let [path (str file)]
    (or (str/starts-with? path "src/")
        (str/starts-with? path "test/")
        (str/includes? path "/src/")
        (str/includes? path "/test/"))))

(defn- ns-path-suffix [ns-sym extension]
  (str (-> (str ns-sym)
           (str/replace "." "/")
           (str/replace "-" "_"))
       extension))

(defn- known-extension [file]
  (some #(when (str/ends-with? (str file) %) %)
        [".clj" ".cljs" ".cljc" ".cljd"]))

(defn- path-mismatch [ns-form context]
  (let [file (:file context)
        ns-sym (second ns-form)]
    (when (and (source-path? file)
               (symbol? ns-sym))
      (when-let [extension (known-extension file)]
        (let [expected (ns-path-suffix ns-sym extension)
              display (fs/display-path file)]
          (when-not (str/ends-with? display expected)
            {:ns ns-sym
             :expected expected
             :actual display}))))))

(defn- namespace-match [zloc context]
  (when (ns-form? zloc)
    (let [ns-form (z/sexpr zloc)
          entries (require-entries ns-form)]
      (or
       (when-let [{:keys [target actual expected]} (some bad-alias entries)]
         {:rule-id :namespace/canonical-alias
          :message (format "Require alias for %s should usually be %s, not %s"
                           target expected actual)
          :suggested-source (format "[%s :as %s]" target expected)
          :contract {:blocked-by ["Renaming an alias requires updating every usage in the namespace."]
                     :llm-task "Rename this require alias to the canonical local alias and update all qualified usages in the file."
                     :acceptance ["The namespace uses the canonical alias."
                                  "All usages compile after the alias rename."
                                  "Formsmith check is clean on the file."]}})
       (when (seq (use-clauses ns-form))
         {:rule-id :namespace/use-clause
          :message "ns :use clauses should be replaced with explicit :require forms"
          :suggested-source "(:require [some.ns :as alias])"
          :contract {:blocked-by ["The imported vars must be mapped to explicit aliases or narrow refer lists."]
                     :llm-task "Replace ns :use clauses with explicit :require aliases or narrow :refer entries."
                     :acceptance ["No :use clause remains."
                                  "Imported names remain available through explicit aliases or refer lists."
                                  "Tests and clj-kondo pass."]}})
       (when-let [{:keys [target]} (some #(when (refer-all? %) %) entries)]
         {:rule-id :namespace/refer-all
          :message (format "Require refer-all from %s should be made explicit" target)
          :suggested-source (format "[%s :as alias]" target)
          :contract {:blocked-by ["The public names actually used from the namespace must be discovered before narrowing the require."]
                     :llm-task "Replace this :refer :all with an explicit alias or a narrow refer list."
                     :acceptance ["No :refer :all remains."
                                  "All previously imported symbols resolve explicitly."
                                  "Tests and clj-kondo pass."]}})
       (when-let [duplicates (seq (duplicate-requires entries))]
         {:rule-id :namespace/duplicate-require
          :message (format "Duplicate require entries should be collapsed: %s"
                           (str/join ", " duplicates))
          :suggested-source "one require entry per namespace"
          :contract {:blocked-by ["Duplicate entries may carry different :as or :refer options that need manual consolidation."]
                     :llm-task "Collapse duplicate require entries while preserving needed aliases and refer lists."
                     :acceptance ["Each required namespace appears once."
                                  "All required aliases and referred symbols still resolve."
                                  "clj-kondo passes."]}})
       (when-let [{:keys [expected actual]} (path-mismatch ns-form context)]
         {:rule-id :namespace/file-path-mismatch
          :message (format "Namespace name should match the source path suffix %s, but file is %s"
                           expected actual)
          :suggested-source expected
          :contract {:blocked-by ["Renaming a namespace or moving a file changes require paths across the project."]
                     :llm-task "Align this namespace name and source file path with the project's Clojure namespace convention."
                     :acceptance ["The namespace name matches the source path."
                                  "All require references are updated."
                                  "Project tests and clj-kondo pass."]}})))))

(def rule
  {:id :namespace/hygiene
   :summary "Emit namespace hygiene contracts for aliases, refer-all/use, duplicate requires, and ns-path drift"
   :safety :unsafe
   :tier :llm-refactor
   :kinds #{:rewrite}
   :check ns-form?
   :apply (fn [zloc context]
            (if-let [{:keys [rule-id message suggested-source contract]} (namespace-match zloc context)]
              {:zloc zloc
               :finding (finding/make
                         {:rule-id rule-id
                          :message message
                          :safety :unsafe
                          :severity :warning
                          :source :formsmith
                          :file (:file context)
                          :line (helpers/line zloc)
                          :column (helpers/column zloc)
                          :applied? false
                          :kind :rewrite
                          :suggested-source suggested-source
                          :contract contract
                          :before (helpers/node-string zloc)
                          :after (helpers/node-string zloc)})}
              {:zloc zloc
               :finding nil}))})
