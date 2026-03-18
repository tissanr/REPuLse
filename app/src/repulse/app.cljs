(ns repulse.app
  (:require [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.core :as core]
            [repulse.audio :as audio]
            [repulse.samples :as samples]
            [repulse.plugins :as plugins]
            [repulse.fx :as fx]
            [repulse.plugins.compressor :as compressor-plugin]
            [clojure.string :as cstr]
            ["@codemirror/view" :refer [EditorView Decoration keymap lineNumbers]]
            ["@codemirror/state" :refer [EditorState StateEffect StateField]]
            ["@codemirror/commands" :refer [defaultKeymap historyKeymap history selectAll]]
            ["@codemirror/language" :refer [bracketMatching]]
            ["@codemirror/theme-one-dark" :refer [oneDark]]
            ["./lisp-lang/index.js" :refer [lispLanguage]]
            ["./lisp-lang/providers.js" :refer [setBankNamesProvider setFxNamesProvider]]))

;;; DOM helpers

(defn el [id] (.getElementById js/document id))

(defn set-output! [msg status]
  (when-let [e (el "output")]
    (set! (.-textContent e) msg)
    (set! (.-className e) (str "output " (name status)))))

(defn set-playing! [playing?]
  (when-let [dot (el "playing-dot")]
    (if playing?
      (.add (.-classList dot) "active")
      (.remove (.-classList dot) "active")))
  (when-let [btn (el "play-btn")]
    (set! (.-textContent btn) (if playing? "■ stop" "▶ play"))
    (if playing?
      (.add (.-classList btn) "active")
      (.remove (.-classList btn) "active"))))

