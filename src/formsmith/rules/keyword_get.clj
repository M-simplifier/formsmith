(ns formsmith.rules.keyword-get
  (:require [formsmith.finding :as finding]
            [formsmith.rules.helpers :as helpers]
            [rewrite-clj.zip :as z]))

(defn- get-match [zloc]
  (when (and (helpers/list-form? zloc)
             (not (helpers/inside-thread-macro-step? zloc)))
    (let [head (helpers/head-symbol zloc)
          [_ target-loc key-loc extra-loc] (helpers/child-locs zloc)
          key-form (some-> key-loc z/sexpr)]
      (when (and target-loc
                 key-loc
                 (nil? extra-loc)
                 (keyword? key-form))
        (case head
          get {:rule-id :get/keyword-lookup
               :message "get with a keyword key can be written as keyword invocation"
               :target-loc target-loc
               :key-loc key-loc}
          clojure.core/get {:rule-id :get/keyword-lookup
                            :message "get with a keyword key can be written as keyword invocation"
                            :target-loc target-loc
                            :key-loc key-loc}
          nil)))))

(defn- rewritten-source [{:keys [target-loc key-loc]}]
  (str "(" (helpers/node-string key-loc) " " (helpers/node-string target-loc) ")"))

(def rule
  {:id :get/keyword-lookup
   :summary "Prefer keyword invocation over two-argument get with a keyword key"
   :safety :syntax-safe
   :kinds #{:rewrite}
   :check (fn [zloc]
            (boolean (get-match zloc)))
   :apply (fn [zloc context]
            (when-let [{:keys [rule-id message] :as match} (get-match zloc)]
              (let [before (helpers/node-string zloc)
                    applied? (and (helpers/autofix-allowed? context zloc :syntax-safe)
                                  (not (some-> zloc z/up helpers/comment-sensitive?)))
                    updated (if applied?
                              (helpers/replace-with-string zloc (rewritten-source match))
                              zloc)
                    after (helpers/node-string updated)]
                {:zloc updated
                 :finding (finding/make
                           {:rule-id rule-id
                            :message message
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
