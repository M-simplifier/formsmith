(let [dispatch :dispatch
      state :ready]
(let [submit! (fn [] [dispatch state])]
[:button {:on-click submit!} "Go"]))
