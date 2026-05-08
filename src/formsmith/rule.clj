(ns formsmith.rule
  (:require [formsmith.proof :as proof]))

(def safe-safety-levels
  #{:layout-only :syntax-safe :semantic-pattern :unsafe})

(def valid-tiers
  #{:certified-fix
    :standard-canonical-fix
    :analyzer-guarded-fix
    :conservative-fix
    :llm-refactor})

(defn tier-for-safety [safety]
  (case safety
    :layout-only :standard-canonical-fix
    :syntax-safe :standard-canonical-fix
    :semantic-pattern :analyzer-guarded-fix
    :unsafe :llm-refactor
    nil))

(defn finding-tier [rule finding]
  (or (:tier finding)
      (:tier rule)
      (tier-for-safety (:safety finding))
      (tier-for-safety (:safety rule))))

(defn certified-proof-valid? [{:keys [tier proof]}]
  (and (or (nil? proof) (proof/known-proof? proof))
       (or (not= :certified-fix tier)
           (proof/known-proof? proof))))

(defn validate-finding! [finding]
  (when-not (certified-proof-valid? finding)
    (throw (ex-info "Invalid certified finding proof" {:finding finding})))
  finding)

(defn valid-rule? [{:keys [id summary safety tier proof check apply]}]
  (and (keyword? id)
       (string? summary)
       (contains? safe-safety-levels safety)
       (or (nil? tier) (contains? valid-tiers tier))
       (or (nil? proof) (keyword? proof))
       (certified-proof-valid? {:tier tier :proof proof})
       (ifn? check)
       (ifn? apply)))
