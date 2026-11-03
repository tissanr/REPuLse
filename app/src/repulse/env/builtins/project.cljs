(ns repulse.env.builtins.project
  "Multi-buffer project builtins (Phase SONG4): use."
  (:require [repulse.project-eval :as project-eval]))

(defn make-builtins [_ctx]
  {"use" project-eval/use-builtin})
