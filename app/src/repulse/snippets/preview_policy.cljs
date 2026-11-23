(ns repulse.snippets.preview-policy
  "Phase QA3 B — which built-ins a snippet preview may call. Pure.
   An allow-list instead of a deny-list: a name runs in a preview iff its
   documented side effects (docs/ai/builtins.json via repulse.ai.knowledge) are
   empty, or it is in preview-extra. Everything else — including names the
   metadata does not know — is replaced by a function that throws."
  (:require [repulse.ai.knowledge :as knowledge]))

(def preview-extra
  "Allowed although their metadata lists side effects: overridden by the sandbox,
   restored by sandbox/restore!, or pattern-level only."
  #{"track" "fx" "song" "play-from" "bpm" "stop" "clear!" "mute!" "unmute!" "solo!"
    "samples!" "load-plugin" "unload-plugin" "bus" "share!" "snippet" "demo" "tutorial"
    "load-gist" "master" "defsynth" "out" "bank" "tween" "arrange" "play-scenes"
    "distort" "amp-sim" "waveshape" "cab" "map?" "seq?" "vector?"})

(defn allowed?
  "May a snippet preview call the built-in `name`? (Decision 9)"
  ([name] (allowed? knowledge/builtins name))
  ([builtins name]
   (or (contains? preview-extra name)
       (let [meta (get builtins name)]
         (and (map? meta) (empty? (get meta "side-effects")))))))

(defn denial-message [name]
  (str name " is not available in snippet previews"))
