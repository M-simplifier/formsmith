(ns formsmith.rules.nested-let
  (:require [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [clojure.string :as str]
            [rewrite-clj.zip :as z]))

(defn- nested-let-parts [zloc]
  (when (and (helpers/list-form? zloc)
             (= 'let (helpers/head-symbol zloc)))
    (let [outer-bindings-loc (some-> zloc z/down z/right)
          inner-let-loc (some-> outer-bindings-loc z/right)]
      (when (and (= :vector (z/tag outer-bindings-loc))
                 inner-let-loc
                 (nil? (z/right inner-let-loc))
                 (helpers/list-form? inner-let-loc)
                 (= 'let (helpers/head-symbol inner-let-loc)))
        (let [inner-bindings-loc (some-> inner-let-loc z/down z/right)
              first-body-loc (some-> inner-bindings-loc z/right)]
          (when (and (= :vector (z/tag inner-bindings-loc))
                     first-body-loc)
            {:outer-bindings-loc outer-bindings-loc
             :inner-let-loc inner-let-loc
             :inner-bindings-loc inner-bindings-loc}))))))

(defn- find-substring-index [source fragment]
  (let [idx (.indexOf source fragment)]
    (when (neg? idx)
      (throw (ex-info "Failed to locate rewrite fragment"
                      {:source source
                       :fragment fragment})))
    idx))

(defn- indent-delta [zloc {:keys [inner-let-loc]}]
  (max 0 (- (helpers/column inner-let-loc)
            (helpers/column zloc))))

(defn- combined-bindings-source [zloc {:keys [outer-bindings-loc inner-bindings-loc] :as parts}]
  (let [outer-bindings (helpers/node-string outer-bindings-loc)
        inner-bindings (helpers/node-string inner-bindings-loc)
        indent-delta (indent-delta zloc parts)
        outer-inner (helpers/strip-delimiters outer-bindings)
        inner-inner (helpers/outdent-block
                     (helpers/strip-delimiters inner-bindings)
                     indent-delta)
        multiline? (or (str/includes? outer-bindings "\n")
                       (str/includes? inner-bindings "\n"))
        binding-indent (or (helpers/multiline-indent outer-bindings)
                           (some-> (helpers/multiline-indent inner-bindings)
                                   (helpers/outdent-block indent-delta))
                           (helpers/default-binding-indent outer-bindings-loc))
        separator (if multiline?
                    (str "\n" binding-indent)
                    " ")]
    (cond
      (str/blank? outer-inner) (str "[" inner-inner "]")
      (str/blank? inner-inner) outer-bindings
      :else (str "[" outer-inner separator inner-inner "]"))))

(defn- inner-body-source [zloc {:keys [inner-bindings-loc inner-let-loc] :as parts}]
  (let [inner-let (helpers/node-string inner-let-loc)
        inner-bindings (helpers/node-string inner-bindings-loc)
        start (+ (find-substring-index inner-let inner-bindings)
                 (count inner-bindings))
        body (subs inner-let start (dec (count inner-let)))
        indent-delta (indent-delta zloc parts)]
    (helpers/outdent-block body indent-delta)))

(defn- rewritten-source [zloc parts]
  (let [outer-source (helpers/node-string zloc)
        outer-bindings (helpers/node-string (:outer-bindings-loc parts))
        inner-let (helpers/node-string (:inner-let-loc parts))
        prefix (subs outer-source 0 (find-substring-index outer-source outer-bindings))
        suffix-start (+ (find-substring-index outer-source inner-let)
                        (count inner-let))
        suffix (subs outer-source suffix-start)]
    (str prefix
         (combined-bindings-source zloc parts)
         (inner-body-source zloc parts)
         suffix)))

(def rule
  {:id :let/nested-let
   :summary "Flatten nested let forms into a single binding block"
   :safety :syntax-safe
   :kinds #{:rewrite}
   :check (fn [zloc]
            (boolean (nested-let-parts zloc)))
   :apply (fn [zloc context]
            (when-let [parts (nested-let-parts zloc)]
              (let [before (helpers/node-string zloc)
                    applied? (helpers/autofix-allowed? context zloc :syntax-safe)
                    updated (if applied?
                              (helpers/replace-with-string zloc
                                                           (rewritten-source zloc parts))
                              zloc)
                    after (helpers/node-string updated)]
                {:zloc updated
                 :finding (finding/make
                           {:rule-id :let/nested-let
                            :message "Nested let can be flattened into a single let"
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
