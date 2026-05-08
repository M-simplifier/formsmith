(ns formsmith.report
  (:require [clojure.data.json :as json]
            [clojure.string :as str]))

(defn- rule-label [{:keys [rule-id]}]
  (cond
    (keyword? rule-id)
    (if-let [ns-part (namespace rule-id)]
      (str ns-part "/" (name rule-id))
      (name rule-id))

    (some? rule-id)
    (str rule-id)

    :else
    "unknown"))

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

(defn text-report [results summary]
  (str
   (str/join
    "\n"
    (for [{:keys [file findings]} results
          finding findings]
      (format "%s:%s:%s [%s] %s"
              file
              (or (:line finding) 1)
              (or (:column finding) 1)
              (rule-label finding)
              (str (:message finding)
                   (when-let [tier (:tier finding)]
                     (str " tier=" (name tier)))
                   (when-let [proof (:proof finding)]
                     (str " proof=" (keyword-label proof)))))))
   (when (seq results) "\n")
   (format "files=%d changed=%d findings=%d"
           (:files summary)
           (:changed summary)
           (:findings summary))))

(defn json-report [results summary]
  (json/write-str {:results results :summary summary}))

(defn data-json-report [data]
  (json/write-str data))

(defn rules-report [rules]
  (str/join
   "\n"
   (for [{:keys [id safety tier proof summary]} rules]
     (format "%-24s %-16s %-24s %-28s %s"
             (rule-label {:rule-id id})
             (name safety)
             (name tier)
             (or (keyword-label proof) "-")
             summary))))

(defn explain-report [{:keys [id summary safety tier proof kinds]}]
  (str/join
   "\n"
   [(str "Rule:    " (rule-label {:rule-id id}))
    (str "Safety:  " (name safety))
    (str "Tier:    " (name tier))
    (str "Proof:   " (or (keyword-label proof) "-"))
    (str "Kinds:   " (str/join ", " (map name kinds)))
    (str "Summary: " summary)]))

(defn frameworks-report [frameworks]
  (str
   (str/join
    "\n\n"
    (for [{:keys [id label category evidence canonical-guidance]} frameworks]
      (str
       (format "%s (%s) category=%s evidence=%d"
               label
               id
               category
               (count evidence))
       "\n"
       "Evidence:"
       (apply str
              (for [{:keys [from to alias file line column]} evidence]
                (format "\n- %s -> %s%s %s:%s:%s"
                        from
                        to
                        (if alias (str " as " alias) "")
                        file
                        (or line 1)
                        (or column 1))))
       "\nCanonical guidance:"
       (apply str
              (for [guidance canonical-guidance]
                (str "\n- " guidance))))))
   (when (seq frameworks) "\n")
   (format "frameworks=%d" (count frameworks))))

(defn analysis-report [{:keys [summary namespaces namespace-deps frameworks]}]
  (str/join
   "\n"
   (concat
    [(format "namespaces=%d namespace-deps=%d vars=%d var-usages=%d locals=%d local-usages=%d frameworks=%d"
             (:namespaces summary)
             (:namespace-deps summary)
             (:vars summary)
             (:var-usages summary)
             (:locals summary)
             (:local-usages summary)
             (:frameworks summary))]
    [""
     "Namespaces:"]
    (for [{:keys [name file]} namespaces]
      (format "- %s %s" name file))
    [""
     "Namespace deps:"]
    (for [{:keys [from to alias]} namespace-deps]
      (format "- %s -> %s%s"
              from
              to
              (if alias
                (str " as " alias)
                "")))
    [""
     "Framework profiles:"]
    (for [{:keys [id label category]} frameworks]
      (format "- %s (%s) category=%s" label id category)))))
