(ns framework-profile-demo.modern
  (:require [io.factorhouse.hsx.core :as hsx]
            [io.factorhouse.rfx.core :as rfx]
            [reagent.core :as r]))

(defn counter []
  (let [n (r/atom 0)]
    [:button {:on-click #(swap! n inc)}
     @n]))

(defn websocket-message! [event]
  (rfx/dispatch event))

(defn mount! [root]
  (.render root (hsx/create-element [counter])))
