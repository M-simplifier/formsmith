(ns demo.cljs-framework
  (:require [re-frame.core :as rf]
            [reagent.core :as r]))

(defn view []
  [:div {:class "panel panel-active"} "ok"])

(defn counter []
  (let [n (r/atom 0)]
    [:button @n]))

(defn panel [title]
  (fn []
    [:section title]))

(rf/reg-event-db
 :save
 (fn [db [_ value]]
   (rf/dispatch [:saved value])
   db))

(rf/reg-sub
 :visible
 (fn [_] [:items])
 (fn [items _] items))
