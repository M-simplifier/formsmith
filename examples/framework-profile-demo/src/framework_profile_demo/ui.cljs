(ns framework-profile-demo.ui
  (:require [re-frame.core :as rf]
            [reagent.core :as r]))

(defn panel []
  [:section
   [:button {:on-click #(rf/dispatch [:save])}
    (r/as-element [:span "Save"])]])
