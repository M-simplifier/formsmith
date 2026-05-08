(ns formsmith.rewrite
  (:require [formsmith.rule :as rule]
            [formsmith.rules.registry :as registry]
            [rewrite-clj.zip :as z]))

(defn- next-loc [zloc]
  (or (z/next zloc) zloc))

(defn- apply-rule [zloc rule context]
  (if ((:check rule) zloc)
    (let [result ((:apply rule) zloc context)]
      (update result :finding
              (fn [finding]
                (when finding
                  (rule/validate-finding!
                   (cond-> (assoc finding :tier (rule/finding-tier rule finding))
                     (:proof rule) (assoc :proof (:proof rule))))))))
    {:zloc zloc
     :finding nil}))

(defn- apply-rules-at-loc [zloc rules context]
  (reduce (fn [{:keys [zloc findings]} rule]
            (let [{next-zloc :zloc finding :finding} (apply-rule zloc rule context)]
              {:zloc next-zloc
               :findings (cond-> findings finding (conj finding))}))
          {:zloc zloc
           :findings []}
          rules))

(defn rewrite-string [source context]
  (let [root (z/of-string source {:track-position? true})
        rules (registry/validated-rules)]
    (loop [loc root
           findings []]
      (if (z/end? loc)
        {:source (z/root-string loc)
         :findings findings}
        (let [{updated-loc :zloc new-findings :findings}
              (apply-rules-at-loc loc rules context)]
          (recur (next-loc updated-loc)
                 (into findings new-findings)))))))
