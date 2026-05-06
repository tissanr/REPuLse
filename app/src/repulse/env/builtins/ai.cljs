(ns repulse.env.builtins.ai
  "AI assistant builtins: (ai) and (ai \"prompt\")"
  (:require [repulse.ai.settings :as ai-settings]
            [repulse.ui.assistant-panel :as assistant-panel]))

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
            (assistant-panel/send! (str prompt-text))
            nil)
        "AI assistant is disabled — enable it in the AI panel settings")))})
