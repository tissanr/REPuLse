(ns repulse.env.builtins.ai
  "AI assistant builtins: (ai) and (ai \"prompt\")"
  (:require [repulse.ai.settings :as ai-settings]
            [repulse.ui.assistant-panel :as assistant-panel]
            [repulse.lisp.util :as lisp-util]))

(defn make-builtins [_ctx]
  {"ai"
   (fn
     ([]
      (if @ai-settings/enabled?
        (do (assistant-panel/show-panel!) nil)
        "AI assistant is disabled — enable it in the AI panel settings"))
     ([prompt-text]
      (if @ai-settings/enabled?
        (do (assistant-panel/show-panel!)
            ;; unwrap SourcedVal wrapper that the reader puts around string literals
            (assistant-panel/send! (str (lisp-util/unwrap prompt-text)))
            nil)
        "AI assistant is disabled — enable it in the AI panel settings")))})
