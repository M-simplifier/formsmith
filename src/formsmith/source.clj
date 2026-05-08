(ns formsmith.source
  (:require [formsmith.fs :as fs]
            [rewrite-clj.zip :as z]))

(defn- head-symbol [zloc]
  (when-let [child (z/down zloc)]
    (let [value (z/sexpr child)]
      (when (symbol? value)
        value))))

(defn- ns-loc [source]
  (let [root (z/of-string source {:track-position? true})]
    (loop [loc root]
      (cond
        (z/end? loc) nil
        (and (= :list (z/tag loc))
             (= 'ns (head-symbol loc))) loc
        :else (recur (or (z/next loc) loc))))))

(defn- option-value [options target]
  (loop [[option value & more] options]
    (cond
      (nil? option) nil
      (= target option) value
      :else (recur more))))

(defn- require-entry->dep [from file line column entry]
  (when (vector? entry)
    (let [target (first entry)
          options (rest entry)]
      (when target
        {:from from
         :to target
         :alias (option-value options :as)
         :file file
         :line line
         :column column
         :source :source-scan}))))

(defn namespace-deps-from-source [file source]
  (when-let [loc (ns-loc source)]
    (let [form (z/sexpr loc)
          from (second form)
          [line column] (z/position loc)]
      (->> (drop 2 form)
           (filter #(and (seq? %) (= :require (first %))))
           (mapcat rest)
           (keep #(require-entry->dep from file line column %))
           vec))))

(defn namespace-deps-from-file [file]
  (namespace-deps-from-source file (slurp file)))

(defn namespace-deps-from-paths [paths]
  (->> (fs/discover-targets paths :lint)
       (mapcat namespace-deps-from-file)
       distinct
       vec))

(defn alias-for [source target]
  (->> (namespace-deps-from-source nil source)
       (some (fn [{:keys [to alias]}]
               (when (= target to)
                 alias)))))

(defn required? [source target]
  (boolean
   (some #(= target (:to %))
         (namespace-deps-from-source nil source))))
