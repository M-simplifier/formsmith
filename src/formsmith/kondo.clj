(ns formsmith.kondo
  (:require [clj-kondo.core :as clj-kondo]
            [formsmith.finding :as finding]))

(defn lint-paths [paths]
  (clj-kondo/run! {:lint paths}))

(defn analyze-paths [paths]
  (clj-kondo/run! {:lint paths
                   :config {:output {:analysis {:locals true}}}}))

(defn normalize-findings [report]
  (mapv (fn [{:keys [type level filename row col message]}]
          (finding/make
           {:rule-id (keyword "kondo" (name type))
            :message message
            :severity level
            :source :clj-kondo
            :file filename
            :line row
            :column col
            :applied? false
            :kind :diagnostic}))
        (:findings report)))

(defn findings-for-paths [paths]
  (normalize-findings (lint-paths paths)))