(defn on-beat []
  (when-let [dot (el "playing-dot")]
    (.add (.-classList dot) "flash")
    (js/setTimeout #(.remove (.-classList dot) "flash") 80)))

;;; Active code highlighting — CodeMirror 6 decoration infrastructure

;; A StateEffect that replaces the entire active-highlights DecorationSet.
(def set-highlights-effect (.define StateEffect))

;; A StateField that holds the current DecorationSet of active event highlights.
(def highlights-field
  (.define StateField
    #js {:create   (fn [_] (.-none Decoration))
         :update   (fn [decs tr]
                     (let [mapped (.map decs (.-changes tr))]
                       (if-let [eff (some #(when (.is % set-highlights-effect) %)
                                          (array-seq (.-effects tr)))]
                         (.-value eff)
                         mapped)))
         :provide  (fn [f] (.from EditorView.decorations f))}))

;; The CSS class applied to active tokens.
(def active-mark (.mark Decoration #js {:class "active-event"}))

;; Atom holding currently active source ranges: [{:from N :to N} ...]
(defonce active-ranges (atom []))

(defonce editor-view (atom nil))
(defonce cmd-view    (atom nil))

(defn- rebuild-decorations! [view]
  (let [ranges  (sort-by :from @active-ranges)
        range-objs (keep (fn [{:keys [from to]}]
                           (try (.range active-mark from to)
                                (catch :default _ nil)))
                         ranges)
        deco-set (if (seq range-objs)
                   (.set Decoration (clj->js range-objs))
                   (.-none Decoration))]
    (.dispatch view #js {:effects #js [(.of set-highlights-effect deco-set)]})))

(defn highlight-range! [{:keys [from to]}]
  (when-let [view @editor-view]
    (let [doc-len (.. view -state -doc -length)
          from'   (min from doc-len)
          to'     (min to   doc-len)]
      (when (< from' to')
        (swap! active-ranges conj {:from from' :to to'})
        (rebuild-decorations! view)
        ;; Remove this range after 120 ms
        (js/setTimeout
          (fn []
            (swap! active-ranges
                   (fn [rs]
                     (filterv #(not (and (= (:from %) from') (= (:to %) to'))) rs)))
            (when-let [v @editor-view]
              (rebuild-decorations! v)))
          120)))))

(defn clear-highlights! []
  (reset! active-ranges [])
  (when-let [view @editor-view]
    (rebuild-decorations! view)))

;;; Track timeline rendering

(defn- render-track-row [track-name pattern cycle muted?]
  (let [sp   {:start [cycle 1] :end [(inc cycle) 1]}
        evs  (try (core/query pattern sp) (catch :default _ []))
        bars (mapv (fn [ev]
                     (let [part (:part ev)
                           pos  (- (core/rat->float (:start part)) cycle)
                           dur  (- (core/rat->float (:end part))
                                   (core/rat->float (:start part)))]
                       {:pos pos :dur (max 0.01 dur)}))
                   evs)
        kw-name (name track-name)]
    (str "<div class=\"track-row" (when muted? " track-muted") "\">"
         "<span class=\"track-name\" onclick=\"window._repulseMuteToggle('" kw-name "')\">"
         kw-name
         "</span>"
         "<div class=\"track-timeline-wrap\">"
         "<svg class=\"track-timeline\" viewBox=\"0 0 100 10\" preserveAspectRatio=\"none\">"
         (apply str (map (fn [{:keys [pos dur]}]
                           (str "<rect x=\"" (* pos 100) "\" y=\"0.5\" "
                                "width=\"" (* dur 100) "\" height=\"9\" "
                                "rx=\"0.5\"/>"))
                         bars))
         "</svg>"
         "<div class=\"track-playhead\" id=\"ph-" kw-name "\"></div>"
         "</div>"
         "</div>")))

(defn- render-track-panel! []
  (when-let [panel (el "track-panel")]
    (let [{:keys [tracks muted playing? cycle]} @audio/scheduler-state]
      (if (or (not playing?) (empty? tracks))
        (set! (.-innerHTML panel) "")
        (set! (.-innerHTML panel)
              (apply str (map (fn [[track-name pattern]]
                                (render-track-row track-name pattern cycle
                                                  (contains? muted track-name)))
                              tracks)))))))

;;; Playhead RAF loop

(defn- start-playhead-raf! []
  (letfn [(frame []
    (let [ac    (when (audio/playing?) @audio/ctx)
          state @audio/scheduler-state]
      (when ac
        (let [now      (.-currentTime ac)
              cyc-dur  (:cycle-dur state)
              frac     (mod (/ now cyc-dur) 1.0)
              pct      (str (* frac 100) "%")]
          (doseq [ph (array-seq (.querySelectorAll js/document ".track-playhead"))]
            (set! (.. ph -style -left) pct)))))
    (js/requestAnimationFrame frame))]
    (js/requestAnimationFrame frame)))

;;; Session persistence

(defn- encode-session []
  (let [bpm    (js/Math.round (audio/get-bpm))
        editor (when-let [v @editor-view]
                 (.. v -state -doc (toString)))
        obj    (clj->js {:v 1 :bpm bpm :editor (or editor "")})]
    (str "#v1:" (js/btoa (js/JSON.stringify obj)))))

(defn- decode-session [hash]
  (try
    (when (and hash (cstr/starts-with? hash "#v1:"))
      (let [b64 (subs hash 4)
            obj (js->clj (js/JSON.parse (js/atob b64)))]
        obj))
    (catch :default _ nil)))

(defn- restore-session! [session]
  (when session
    (when-let [bpm (get session "bpm")]
      (audio/set-bpm! bpm))
    (when-let [ed (get session "editor")]
      (when-let [view @editor-view]
        (.dispatch view
                   #js {:changes #js {:from   0
                                      :to     (.. view -state -doc -length)
                                      :insert ed}})))))

(defn share! []
  (let [session (encode-session)
        url     (str (.-origin js/location) (.-pathname js/location) session)]
    (if (.-clipboard js/navigator)
      (-> (.writeText (.-clipboard js/navigator) url)
          (.then (fn [] (set-output! (str "URL copied to clipboard") :success)))
          (.catch (fn [_] (js/prompt "Copy this URL:" url))))
      (js/prompt "Copy this URL:" url))))

;;; Forward declarations
(declare evaluate!)

;;; Environment — created once, reused across evaluations

(defonce env-atom
  (atom nil))

;; Keys present in the initial env — used to filter built-ins from user defs
(defonce ^:private builtin-names (atom #{}))

;;; Plugin support

(defn make-host []
  #js {:audioCtx    (audio/get-ctx)
       :analyser    @audio/analyser-node
       :masterGain  @audio/master-gain
       :sampleRate  (.-sampleRate (audio/get-ctx))
       :version     "1.0.0"
       :registerLisp (fn [name f]
                       (swap! env-atom assoc name f))})

(defn mount-visual! [plugin]
  (let [panel (el "plugin-panel")]
    (.mount plugin panel)
    (.remove (.-classList panel) "hidden")))

;;; ── Demo templates ────────────────────────────────────────────────────

(def demo-templates
  {:techno
   {:bpm 130
    :code
";; TECHNO — four-on-the-floor kick, offbeat hats, snare on 2/4, acid bassline
(bpm 130)

(play :kick
  (seq :bd :bd :bd :bd))

(play :hat
  (->> (fast 2 (seq :_ :oh :_ :oh))
       (amp (seq 0.5 0.7 0.5 0.9))))

(play :snare
  (seq :_ :sd :_ :sd))

(play :bass
  (->> (scale :minor :c2 (seq 0 0 3 5))
       (fast 2)
       (decay 0.15)
       (amp 0.8)))
"}

   :ambient
   {:bpm 72
    :code
";; AMBIENT — slow pad chords with reverb, gentle melodic line
(bpm 72)

(play :pad
  (->> (chord :minor7 :a3)
       (amp 0.3)
       (attack 0.4)
       (decay 3.0)))

(play :melody
  (->> (scale :minor :a4 (seq 0 2 4 7 4 2))
       (slow 2)
       (amp 0.4)
       (attack 0.1)
       (decay 1.5)))

(play :pulse
  (->> (seq :c5 :_ :e5 :_)
       (slow 4)
       (amp 0.15)
       (decay 0.8)))
"}

   :dnb
   {:bpm 174
    :code
";; DRUM & BASS — fast breakbeat, sub bass, amen-style rhythm
(bpm 174)

(play :break
  (seq :bd :_ :_ :bd :_ :_ :sd :_
       :bd :_ :bd :_ :_ :sd :_ :_))

(play :hat
  (->> (fast 2 (seq :hh :hh :oh :hh))
       (amp (seq 0.6 0.4 0.8 0.4))))

(play :sub
  (->> (scale :minor :e1 (seq 0 :_ 0 :_ 3 :_ 5 :_))
       (amp 0.9)
       (decay 0.2)))
"}

   :minimal
   {:bpm 120
    :code
";; MINIMAL — sparse kick, subtle hi-hats, one-note bass
(bpm 120)

(play :kick
  (seq :bd :_ :_ :_ :bd :_ :_ :_))

(play :hat
  (->> (seq :_ :hh :_ :hh :_ :hh :_ :_)
       (amp 0.35)))

(play :bass
  (->> (pure :c2)
       (amp 0.6)
       (decay 0.12)))
"}

   :house
   {:bpm 124
    :code
";; HOUSE — classic four-on-the-floor, organ stab chords, open hat
(bpm 124)

(play :kick
  (seq :bd :bd :bd :bd))

(play :hat
  (->> (seq :_ :oh :_ :oh)
       (amp 0.5)))

(play :clap
  (seq :_ :sd :_ :sd))

(play :chord
  (->> (every 4 (fast 2) (chord :dom7 :c4))
       (amp 0.4)
       (attack 0.02)
       (decay 0.25)))

(play :bass
  (->> (scale :minor :c2 (seq 0 0 3 0 5 0 3 0))
       (amp 0.7)
       (decay 0.1)))
"}

   :dub
   {:bpm 140
    :code
";; DUB — heavy bass, delay-heavy snare, sparse hats
(bpm 140)

(play :kick
  (seq :bd :_ :_ :_ :_ :_ :bd :_))

(play :snare
  (->> (seq :_ :_ :_ :sd :_ :_ :_ :_)
       (amp 0.8)
       (decay 0.3)))

(play :hat
  (->> (seq :_ :hh :_ :_ :_ :_ :hh :_)
       (amp 0.3)))

(play :bass
  (->> (scale :minor :g1 (seq 0 :_ :_ 0 :_ 3 :_ :_))
       (amp 0.9)
       (attack 0.01)
       (decay 0.4)))
"}

   :experimental
   {:bpm 110
    :code
";; EXPERIMENTAL — algorithmic patterns using every, rev, fmap
(bpm 110)

(play :rhythm
  (every 3 rev
    (seq :bd :_ :sd :_ :bd :bd :_ :sd)))

(play :texture
  (->> (every 2 (fast 2) (seq :hh :oh :hh :_))
       (amp (seq 0.3 0.6 0.4 0.8))))

(play :melody
  (->> (scale :dorian :d3 (seq 0 2 4 6 7 4 2 0))
       (every 4 rev)
       (every 3 (fast 2))
       (amp 0.5)
       (decay 0.6)))

(play :drone
  (->> (chord :sus4 :d2)
       (amp 0.2)
       (attack 0.5)
       (decay 2.5)))
"}})

;;; ── Tutorial chapters ──────────────────────────────────────────────────

(def tutorial-chapters
  [
   ;; Chapter 1: First sound
   ";; === Tutorial 1/8 — First Sound ===
;;
;; Welcome to REPuLse! Let's make some noise.
;;
;; `seq` creates a sequence of sounds.  Each value plays
;; for one equal subdivision of the cycle:
;;   :bd = bass drum   :sd = snare   :hh = hi-hat
;;
;; Press Alt+Enter (Option+Enter on Mac) to hear this:

(seq :bd :sd :bd :sd)

;; Try changing :sd to :hh and press Alt+Enter again.
;; When you're ready, type (tutorial 2) in the command bar."

   ;; Chapter 2: Layering
   ";; === Tutorial 2/8 — Layering with stack ===
;;
;; `stack` plays multiple patterns at the same time.
;; Each pattern runs in parallel, like tracks in a mixer.

(stack
  (seq :bd :_ :bd :_)
  (seq :_ :sd :_ :sd)
  (seq :hh :hh :hh :hh))

;; :_ is a rest — silence for that step.
;; Try adding a fourth layer!
;; Next: (tutorial 3)"

   ;; Chapter 3: Speed
   ";; === Tutorial 3/8 — Speed: fast & slow ===
;;
;; `fast` speeds up a pattern by a factor.
;; `slow` does the opposite.

(stack
  (seq :bd :_ :bd :_)
  (fast 2 (seq :hh :oh))
  (slow 2 (seq :sd :_ :_ :_)))

;; (fast 2 pat) plays pat twice per cycle.
;; (slow 2 pat) stretches pat over two cycles.
;; Try (fast 4 (seq :hh :oh)) for rapid hi-hats.
;; Next: (tutorial 4)"

   ;; Chapter 4: Evolution
   ";; === Tutorial 4/8 — Evolution: every ===
;;
;; `every` applies a transformation only on certain cycles.
;; (every N transform pattern) — transform every Nth cycle.

(stack
  (every 4 (fast 2) (seq :bd :_ :bd :_))
  (seq :_ :sd :_ :sd)
  (every 3 rev (seq :hh :oh :hh :_)))

;; The kick doubles speed every 4th cycle.
;; The hats reverse every 3rd cycle.
;; This is how patterns stay alive without manual changes.
;; Next: (tutorial 5)"

   ;; Chapter 5: Naming
   ";; === Tutorial 5/8 — Naming: def ===
;;
;; `def` binds a name to a value. Use it to build
;; a vocabulary of reusable parts.

(def kick  (seq :bd :_ :bd :_))
(def snare (seq :_ :sd :_ :sd))
(def hat   (fast 2 (seq :hh :oh)))

(stack kick snare hat)

;; Now you can refer to `kick`, `snare`, `hat` by name.
;; Try: (def kick (seq :bd :bd :_ :bd)) and re-evaluate.
;; Next: (tutorial 6)"

   ;; Chapter 6: Multi-track
   ";; === Tutorial 6/8 — Multi-Track: play ===
;;
;; `play` starts a named track.  Each track runs
;; independently — you can update one without stopping others.

(play :kick
  (seq :bd :_ :bd :bd))

(play :snare
  (seq :_ :sd :_ :sd))

(play :hat
  (fast 2 (seq :hh :oh)))

;; In the command bar, try:
;;   (mute! :hat)     — silence the hats
;;   (unmute! :hat)   — bring them back
;;   (solo! :kick)    — hear only the kick
;;   (clear!)         — stop everything
;; Next: (tutorial 7)"

   ;; Chapter 7: Melody
   ";; === Tutorial 7/8 — Melody: scale & chord ===
;;
;; Note keywords like :c4 play pitched tones.
;; `scale` maps degree numbers (0, 1, 2, …) to a musical scale.
;; `chord` stacks the tones of a chord.

(play :bass
  (scale :minor :c3 (seq 0 0 3 5)))

(play :chords
  (slow 2 (chord :minor :c4)))

(play :melody
  (scale :minor :c4 (seq 0 2 4 7 4 2)))

(play :kick
  (seq :bd :bd :bd :bd))

;; Try changing :minor to :dorian or :blues.
;; Try (transpose 5 ...) around the melody.
;; Next: (tutorial 8)"

   ;; Chapter 8: Expression
   ";; === Tutorial 8/8 — Expression: amp, decay, ->> ===
;;
;; Per-event parameters make patterns expressive.
;; `->>` threads a pattern through a chain of transformers.

(play :kick
  (->> (seq :bd :bd :bd :bd)
       (amp (seq 0.9 0.5 0.7 0.5))))

(play :lead
  (->> (scale :minor :c4 (seq 0 2 4 7 4 2 0 :_))
       (amp 0.6)
       (attack 0.02)
       (decay 0.5)))

(play :pad
  (->> (chord :minor7 :c3)
       (amp 0.25)
       (attack 0.3)
       (decay 2.0)))

;; (amp val) sets amplitude 0.0–1.0
;; (attack secs) sets onset time
;; (decay secs) sets fade time
;; (pan pos) sets stereo position -1.0 to 1.0
;;
;; That's the basics! Try (demo :techno) or (demo :experimental)
;; to hear full compositions, or start writing your own."
])

(defn make-stop-fn []
  (fn []
    (audio/stop!)
    (clear-highlights!)
    (set-playing! false)
    (set-output! "stopped" :idle)))

(defn ensure-env! []
  (when (nil? @env-atom)
    (samples/init!)
    (reset! env-atom
            (assoc (leval/make-env (make-stop-fn) audio/set-bpm!)
                   ;; --- Multi-track ---
                   "play"
                   (fn [track-name pat]
                     (let [name' (leval/unwrap track-name)
                           pat'  (leval/unwrap pat)]
                       (if (and (map? pat') (fn? (:query pat')))
                         (do
                           (audio/play-track! name' pat' on-beat highlight-range!)
                           (set-playing! true)
                           (str "=> track :" (name name') " playing"))
                         "Error: second argument to play must be a pattern")))
                   "mute!"
                   (fn [track-name]
                     (let [name' (leval/unwrap track-name)]
                       (audio/mute-track! name')
                       (str "=> muted :" (name name'))))
                   "unmute!"
                   (fn [track-name]
                     (let [name' (leval/unwrap track-name)]
                       (audio/unmute-track! name')
                       (str "=> unmuted :" (name name'))))
                   "solo!"
                   (fn [track-name]
                     (let [name' (leval/unwrap track-name)]
                       (audio/solo-track! name')
                       (str "=> solo :" (name name'))))
                   "clear!"
                   (fn
                     ([]
                      (audio/stop!)
                      (clear-highlights!)
                      (set-playing! false)
                      "=> cleared all tracks")
                     ([track-name]
                      (let [name' (leval/unwrap track-name)]
                        (audio/clear-track! name')
                        (when (not (:playing? @audio/scheduler-state))
                          (set-playing! false))
                        (str "=> cleared :" (name name')))))
                   "tracks"
                   (fn []
                     (let [ks (keys (:tracks @audio/scheduler-state))]
                       (if (seq ks)
                         (str "=> (" (cstr/join " " (map #(str ":" (name %)) ks)) ")")
                         "=> ()")))
                   ;; --- Hot-swap update ---
                   "upd"
                   (fn []
                     (when-let [view @editor-view]
                       (let [code   (.. view -state -doc (toString))
                             env    (assoc @env-atom "stop" (make-stop-fn))
                             result (lisp/eval-string code env)]
                         (if-let [err (:error result)]
                           (do (clear-highlights!)
                               (set-output! (str "Error: " err) :error))
                           (let [val (:result result)]
                             (cond
                               (and (map? val) (fn? (:query val)))
                               (do (audio/play-track! :_ val on-beat highlight-range!)
                                   (set-playing! true)
                                   (set-output! "updated" :success))
                               (nil? val) nil
                               (string? val) (set-output! val :success)
                               :else (set-output! (str "=> " (pr-str val)) :success)))))))
                   ;; --- Tap tempo ---
                   "tap!"
                   (fn []
                     (if-let [bpm (audio/tap!)]
                       (str "=> " (.toFixed bpm 1) " BPM")
                       "=> tap again…"))
                   ;; --- MIDI clock ---
                   "midi-sync!"
                   (fn [enabled?]
                     (let [on? (leval/unwrap enabled?)]
                       (audio/set-midi-sync! on?)
                       (str "=> MIDI sync " (if on? "enabled" "disabled"))))
                   ;; --- Samples ---
                   "samples!"
                   (fn [url]
                     (let [url' (leval/unwrap url)]
                       (samples/load-external! url')
                       (str "loading " url' "…")))
                   "sample-banks"
                   (fn [] (samples/format-banks))
                   ;; --- Plugins ---
                   "load-plugin"
                   (fn [url]
                     (let [url' (leval/unwrap url)]
                       (-> (js* "import(~{})" url')
                           (.then (fn [m]
                                    (let [plug (.-default m)]
                                      (when (= "effect" (.-type plug))
                                        (fx/remove-effect! (.-name plug)))
                                      (plugins/register! plug (make-host))
                                      (if (= "visual" (.-type plug))
                                        (mount-visual! plug)
                                        (when (= "effect" (.-type plug))
                                          (fx/add-effect! plug))))))
                           (.catch (fn [e]
                                     (js/console.warn "[REPuLse] Plugin load failed:" e))))
                       nil))
                   "bank"
                   (fn [prefix]
                     (samples/set-bank-prefix! (leval/unwrap prefix))
                     (str "bank: " (if prefix (name (leval/unwrap prefix)) "cleared")))
                   "fx"
                   (fn [& args]
                     (let [args'     (mapv leval/unwrap args)
                           first-arg (first args')]
                       (cond
                         (= first-arg :off)
                         (fx/bypass! (cljs.core/name (second args')) true)

                         (= first-arg :on)
                         (fx/bypass! (cljs.core/name (second args')) false)

                         (= first-arg :remove)
                         (fx/remove-effect! (cljs.core/name (second args')))

                         :else
                         (let [effect-name (cljs.core/name first-arg)
                               rest-args   (rest args')]
                           (if (keyword? (first rest-args))
                             (doseq [[k v] (partition 2 rest-args)]
                               (fx/set-param! effect-name (cljs.core/name k) v))
                             (fx/set-param! effect-name "value" (first rest-args))))))
                     nil)
                   ;; --- Per-track FX --- Phase L
                   "track-fx"
                   (fn [& args]
                     (let [args'      (mapv leval/unwrap args)
                           track-name (first args')
                           rest-args  (vec (rest args'))]
                       (cond
                         ;; (track-fx :bass :off) — clear all effects from track
                         (and (= (first rest-args) :off) (= 1 (count rest-args)))
                         (fx/clear-track-effects! track-name)

                         ;; (track-fx :bass :off :reverb) — remove specific effect
                         (= (first rest-args) :off)
                         (fx/remove-track-effect! track-name (cljs.core/name (second rest-args)))

                         ;; (track-fx :bass :reverb 0.4) — add/set effect
                         :else
                         (let [effect-name (cljs.core/name (first rest-args))
                               params      (rest rest-args)]
                           (when-not (some #(= effect-name (:name %))
                                           (:fx-chain (get @audio/track-nodes track-name)))
                             (fx/add-track-effect! track-name effect-name))
                           (if (keyword? (first params))
                             (doseq [[k v] (partition 2 params)]
                               (fx/set-track-param! track-name effect-name (cljs.core/name k) v))
                             (when (seq params)
                               (fx/set-track-param! track-name effect-name "value" (first params))))))
                       nil))
                   ;; --- Demo templates ---
                   "demo"
                   (fn [& args]
                     (let [kw (when (seq args) (leval/unwrap (first args)))]
                       (if (nil? kw)
                         (str "available demos: "
                              (cstr/join " " (map #(str ":" (name %))
                                                  (sort (keys demo-templates)))))
                         (if-let [{:keys [bpm code]} (get demo-templates kw)]
                           (do
                             (audio/set-bpm! bpm)
                             (when-let [view @editor-view]
                               (.dispatch view
                                          #js {:changes #js {:from 0
                                                             :to   (.. view -state -doc -length)
                                                             :insert code}})
                               (js/setTimeout #(evaluate! code) 50))
                             (str "=> loaded demo :" (name kw)))
                           (str "unknown demo :" (name kw)
                                " — available: "
                                (cstr/join " " (map #(str ":" (name %))
                                                    (sort (keys demo-templates)))))))))
                   ;; --- Interactive tutorial ---
                   "tutorial"
                   (fn [& args]
                     (let [n   (if (seq args) (int (leval/unwrap (first args))) 1)
                           idx (dec n)]
                       (if (and (>= idx 0) (< idx (count tutorial-chapters)))
                         (let [code (nth tutorial-chapters idx)]
                           (when-let [view @editor-view]
                             (.dispatch view
                                        #js {:changes #js {:from 0
                                                           :to   (.. view -state -doc -length)
                                                           :insert code}}))
                           (str "=> tutorial chapter " n "/" (count tutorial-chapters)
                                " — press Alt+Enter to play"))
                         (str "tutorial has chapters 1–" (count tutorial-chapters)
                              " — try (tutorial 1)"))))
                   ;; --- Gist import ---
                   "load-gist"
                   (fn [url]
                     (let [url' (leval/unwrap url)
                           raw-url (if (re-find #"gist\.githubusercontent\.com" url')
                                     url'
                                     ;; Convert gist.github.com/user/id → API URL
                                     (let [[_ gist-id] (re-find #"/([a-f0-9]+)/?$" url')]
                                       (str "https://api.github.com/gists/" gist-id)))]
                       (if (re-find #"api\.github\.com" raw-url)
                         ;; API path — fetch JSON, extract first file's content
                         (-> (js/fetch raw-url)
                             (.then #(.json %))
                             (.then (fn [data]
                                      (let [files      (js->clj (.-files data))
                                            first-file (second (first files))
                                            content    (get first-file "content")]
                                        (when-let [view @editor-view]
                                          (.dispatch view
                                                     #js {:changes #js {:from   0
                                                                        :to     (.. view -state -doc -length)
                                                                        :insert content}})
                                          (evaluate! content)))))
                             (.catch (fn [e]
                                       (set-output! (str "Gist load failed: " e) :error))))
                         ;; Raw URL — fetch text directly
                         (-> (js/fetch raw-url)
                             (.then #(.text %))
                             (.then (fn [text]
                                      (when-let [view @editor-view]
                                        (.dispatch view
                                                   #js {:changes #js {:from   0
                                                                      :to     (.. view -state -doc -length)
                                                                      :insert text}})
                                        (evaluate! text))))
                             (.catch (fn [e]
                                       (set-output! (str "Gist load failed: " e) :error)))))
                       (str "loading gist…")))
                   ;; --- WAV export ---
                   "export"
                   (fn [& args]
                     (let [arg       (when (seq args) (leval/unwrap (first args)))
                           n-cycles  (if (number? arg) (int arg) 4)
                           track-kw  (when (keyword? arg) arg)
                           state     @audio/scheduler-state
                           all-tracks (:tracks state)
                           tracks    (cond
                                       (nil? track-kw) all-tracks
                                       (contains? all-tracks track-kw) {track-kw (get all-tracks track-kw)}
                                       :else {})
                           cycle-dur (:cycle-dur state)
                           duration  (* n-cycles cycle-dur)
                           sr        44100
                           n-frames  (int (* sr duration))
                           offline   (js/OfflineAudioContext. 2 n-frames sr)]
                       (if (empty? tracks)
                         (if track-kw
                           (str "Error: no track :" (name track-kw))
                           "Error: no active tracks to export")
                         (do
                           ;; Schedule all events for N cycles into the offline context
                           (doseq [c (range n-cycles)]
                             (let [sp {:start [(int c) 1] :end [(int (inc c)) 1]}]
                               (doseq [[_track-name pattern] tracks]
                                 (when pattern
                                   (let [evs (core/query pattern sp)]
                                     (doseq [ev evs]
                                       (let [part-start (core/rat->float (:start (:part ev)))
                                             t          (* (- part-start c) cycle-dur)
                                             abs-t      (+ (* c cycle-dur) t)]
                                         (when (>= abs-t 0)
                                           (audio/play-event offline abs-t (:value ev))))))))))
                           ;; Render and encode as WAV
                           (-> (.startRendering offline)
                               (.then (fn [buffer]
                                        (let [ch-l       (.getChannelData buffer 0)
                                              ch-r       (.getChannelData buffer 1)
                                              n          (.-length ch-l)
                                              bps        16
                                              n-ch       2
                                              data-bytes (* n n-ch (/ bps 8))
                                              buf        (js/ArrayBuffer. (+ 44 data-bytes))
                                              dv         (js/DataView. buf)]
                                          ;; RIFF header
                                          (doto dv
                                            (.setUint8   0 82) (.setUint8   1 73)  ; R I
                                            (.setUint8   2 70) (.setUint8   3 70)  ; F F
                                            (.setUint32  4 (+ 36 data-bytes) true)
                                            (.setUint8   8 87) (.setUint8   9 65)  ; W A
                                            (.setUint8  10 86) (.setUint8  11 69)  ; V E
                                            ;; fmt chunk
                                            (.setUint8  12 102) (.setUint8 13 109) ; f m
                                            (.setUint8  14 116) (.setUint8 15 32)  ; t _
                                            (.setUint32 16 16 true)                ; chunk size
                                            (.setUint16 20 1 true)                 ; PCM
                                            (.setUint16 22 n-ch true)
                                            (.setUint32 24 sr true)
                                            (.setUint32 28 (* sr n-ch (/ bps 8)) true)
                                            (.setUint16 32 (* n-ch (/ bps 8)) true)
                                            (.setUint16 34 bps true)
                                            ;; data chunk
                                            (.setUint8  36 100) (.setUint8 37 97)  ; d a
                                            (.setUint8  38 116) (.setUint8 39 97)  ; t a
                                            (.setUint32 40 data-bytes true))
                                          ;; Interleaved 16-bit PCM samples
                                          (dotimes [i n]
                                            (let [l      (Math/max -1 (Math/min 1 (aget ch-l i)))
                                                  r      (Math/max -1 (Math/min 1 (aget ch-r i)))
                                                  offset (+ 44 (* i 4))]
                                              (.setInt16 dv offset       (int (* l 32767)) true)
                                              (.setInt16 dv (+ offset 2) (int (* r 32767)) true)))
                                          ;; Trigger download
                                          (let [blob (js/Blob. #js [buf] #js {:type "audio/wav"})
                                                url  (.createObjectURL js/URL blob)
                                                a    (.createElement js/document "a")]
                                            (set! (.-href a) url)
                                            (set! (.-download a) (str "repulse-"
                                                                       (if track-kw (name track-kw) "all")
                                                                       "-" n-cycles "cycles.wav"))
                                            (.appendChild (.-body js/document) a)
                                            (.click a)
                                            (.removeChild (.-body js/document) a)
                                            (js/setTimeout #(.revokeObjectURL js/URL url) 1000)))))
                               (.catch (fn [e]
                                         (js/console.error "[REPuLse] export failed:" e))))
                           (str "exporting "
                                (if track-kw (str ":" (name track-kw)) "all tracks")
                                " — " n-cycles " cycles…")))))))
    ;; Wire the FX event notification callback (used by sidechain plugin)
    (swap! audio/scheduler-state assoc :on-fx-event fx/notify-fx-event!)
    ;; Snapshot built-in names so render-context-panel! can filter them out
    (reset! builtin-names (set (keys @env-atom)))))

;;; Evaluation

(defn evaluate! [code]
  (ensure-env!)
  ;; Keep stop fn up to date (in case env was reset)
  (let [env (assoc @env-atom "stop" (make-stop-fn))
        result (lisp/eval-string code env)]
    (if-let [err (:error result)]
      (do
        (clear-highlights!)
        (set-output! (str "Error: " err) :error))
      (let [val (:result result)]
        (cond
          ;; Pattern — start playing (legacy single-pattern mode)
          (and (map? val) (fn? (:query val)))
          (do
            (audio/stop!)
            (clear-highlights!)
            (audio/start! val on-beat highlight-range!)
            (set-playing! true)
            (set-output! "playing pattern — Alt+Enter to re-evaluate, (stop) to stop" :success))

          ;; stop fn was called directly — handled inside stop fn
          (nil? val)
          nil

          ;; Pre-formatted string (e.g. sample-banks, track ops) — display as-is
          (string? val)
          (set-output! val :success)

          :else
          (set-output! (str "=> " (pr-str val)) :success))))))

;;; Context panel

(defn- infer-type [v]
  (cond
    (and (map? v) (fn? (:query v))) "pattern"
    (fn? v)                          "fn"
    (number? v)                      "number"
    (string? v)                      "string"
    (keyword? v)                     "keyword"
    :else                            "value"))

(defn- fmt-pv [v]
  (if (number? v)
    (if (== v (Math/round v)) (str (int v)) (.toFixed v 2))
    (str v)))

(defn- render-context-panel! []
  (when-let [status-el (el "ctx-status")]
    (let [bpm     (Math/round (/ 240.0 (:cycle-dur @audio/scheduler-state)))
          playing? (audio/playing?)]
      (set! (.-innerHTML status-el)
            (str "<span class=\"ctx-bpm\">" bpm " BPM</span>"
                 (when-let [pfx @samples/active-bank-prefix]
                   (str "<span class=\"ctx-bank\">" pfx "</span>"))
                 "<span class=\"" (if playing? "ctx-playing" "ctx-stopped") "\">"
                 (if playing? "&#9679; playing" "&#9675; stopped")
                 "</span>"))))
  (when-let [bindings-el (el "ctx-bindings")]
    (let [env       (or @env-atom {})
          builtins  @builtin-names
          user-defs (sort (remove builtins (keys env)))]
      (set! (.-innerHTML bindings-el)
            (str "<div class=\"ctx-section-title\">Bindings</div>"
                 (if (empty? user-defs)
                   "<div class=\"ctx-empty\">—</div>"
                   (apply str
                          (map (fn [k]
                                 (str "<div class=\"ctx-row\">"
                                      "<span class=\"ctx-name\">" k "</span>"
                                      "<span class=\"ctx-type\">" (infer-type (get env k)) "</span>"
                                      "</div>"))
                               user-defs)))))))
  (when-let [effects-el (el "ctx-effects")]
    (let [chain @fx/chain]
      (set! (.-innerHTML effects-el)
            (str "<div class=\"ctx-section-title\">Effects</div>"
                 (if (empty? chain)
                   "<div class=\"ctx-empty\">—</div>"
                   (apply str
                          (map (fn [{:keys [name plugin bypassed?]}]
                                 (let [params    (when plugin
                                                   (try (js->clj (.getParams ^js plugin))
                                                        (catch :default _ {})))
                                       first-kv  (first (seq params))
                                       param-str (when first-kv
                                                   (str (first first-kv) ": "
                                                        (fmt-pv (second first-kv))))]
                                   (str "<div class=\"ctx-row\">"
                                        "<span class=\"ctx-name\">" name "</span>"
                                        (cond
                                          bypassed?  "<span class=\"ctx-bypass\">off</span>"
                                          param-str  (str "<span class=\"ctx-param\">" param-str "</span>")
                                          :else      "")
                                        "</div>")))
                               chain))))))))

;;; CodeMirror editor

(def ^:private storage-key "repulse-editor")
(def ^:private bpm-storage-key "repulse-bpm")

(defn- load-editor-content [fallback]
  (or (try (.getItem js/localStorage storage-key) (catch :default _ nil))
      fallback))

(defn- load-saved-bpm []
  (try
    (when-let [s (.getItem js/localStorage bpm-storage-key)]
      (let [n (js/parseFloat s)]
        (when-not (js/isNaN n) n)))
    (catch :default _ nil)))

(def ^:private save-listener
  (.of EditorView.updateListener
       (fn [^js update]
         (when (.-docChanged update)
           (try (.setItem js/localStorage storage-key
                          (.. update -state -doc (toString)))
                (catch :default _))))))

(defn make-cmd-editor 
  "Single-line CodeMirror editor for the command bar. Enter evaluates + clears."
  [container]
  (let [clear-view! (fn [view]
                      (.dispatch view
                                 #js {:changes #js {:from 0
                                                    :to   (.. view -state -doc -length)
                                                    :insert ""}})
                      true)
        eval-cmd    (fn [view]
                      (let [raw (cstr/trim (.. view -state -doc (toString)))]
                        (when (seq raw)
                          (let [code (if (cstr/starts-with? raw "(") raw (str "(" raw ")"))]
                            (evaluate! code)
                            (clear-view! view))))
                      true)
        clear+return! (fn [view]
                        (clear-view! view)
                        (when-let [ev @editor-view] (.focus ev))
                        true)
        extensions  #js [oneDark
                         lispLanguage
                         (.of keymap #js [#js {:key "Mod-a"  :run selectAll}
                                          #js {:key "Enter"  :run eval-cmd}
                                          #js {:key "Escape" :run clear+return!}])]
        state (.. EditorState (create #js {:doc "" :extensions extensions}))
        view  (EditorView. #js {:state state :parent container})]
    view))

(defn make-editor [container initial-value on-eval]
  (let [eval-cmd (fn [view]
                   (on-eval (.. view -state -doc (toString)))
                   true)
        eval-binding    #js {:key "Alt-Enter" :run eval-cmd}
        upd-fn          (fn [_] (evaluate! "(upd)") true)
        upd-binding     #js {:key "Ctrl-." :run upd-fn}
        upd-f9-binding  #js {:key "F9"     :run upd-fn}
        escape-binding  #js {:key "Escape"
                             :run (fn [_]
                                    (when-let [cv @cmd-view] (.focus cv))
                                    true)}
        extensions #js [(history)
                        (lineNumbers)
                        oneDark
                        lispLanguage
                        (bracketMatching)
                        highlights-field
                        save-listener
                        (.-lineWrapping EditorView)
                        (.of keymap (.concat
                                     #js [escape-binding eval-binding upd-binding upd-f9-binding]
                                     (clj->js defaultKeymap)
                                     (clj->js historyKeymap)))]
        state (.. EditorState
                  (create #js {:doc (load-editor-content initial-value)
                               :extensions extensions}))
        view (EditorView. #js {:state state :parent container})]
    view))

;;; App bootstrap

(defn on-play-btn-click []
  (if (audio/playing?)
    ;; Stop
    (do (audio/stop!)
        (clear-highlights!)
        (set-playing! false)
        (set-output! "stopped" :idle))
    ;; Evaluate current editor content and start
    (when-let [view @editor-view]
      (evaluate! (.. view -state -doc (toString))))))

(defn build-dom! []
  (let [app (el "app")]
    (set! (.-innerHTML app)
          (str "<header>"
               "  <h1>REPuLse</h1>"
               "  <div class=\"header-controls\">"
               "    <button id=\"tap-btn\" class=\"tap-btn\">tap</button>"
               "    <button id=\"share-btn\" class=\"share-btn\">share</button>"
               "    <button id=\"play-btn\" class=\"play-btn\">&#9654; play</button>"
               "    <div id=\"playing-dot\" class=\"playing-dot\"></div>"
               "  </div>"
               "</header>"
               "<div class=\"main-area\">"
               "  <div id=\"editor-container\" class=\"editor-container\"></div>"
               "  <div id=\"context-panel\" class=\"context-panel\">"
               "    <div id=\"ctx-status\" class=\"ctx-status\"></div>"
               "    <div id=\"ctx-bindings\" class=\"ctx-section\"></div>"
               "    <div id=\"ctx-effects\" class=\"ctx-section\"></div>"
               "  </div>"
               "</div>"
               "<div id=\"cmd-bar\" class=\"cmd-bar\">"
               "  <span class=\"cmd-prompt\">&gt;</span>"
               "  <div id=\"cmd-container\" class=\"cmd-container\"></div>"
               "</div>"
               "<div id=\"track-panel\" class=\"track-panel\"></div>"
               "<div id=\"plugin-panel\" class=\"plugin-panel hidden\"></div>"
               "<footer>"
               "  <span id=\"output\" class=\"output\">ready &mdash; Alt+Enter or click play</span>"
               "  <span class=\"hint\">Alt+Enter to eval</span>"
               "</footer>")))
  (.addEventListener (el "play-btn")  "click" on-play-btn-click)
  (.addEventListener (el "tap-btn")   "click" (fn [] (evaluate! "(tap!)")))
  (.addEventListener (el "share-btn") "click" share!))

(defn init []
  (build-dom!)
  (ensure-env!)
  (setBankNamesProvider (fn [] (clj->js (samples/bank-names))))
  (setFxNamesProvider   (fn [] (clj->js (mapv :name @fx/chain))))

  ;; Restore saved BPM from localStorage
  (when-let [bpm (load-saved-bpm)]
    (audio/set-bpm! bpm))

  ;; Restore session from URL hash if present
  (let [hash (.-hash js/location)]
    (when-let [session (decode-session hash)]
      ;; Remove hash from URL without reload
      (.replaceState js/history nil nil (.-pathname js/location))
      ;; Editor isn't mounted yet — defer restore
      (js/setTimeout #(restore-session! session) 0)))

  ;; Register global mute-toggle for track panel onclick
  (set! (.-_repulseMuteToggle js/window)
        (fn [track-name-str]
          (let [kw (keyword track-name-str)]
            (if (contains? (:muted @audio/scheduler-state) kw)
              (audio/unmute-track! kw)
              (audio/mute-track! kw)))))

  (reset! cmd-view (make-cmd-editor (el "cmd-container")))

  (let [container (el "editor-container")
        view (make-editor container "(seq :bd :sd :bd :sd)" evaluate!)]
    (reset! editor-view view)
    (.focus view))

  ;; Global keyboard shortcuts — capture phase so we fire before any child handler
  ;; (including CodeMirror) can stop propagation.
  (.addEventListener js/document "keydown"
    (fn [e]
      (when-let [view @editor-view]
        (let [target     (.-target e)
              editor-dom (.-dom view)
              cmd-dom    (el "cmd-container")
              in-editor? (.contains editor-dom target)
              in-cmd?    (and cmd-dom (.contains cmd-dom target))]
          (cond
            ;; Alt/Option+Enter — evaluate main buffer from anywhere.
            (and (.-altKey e) (= "Enter" (.-key e)))
            (do (.preventDefault e)
                (evaluate! (.. view -state -doc (toString))))

            ;; Ctrl+. or F9 — run (upd) from anywhere
            (or (and (.-ctrlKey e) (= "." (.-key e)))
                (= "F9" (.-key e)))
            (do (.preventDefault e)
                (evaluate! "(upd)"))

            ;; Ctrl/Cmd+A — focus editor + select all when outside both editors
            (and (or (.-metaKey e) (.-ctrlKey e))
                 (= "a" (.-key e))
                 (not in-editor?)
                 (not in-cmd?))
            (do (.preventDefault e)
                (.focus view)
                (selectAll view))))))
    true) ;; true = capture phase

  ;; Auto-load built-in visual plugins
  (-> (js* "import('/plugins/oscilloscope.js')")
      (.then (fn [m]
               (let [plug (.-default m)]
                 (plugins/register! plug (make-host))
                 (mount-visual! plug))))
      (.catch (fn [e]
                (js/console.warn "[REPuLse] oscilloscope load failed:" e))))

  ;; Auto-load built-in effect plugins
  (plugins/register! compressor-plugin/plugin (make-host))
  (fx/add-effect!    compressor-plugin/plugin)
  (doseq [url ["/plugins/reverb.js"
               "/plugins/delay.js"
               "/plugins/filter.js"
               "/plugins/dattorro-reverb.js"
               "/plugins/chorus.js"
               "/plugins/phaser.js"
               "/plugins/tremolo.js"
               "/plugins/overdrive.js"
               "/plugins/bitcrusher.js"]]
    (-> (js* "import(~{})" url)
        (.then (fn [m]
                 (let [plug (.-default m)]
                   (plugins/register! plug (make-host))
                   (fx/add-effect! plug))))
        (.catch (fn [e]
                  (js/console.warn "[REPuLse] Effect load failed:" url e)))))

  ;; Reactive context panel + track panel
  (add-watch env-atom               ::ctx (fn [_ _ _ _] (render-context-panel!)))
  (add-watch fx/chain               ::ctx (fn [_ _ _ _] (render-context-panel!)))
  (add-watch audio/scheduler-state  ::ctx (fn [_ _ _ _]
                                            (render-context-panel!)
                                            (render-track-panel!)
                                            ;; Persist BPM
                                            (try
                                              (.setItem js/localStorage bpm-storage-key
                                                        (str (audio/get-bpm)))
                                              (catch :default _))))
  (add-watch samples/active-bank-prefix ::ctx (fn [_ _ _ _] (render-context-panel!)))

  (render-context-panel!)
  (start-playhead-raf!))

(defn reload []
  ;; Hot-reload hook: re-attach evaluate! without rebuilding the DOM
  )
