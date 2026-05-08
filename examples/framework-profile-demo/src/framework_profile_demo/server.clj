(ns framework-profile-demo.server
  (:require [integrant.core :as ig]
            [malli.core :as m]
            [reitit.ring :as ring]
            [ring.util.response :as response]))

(def schema
  [:map [:id string?]])

(defmethod ig/init-key ::handler [_ _]
  (ring/ring-handler
   (ring/router
    [["/health"
      {:get (fn [_]
              (response/response {:ok (m/validate schema {:id "health"})}))}]])))
