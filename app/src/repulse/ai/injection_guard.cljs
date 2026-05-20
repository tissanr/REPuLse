(ns repulse.ai.injection-guard
  "Wrap externally-sourced content in <untrusted> XML tags before it enters
   the model context. The system prompt instructs the model to treat the
   contents as opaque data, not instructions.")

(defn wrap
  "Wrap a string in <untrusted> tags."
  [s]
  (str "<untrusted>\n" s "\n</untrusted>"))

(defn guard-tool-result
  "Return a copy of result with untrusted fields wrapped.
   tool-kw is the keyword tool name (e.g. :read_buffer)."
  [tool-kw result]
  (case tool-kw
    :read_buffer
    (if (and (:ok result) (:text result))
      (update result :text wrap)
      result)

    :find_snippet
    (if (and (:ok result) (:results result))
      (update result :results
              (fn [rs]
                (mapv (fn [s]
                        (cond-> s
                          (:title s)       (update :title wrap)
                          (:description s) (update :description wrap)))
                      rs)))
      result)

    ;; All other tools return user-owned data — no wrapping needed.
    result))
