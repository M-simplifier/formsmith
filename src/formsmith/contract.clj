(ns formsmith.contract
  (:require [clojure.string :as str]
            [formsmith.fs :as fs]))

(defn- keyword-label [value]
  (cond
    (keyword? value)
    (if-let [ns-part (namespace value)]
      (str ns-part "/" (name value))
      (name value))

    (some? value)
    (str value)

    :else
    nil))

(defn- contract-id [{:keys [rule-id file line column]}]
  (str "llm-refactor:"
       (keyword-label rule-id)
       ":"
       file
       ":"
       (or line 1)
       ":"
       (or column 1)))

(defn- llm-refactor-finding? [finding]
  (or (= :llm-refactor (:tier finding))
      (= :unsafe (:safety finding))))

(defn finding->contract [finding]
  (let [file (fs/display-path (:file finding))
        finding (assoc finding :file file)
        custom (:contract finding)]
    {:id (contract-id finding)
     :type "llm-refactor"
     :rule-id (keyword-label (:rule-id finding))
     :message (:message finding)
     :location {:file (:file finding)
                :line (:line finding)
                :column (:column finding)}
     :current-source (:before finding)
     :suggested-source (:suggested-source finding)
     :blocked-by (or (:blocked-by custom)
                     ["The tool cannot prove that the suggested rewrite preserves behavior."])
     :llm-task (or (:llm-task custom)
                   "Inspect the surrounding code and decide whether the canonical shape preserves the intended behavior.")
     :acceptance (or (:acceptance custom)
                     ["Preserve runtime behavior."
                      "Run the affected tests."
                      "Run formsmith check on the affected paths."])
     :evidence {:tier (keyword-label (:tier finding))
                :safety (keyword-label (:safety finding))
                :source (keyword-label (:source finding))}}))

(defn contracts-from-results [results]
  (->> results
       (mapcat :findings)
       (filter llm-refactor-finding?)
       (mapv finding->contract)))

(def source-heavy-contract-keys
  [:current-source :suggested-source])

(defn scrub-contract [contract]
  (apply dissoc contract source-heavy-contract-keys))

(defn summarize [contracts]
  {:contracts (count contracts)})

(defn text-report [contracts summary]
  (str
   (str/join
    "\n\n"
    (for [{:keys [id rule-id location message suggested-source blocked-by llm-task acceptance]} contracts]
      (str
       (format "%s:%s:%s [%s] %s"
               (:file location)
               (or (:line location) 1)
               (or (:column location) 1)
               (keyword-label rule-id)
               message)
       "\n"
       "contract-id=" id
       (when suggested-source
         (str "\nsuggested-source=" suggested-source))
       "\nblocked-by="
       (str/join " | " blocked-by)
       "\nllm-task="
       llm-task
       "\nacceptance="
       (str/join " | " acceptance))))
   (when (seq contracts) "\n")
   (format "contracts=%d" (:contracts summary))))
