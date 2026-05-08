(ns formsmith.finding)

(defn make
  [{:keys [rule-id message safety tier proof severity source file line column applied? kind replacement before after suggested-source contract]}]
  {:rule-id rule-id
   :message message
   :safety safety
   :tier tier
   :proof proof
   :severity severity
   :source source
   :file file
   :line line
   :column column
   :applied? (boolean applied?)
   :kind kind
   :replacement replacement
   :before before
   :after after
   :suggested-source suggested-source
   :contract contract})
