(ns repulse.ai.system-prompt
  (:require [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.samples :as samples]
            [repulse.session :as session]
            [repulse.ai.settings :as settings]
            [clojure.string :as str]))

(defn build
  "Build the system prompt string. builtins-summary is a compact preloaded string
   (empty string is fine — the prompt degrades gracefully without it)."
  [builtins-summary]
  (let [bpm      (audio/get-bpm)
        state    @audio/scheduler-state
        tracks   (mapv name (keys (:tracks state)))
        muted    (mapv name (:muted state))
        fx-names (mapv :name (filter :active? @fx/chain))
        bank     @samples/active-bank-prefix
        snap     (js/JSON.stringify (clj->js {:bpm bpm :tracks tracks
                                              :muted muted :fx fx-names :bank bank}))
        code-ctx (when @settings/include-code?
                   (str "\n\nEditor code:\n```lisp\n"
                        (if-let [f @session/editor-text-fn] (f) "")
                        "\n```"))]
    (str
      "You are an expert REPuLse-Lisp live coding assistant. "
      "REPuLse is a browser-based live coding instrument where music is written in a minimal Lisp. "
      "Patterns are pure functions of time. "
      "Always return working REPuLse-Lisp code in fenced ```lisp code blocks.\n\n"
      "Key rules:\n"
      "- Use (track :name pattern) for named tracks, not bare patterns\n"
      "- Thread parameters with ->>: (->> pat (amp 0.8) (attack 0.02))\n"
      "- Use (bpm N) to set tempo, (fx :name val) for effects\n"
      "- Rests are :_ not nil or 0\n"
      "- (scale :minor :c4 (seq 0 2 4 7)) maps degree integers to Hz\n"
      "- (euclidean k n :sample) — Bjorklund rhythms\n\n"
      "Current session:\n"
      snap
      (or code-ctx "")
      (when (seq builtins-summary)
        (str "\n\nBuilt-in vocabulary (partial):\n" builtins-summary)))))
