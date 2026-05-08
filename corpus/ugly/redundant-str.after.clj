(let [execute-one! (fn [& _] :ok)
      ds :ds
      author-id :author-id
      body :body
      now (fn [] :now)]
  (execute-one! ds
                "insert into posts (author_id, body, created_at) values (?, ?, ?)"
                author-id
                body
                (now)))
