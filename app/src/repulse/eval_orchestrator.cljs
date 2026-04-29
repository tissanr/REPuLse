(ns repulse.eval-orchestrator
  "Evaluation entry point, diagnostics, and live slider patching.
   Responsibility: own evaluate!, set-diagnostics!, pending-mutes, and all
   editor code-patching logic for slider updates.
   Exports: evaluate!, set-diagnostics!, pending-mutes,
            slider-patch-and-eval!, fx-slider-patch-and-eval!,
            per-track-fx-slider-patch-and-eval!"
  (:require [repulse.lisp.core :as lisp]
            [repulse.core :as core]
            [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.env.builtins :as builtins]
            [repulse.ui.editor :as editor]
            [repulse.ui.context-panel :as ctx-panel]
            ["@codemirror/lint" :refer [setDiagnostics]]))

;;; Owned atoms

;; Muted tracks restored from localStorage — applied after the first eval creates tracks.
(defonce pending-mutes (atom #{}))

(defonce ^:private slider-timeout (atom nil))

;;; Callbacks from app.cljs — populated via init! before evaluate! is called.

(defonce ^:private cbs (atom {}))

(defn init!
  "Wire app-level callback fns into this module.
   Must be called during app init before the first evaluation.
   config — {:on-beat-fn f :make-stop-fn-fn f :set-playing!-fn f :set-output!-fn f}"
  [{:keys [on-beat-fn make-stop-fn-fn set-playing!-fn set-output!-fn]}]
  (reset! cbs {:on-beat      on-beat-fn
               :make-stop-fn make-stop-fn-fn
               :set-playing! set-playing!-fn
               :set-output!  set-output!-fn}))

;;; Diagnostics

(defn set-diagnostics!
  "Push a single error diagnostic into the editor, or clear all diagnostics.
   Pass nil for from/to/message to clear."
  [view from to message]
  (let [diags (if (and from to (< from to))
                #js [#js {:from from :to to :severity "error" :message message}]
                #js [])]
    (.dispatch view (setDiagnostics (.-state view) diags))))

;;; Evaluation

(defn evaluate! [code]
  (let [{:keys [on-beat make-stop-fn set-playing! set-output!]} @cbs]
    (builtins/ensure-env!)
    ;; Reset active flags so removed (fx ...) calls disappear from the panel
    (swap! fx/chain (fn [c] (mapv #(assoc % :active? false) c)))
    ;; Clear per-evaluation duplicate-track detector
    (reset! builtins/seen-tracks #{})
    ;; Keep stop fn up to date (in case env was reset)
    (let [env    (assoc @builtins/env-atom "stop" (make-stop-fn))
          result (lisp/eval-string code env)]
      (if (lisp/eval-error? result)
        (do
          (editor/clear-highlights!)
          (when-let [view @editor/editor-view]
            (let [{:keys [from to]} (:source result)]
              (set-diagnostics! view from to (:message result))))
          (set-output! (str "Error: " (:message result)) :error))
        (let [val (:result result)]
          ;; Clear stale diagnostics only when there is a real result.
          ;; A nil val means the command handled output itself (e.g. (upd), (stop))
          ;; and may have already set its own diagnostics — don't clobber them.
          (when (some? val)
            (when-let [view @editor/editor-view]
              (set-diagnostics! view nil nil nil)))
          (cond
            ;; Pattern — start playing (legacy single-pattern mode).
            ;; Uses play-track! on the anonymous :_ track so that :track-fx
            ;; metadata (from per-track fx calls in ->>) is applied correctly.
            ;; audio/start! did not create a track node, so output-for-track
            ;; always fell back to master-gain and FX were silently skipped.
            (core/pattern? val)
            (do
              (audio/stop!)
              (editor/clear-highlights!)
              (audio/play-track! :_ val on-beat editor/highlight-range!)
              (fx/apply-track-effects! :_ (:track-fx val))
              (set-playing! true)
              (set-output! "playing pattern — Alt+Enter to re-evaluate, (stop) to stop" :success))

            ;; Plain event-value map: (saw :c4), (sound :tabla 0), (noise), etc.
            ;; Auto-wrap in pure so users don't need to write (pure (saw :c4)).
            (and (map? val) (or (:note val) (:bank val) (:synth val)))
            (do
              (audio/stop!)
              (editor/clear-highlights!)
              (audio/play-track! :_ (core/pure val) on-beat editor/highlight-range!)
              (set-playing! true)
              (set-output! "playing — Alt+Enter to re-evaluate, (stop) to stop" :success))

            ;; stop fn was called directly — handled inside stop fn
            (nil? val)
            nil

            ;; Pre-formatted string (e.g. sample-banks, track ops) — display as-is
            (string? val)
            (set-output! val :success)

            :else
            (set-output! (str "=> " (pr-str val)) :success))

          ;; ── Legacy :_ track hot-swap ──────────────────────────────────────
          ;; When the last form is not a pattern (e.g. (defsynth lfo ...) follows
          ;; (def bass ...)), the scheduler's :_ track is never updated because
          ;; no play-track! call is made.  After evaluation, if :_ is active and
          ;; exactly one pattern exists in *defs*, update :_ with it so slider
          ;; changes and re-evaluations are reflected immediately.
          (when (and (not (core/pattern? val))
                     (contains? (:tracks @audio/scheduler-state) :_))
            (let [defs-vals (vals @(:*defs* env))
                  pats      (filter core/pattern? defs-vals)]
              (when (= 1 (count pats))
                (let [pat (first pats)]
                  (audio/play-track! :_ pat on-beat editor/highlight-range!)
                  (fx/apply-track-effects! :_ (:track-fx pat))))))

          ;; ── Apply restored mutes after first eval ─────────────────────────
          ;; pending-mutes is populated during session restore; applied once tracks exist.
          (when (seq @pending-mutes)
            (let [tracks (set (keys (:tracks @audio/scheduler-state)))]
              (when (seq tracks)
                (doseq [tk @pending-mutes]
                  (when (contains? tracks tk)
                    (audio/mute-track! tk)))
                (reset! pending-mutes #{})))))))))

;;; Code patching for live slider updates

(defn- patch-param-in-editor!
  "Find the first `(param-name NUMBER` in editor text, starting from the position
   of `:track-name`, and replace the number with new-val."
  [track-name param-name new-val]
  (when-let [view @editor/editor-view]
    (let [doc        (.. view -state -doc (toString))
          track-kw   (str ":" track-name)
          track-pos  (let [p (.indexOf doc track-kw)] (if (>= p 0) p 0))
          sub-doc    (.substring doc track-pos)
          re         (js/RegExp. (str "\\(" param-name "\\s+(-?[0-9]*\\.?[0-9]+)"))
          match      (.exec re sub-doc)]
      (when match
        (let [full  (aget match 0)
              num   (aget match 1)
              start (+ track-pos (.-index match) (- (.-length full) (.-length num)))
              end   (+ start (.-length num))
              fmtd  (if (== new-val (Math/round new-val))
                      (str (int new-val))
                      (.toFixed new-val 2))]
          (.dispatch view #js {:changes #js {:from start :to end :insert fmtd}}))))))

(defn slider-patch-and-eval! [track-name param-name new-val]
  (patch-param-in-editor! track-name param-name new-val)
  (when @slider-timeout (js/clearTimeout @slider-timeout))
  (reset! slider-timeout
    (js/setTimeout
      (fn []
        (reset! slider-timeout nil)
        (when-let [view @editor/editor-view]
          (evaluate! (.. view -state -doc (toString)))))
      150)))

(defn- patch-fx-param-in-editor!
  "Update or insert a param in a (fx :effect-name ...) call.
   Named :param-name NUMBER exists -> replace the number.
   Positional (fx :effect-name NUMBER) for primary param -> replace.
   Not found -> insert :param-name value before the closing )."
  [effect-name param-name new-val]
  (when-let [view @editor/editor-view]
    (let [doc      (.. view -state -doc (toString))
          primary? (= param-name (get ctx-panel/FX-PRIMARY-PARAM effect-name))
          fmtd     (if (== new-val (Math/round new-val))
                     (str (int new-val))
                     (.toFixed new-val 2))
          re-named (js/RegExp. (str "\\(fx\\s+:" effect-name "[^)]*:" param-name
                                    "\\s+(-?[0-9]*\\.?[0-9]+)"))
          match    (.exec re-named doc)
          re-pos   (when (and (not match) primary?)
                     (js/RegExp. (str "\\(fx\\s+:" effect-name "\\s+(-?[0-9]*\\.?[0-9]+)")))
          match    (or match (when re-pos (.exec re-pos doc)))]
      (if match
        (let [full  (aget match 0)
              num   (aget match 1)
              start (+ (.-index match) (- (.-length full) (.-length num)))
              end   (+ start (.-length num))]
          (.dispatch view #js {:changes #js {:from start :to end :insert fmtd}}))
        (let [re-fx    (js/RegExp. (str "\\(fx\\s+:" effect-name "[^)]*\\)"))
              fx-match (.exec re-fx doc)]
          (when fx-match
            (let [close-pos (+ (.-index fx-match)
                               (dec (.-length (aget fx-match 0))))]
              (.dispatch view #js {:changes #js {:from close-pos
                                                 :to   close-pos
                                                 :insert (str " :" param-name " " fmtd)}}))))))))

(defn fx-slider-patch-and-eval! [effect-name param-name new-val]
  (fx/set-param! effect-name param-name new-val)
  (patch-fx-param-in-editor! effect-name param-name new-val)
  (when @slider-timeout (js/clearTimeout @slider-timeout))
  (reset! slider-timeout
    (js/setTimeout
      (fn []
        (reset! slider-timeout nil)
        (when-let [view @editor/editor-view]
          (evaluate! (.. view -state -doc (toString)))))
      150)))

(defn- patch-per-track-fx-param-in-editor!
  "Update or insert a param in (fx :effect-name ...) scoped to (track :track-name ...)."
  [track-name effect-name param-name new-val]
  (when-let [view @editor/editor-view]
    (let [doc         (.. view -state -doc (toString))
          track-kw    (str "(track :" track-name)
          track-start (.indexOf doc track-kw)
          ;; Scope: from track-start to next (track : or end of doc
          next-start  (let [p (.indexOf doc "(track :" (inc track-start))]
                        (if (>= p 0) p (.-length doc)))
          sub-doc     (when (>= track-start 0) (.substring doc track-start next-start))
          primary?    (= param-name (get ctx-panel/FX-PRIMARY-PARAM effect-name))
          fmtd        (if (== new-val (Math/round new-val))
                        (str (int new-val))
                        (.toFixed new-val 2))]
      (when sub-doc
        (let [re-named (js/RegExp. (str "\\(fx\\s+:" effect-name "[^)]*:" param-name
                                         "\\s+(-?[0-9]*\\.?[0-9]+)"))
              match    (.exec re-named sub-doc)
              re-pos   (when (and (not match) primary?)
                         (js/RegExp. (str "\\(fx\\s+:" effect-name "\\s+(-?[0-9]*\\.?[0-9]+)")))
              match    (or match (when re-pos (.exec re-pos sub-doc)))]
          (if match
            (let [full  (aget match 0)
                  num   (aget match 1)
                  start (+ track-start (.-index match) (- (.-length full) (.-length num)))
                  end   (+ start (.-length num))]
              (.dispatch view #js {:changes #js {:from start :to end :insert fmtd}}))
            (let [re-fx    (js/RegExp. (str "\\(fx\\s+:" effect-name "[^)]*\\)"))
                  fx-match (.exec re-fx sub-doc)]
              (when fx-match
                (let [close-pos (+ track-start (.-index fx-match)
                                   (dec (.-length (aget fx-match 0))))]
                  (.dispatch view #js {:changes #js {:from close-pos
                                                     :to   close-pos
                                                     :insert (str " :" param-name " " fmtd)}}))))))))))

(defn per-track-fx-slider-patch-and-eval! [track-name effect-name param-name new-val]
  (fx/set-track-param! (keyword track-name) effect-name param-name new-val)
  (patch-per-track-fx-param-in-editor! track-name effect-name param-name new-val)
  (when @slider-timeout (js/clearTimeout @slider-timeout))
  (reset! slider-timeout
    (js/setTimeout
      (fn []
        (reset! slider-timeout nil)
        (when-let [view @editor/editor-view]
          (evaluate! (.. view -state -doc (toString)))))
      150)))
