(ns formsmith.format.cljfmt
  (:require [cljfmt.core :as cljfmt]
            [formsmith.format.backend :as backend]))

(defrecord CljfmtBackend []
  backend/FormatterBackend
  (format-source [_ source options]
    (cljfmt/reformat-string source (or options {}))))

(def default-backend
  (->CljfmtBackend))

