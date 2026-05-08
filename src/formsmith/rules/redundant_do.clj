(ns formsmith.rules.redundant-do
  (:require [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]))

(def ^:private body-splicing-heads
  '#{let loop binding when when-not when-first when-let
     doseq dotimes locking with-open with-local-vars with-redefs comment})

(defn- body-start-index [head]
  (case head
    (let loop binding when when-not when-first when-let
         doseq dotimes locking with-open with-local-vars with-redefs comment)
    2
    nil))

(defn- redundant-do-parts [zloc]
  (let [head (helpers/head-symbol zloc)
        body-index (body-start-index head)]
    (when (and (helpers/list-form? zloc)
               (contains? body-splicing-heads head)
               body-index)
      (let [children (helpers/child-locs zloc)
            wrapped-do-loc (nth children body-index nil)
            extra-body-loc (nth children (inc body-index) nil)
            do-children (some-> wrapped-do-loc helpers/child-locs)
            do-body-loc (nth do-children 1 nil)]
        (when (and wrapped-do-loc
                   (nil? extra-body-loc)
                   (= 'do (helpers/head-symbol wrapped-do-loc))
                   do-body-loc)
          {:wrapped-do-loc wrapped-do-loc
           :do-body-loc do-body-loc})))))

(defn- rewritten-source [zloc {:keys [wrapped-do-loc do-body-loc]}]
  (let [outer-source (helpers/node-string zloc)
        wrapped-source (helpers/node-string wrapped-do-loc)
        body-source (helpers/node-string do-body-loc)
        body-index (helpers/find-substring-index wrapped-source body-source)
        indent-delta (max 0 (- (helpers/column do-body-loc)
                               (helpers/column wrapped-do-loc)))
        replacement (helpers/outdent-block
                     (subs wrapped-source body-index (dec (count wrapped-source)))
                     indent-delta)
        wrapped-index (helpers/find-substring-index outer-source wrapped-source)]
    (str (subs outer-source 0 wrapped-index)
         replacement
         (subs outer-source (+ wrapped-index (count wrapped-source))))))

(def rule
  {:id :redundant-do/body
   :summary "Flatten redundant do inside body-splicing forms"
   :safety :syntax-safe
   :kinds #{:rewrite}
   :check (fn [zloc]
            (boolean (redundant-do-parts zloc)))
   :apply (fn [zloc context]
            (when-let [{:keys [wrapped-do-loc] :as parts} (redundant-do-parts zloc)]
              (let [before (helpers/node-string zloc)
                    applied? (helpers/autofix-allowed? context zloc :syntax-safe)
                    updated (if applied?
                              (helpers/replace-with-string zloc
                                                           (rewritten-source zloc parts))
                              zloc)
                    after (helpers/node-string updated)]
                {:zloc updated
                 :finding (finding/make
                           {:rule-id :redundant-do/body
                            :message "Redundant do wrapper can be flattened"
                            :safety :syntax-safe
                            :severity :warning
                            :source :formsmith
                            :file (:file context)
                            :line (helpers/line zloc)
                            :column (helpers/column zloc)
                            :applied? applied?
                            :kind :rewrite
                            :before before
                            :after after
                            :replacement (helpers/node-string wrapped-do-loc)})})))})
