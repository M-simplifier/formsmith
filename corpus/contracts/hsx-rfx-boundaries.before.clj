(ns demo.hsx-rfx
  (:require ["react" :as react]
            [io.factorhouse.hsx.core :as hsx]
            [io.factorhouse.rfx.core :as rfx]))

(defn panel []
  (react/useEffect (fn [] (js/console.log "mounted")))
  (hsx/create-element (fn [] [:div])))

(defn navigate! [event]
  (rfx/dispatch event))
