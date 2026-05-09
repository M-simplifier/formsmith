(ns demo.backend-boundaries
  (:require [integrant.core :as ig]
            [malli.core :as m]
            [reitit.ring :as ring]
            [ring.util.response :as response]))

(def user-schema
  [:map [:id int?] [:name string?] [:email string?]])

(m/=> #'create-user [:=> [:cat :map] :map])

(def registry
  {:registry {::user [:map [:friend [:ref ::user]]]}})

(def route-data
  {:middleware [wrap-auth]
   :coercion coercion
   :parameters {:body [:map [:id int?]]}})

(def route-data-with-inherited-coercion
  {:parameters {:body [:map [:id int?]]}})

(def routes
  [["/health" {:get (fn [_] {:status 200 :body "ok"})}]])

(defn handler [_]
  {:status 200 :body "ok"})

(defn wrap-auth [handler]
  (fn [request]
    (handler request)))

(defn user-handler [request]
  (m/validate [:map [:id int?]] (:body request))
  {:status 200 :body "ok"})

(def config {:app/server {:db (ig/ref :app/db)}})

(defmethod ig/halt-key! :db [_ db]
  (stop-db db))

(defmethod ig/init-key :db [_ config]
  (start-db config))

(defmethod ig/init-key ::cache [_ config]
  (start-cache config))

(defmethod ig/expand-key ::module [_ opts]
  {:app/db opts})
