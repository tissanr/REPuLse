(ns repulse.env.builtins
  "Lisp environment assembly — creates and caches the Lisp env atom with all
   app-layer built-in functions.
   Responsibility: own env-atom, builtin-names, and seen-tracks; assemble the full
   Lisp environment on first call to ensure-env!.
   Exports: env-atom, builtin-names, seen-tracks, evaluate-ref, ensure-env!, init!."
  (:require [repulse.lisp.eval :as leval]
            [repulse.audio :as audio]
            [repulse.synth :as synth]
            [repulse.samples :as samples]
            [repulse.fx :as fx]
            [repulse.env.builtins.tracks :as b-tracks]
            [repulse.env.builtins.fx :as b-fx]
            [repulse.env.builtins.samples :as b-samples]
            [repulse.env.builtins.midi :as b-midi]
            [repulse.env.builtins.content :as b-content]
            [repulse.env.builtins.export :as b-export]
            [repulse.env.builtins.session :as b-session]
            [repulse.env.builtins.routing :as b-routing]))

;;; Owned atoms

;; The main Lisp environment — created once, reused across evaluations.
(defonce env-atom (atom nil))

;; Keys present in the initial env — used to filter built-ins from user defs.
(defonce builtin-names (atom #{}))

;; Tracks which track names have been defined in the current evaluation pass.
;; Reset by evaluate! before each eval; checked by the `track` builtin to
;; detect duplicate track names in the same buffer.
(defonce seen-tracks (atom #{}))

;;; Lazy reference to evaluate! — set by app.cljs after eval-orchestrator loads.
;; This breaks the ensure-env! ↔ evaluate! circular dependency:
;; builtins that call evaluate (demo, load-gist) deref this atom at call time,
;; not at construction time — so the fn is always bound when actually needed.
(defonce evaluate-ref (atom nil))

;;; Callbacks from app.cljs — populated via init! before ensure-env! is called.
(defonce ^:private cbs (atom {}))

(defn init!
  "Wire app-level callback fns into this module.
   Must be called before ensure-env!.
   config — {:on-beat-fn f :set-playing!-fn f :set-output!-fn f :make-stop-fn-fn f :share!-fn f}"
  [{:keys [on-beat-fn set-playing!-fn set-output!-fn make-stop-fn-fn share!-fn]}]
  (reset! cbs {:on-beat       on-beat-fn
               :set-playing!  set-playing!-fn
               :set-output!   set-output!-fn
               :make-stop-fn  make-stop-fn-fn
               :share!        share!-fn}))

;;; Environment construction

(defn ensure-env! []
  (when (nil? @env-atom)
    (let [{:keys [on-beat set-playing! set-output! make-stop-fn share!]} @cbs
          ctx {:on-beat      on-beat
               :set-playing! set-playing!
               :set-output!  set-output!
               :make-stop-fn make-stop-fn
               :share!       share!
               :env-atom     env-atom
               :evaluate-ref evaluate-ref
               :seen-tracks  seen-tracks}]
      (samples/init!)
      (reset! env-atom
              (-> (leval/make-env (make-stop-fn) audio/set-bpm!)
                  (assoc :*register-synth-fn* synth/register-synth!)
                  (merge (b-tracks/make-builtins ctx))
                  (merge (b-fx/make-builtins ctx))
                  (merge (b-samples/make-builtins ctx))
                  (merge (b-midi/make-builtins ctx))
                  (merge (b-content/make-builtins ctx))
                  (merge (b-export/make-builtins ctx))
                  (merge (b-session/make-builtins ctx))
                  (merge (b-routing/make-builtins ctx))))
      ;; Wire the FX event notification callback (used by sidechain plugin)
      (swap! audio/scheduler-state assoc :on-fx-event fx/notify-fx-event!)
      ;; Snapshot built-in names so render-context-panel! can filter them out
      (reset! builtin-names (set (keys @env-atom))))))
