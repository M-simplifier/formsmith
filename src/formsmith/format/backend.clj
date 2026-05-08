(ns formsmith.format.backend)

(defprotocol FormatterBackend
  (format-source [this source options]))

