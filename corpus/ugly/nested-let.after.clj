(let [dispatch :dispatch
      state :ready
      submit! (fn [] [dispatch state])]
  [:button {:on-click submit!} "Go"])
