(ns formsmith.rules.helpers
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [rewrite-clj.parser :as p]
            [rewrite-clj.zip :as z]))

(defn head-symbol [zloc]
  (when-let [child (z/down zloc)]
    (let [value (z/sexpr child)]
      (when (symbol? value) value))))

(defn child-locs [zloc]
  (loop [child (z/down zloc)
         acc []]
    (if child
      (recur (z/right child) (conj acc child))
      acc)))

(defn child-loc [zloc index]
  (nth (child-locs zloc) index nil))

(defn list-form? [zloc]
  (= :list (z/tag zloc)))

(def thread-macro-heads
  '#{-> ->> some-> some->> cond-> cond->> as-> doto})

(defn node-string [zloc]
  (z/string zloc))

(defn replace-with-string [zloc source]
  (z/replace zloc (p/parse-string source)))

(defn line [zloc]
  (first (z/position zloc)))

(defn column [zloc]
  (second (z/position zloc)))

(defn replace-with-form [zloc form]
  (z/replace zloc (p/parse-string (pr-str form))))

(defn same-loc? [left right]
  (and (= (line left) (line right))
       (= (column left) (column right))))

(defn form-occurs? [form target]
  (boolean
   (some #(= target %)
         (tree-seq coll? seq form))))

(defn replace-form [form target replacement]
  (walk/prewalk #(if (= target %) replacement %) form))

(defn- form-symbols [form]
  (into #{}
        (filter symbol?)
        (tree-seq coll? seq form)))

(defn- keyword-invocation-form? [form]
  (and (seq? form)
       (= 2 (count form))
       (keyword? (first form))
       (symbol? (second form))))

(defn- get-form-keyword [form]
  (when (and (seq? form)
             (= 'get (first form))
             (= 3 (count form))
             (keyword? (nth form 2)))
    (nth form 2)))

(defn- get-in-form-keyword [form]
  (when (and (seq? form)
             (= 'get-in (first form))
             (= 3 (count form))
             (vector? (nth form 2)))
    (let [path (nth form 2)
          last-key (peek path)]
      (when (keyword? last-key)
        last-key))))

(defn seq-bindable-form? [form]
  (or (symbol? form)
      (keyword-invocation-form? form)
      (some? (get-form-keyword form))
      (some? (get-in-form-keyword form))))

(defn seq-binding-symbol [form surrounding-forms]
  (if (symbol? form)
    form
    (when-let [key (or (when (keyword-invocation-form? form)
                         (first form))
                       (get-form-keyword form)
                       (get-in-form-keyword form))]
      (let [base-name (name key)
            taken (into #{}
                        (mapcat form-symbols)
                        surrounding-forms)
            base-symbol (symbol base-name)
            fallback-symbol (symbol (str base-name "-value"))]
        (cond
          (not (contains? taken base-symbol)) base-symbol
          (not (contains? taken fallback-symbol)) fallback-symbol
          :else (loop [n 2]
                  (let [candidate (symbol (str base-name "-value-" n))]
                    (if (contains? taken candidate)
                      (recur (inc n))
                      candidate))))))))

(defn find-substring-index
  ([source fragment]
   (find-substring-index source fragment 0))
  ([source fragment from-index]
   (let [idx (.indexOf source fragment from-index)]
     (when (neg? idx)
       (throw (ex-info "Failed to locate rewrite fragment"
                       {:source source
                        :fragment fragment
                        :from-index from-index})))
     idx)))

(defn strip-delimiters [source]
  (subs source 1 (dec (count source))))

(defn multiline-indent [source]
  (some->> (re-find #"\n([ \t]+)\S" source)
           second))

(defn default-binding-indent [bindings-loc]
  (apply str (repeat (column bindings-loc) " ")))

(defn outdent-block [source spaces]
  (if (pos? spaces)
    (str/replace source
                 #"\n([ \t]+)"
                 (fn [[_ indent]]
                   (let [remaining (subs indent (min spaces (count indent)))]
                     (str "\n" remaining))))
    source))

(defn rewrite-enabled? [context]
  (= :fix (:mode context)))

(def safe-autofix-safeties
  #{:layout-only :syntax-safe})

(def aggressive-autofix-safeties
  (conj safe-autofix-safeties :semantic-pattern))

(defn- allowed-autofix-safeties [context]
  (if (:aggressive? context)
    aggressive-autofix-safeties
    safe-autofix-safeties))

(defn comment-sensitive? [zloc]
  (let [source (z/string zloc)]
    (or (str/includes? source ";")
        (str/includes? source "#_")
        (str/includes? source "#?"))))

(defn metadata-sensitive? [zloc]
  (str/includes? (z/string zloc) "^"))

(defn source-sensitive? [zloc]
  (or (comment-sensitive? zloc)
      (metadata-sensitive? zloc)))

(def reader-sensitive-tags
  #{:uneval :reader-macro :syntax-quote :unquote :unquote-splicing})

(defn sexpr-sensitive? [zloc]
  (contains? reader-sensitive-tags (z/tag zloc)))

(defn sexpr-sensitive-context? [zloc]
  (loop [loc zloc]
    (cond
      (nil? loc) false
      (= :forms (z/tag loc)) false
      (sexpr-sensitive? loc) true
      :else (recur (z/up loc)))))

(defn autofix-allowed? [context zloc safety]
  (and (rewrite-enabled? context)
       (contains? (allowed-autofix-safeties context) safety)
       (not (source-sensitive? zloc))))

(defn parent-head-symbol [zloc]
  (some-> zloc z/up head-symbol))

(defn child-index [parent-loc child-loc]
  (some (fn [[idx loc]]
          (when (same-loc? loc child-loc)
            idx))
        (map-indexed vector (child-locs parent-loc))))

(defn boolean-test-position? [zloc]
  (when-let [parent-loc (z/up zloc)]
    (let [head (head-symbol parent-loc)
          idx (child-index parent-loc zloc)]
      (case head
        if (= 1 idx)
        if-not (= 1 idx)
        when (= 1 idx)
        when-not (= 1 idx)
        cond (and (integer? idx)
                  (pos? idx)
                  (odd? idx))
        cond-> (and (integer? idx)
                    (>= idx 2)
                    (even? idx))
        cond->> (and (integer? idx)
                     (>= idx 2)
                     (even? idx))
        false))))

(defn inside-thread-macro-step? [zloc]
  (contains? thread-macro-heads (parent-head-symbol zloc)))

(defn single-do-body-loc [zloc]
  (when (and (list-form? zloc)
             (= 'do (head-symbol zloc)))
    (let [[_ body-loc extra-loc] (child-locs zloc)]
      (when (and body-loc (nil? extra-loc))
        body-loc))))
