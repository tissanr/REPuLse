(ns repulse.app
  (:require [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.core :as core]
            [repulse.audio :as audio]
            [repulse.bus :as bus]
            [repulse.midi :as midi]
            [repulse.theory :as theory]
            [repulse.params :as params]
            [repulse.synth :as synth]
            [repulse.samples :as samples]
            [repulse.plugins :as plugins]
            [repulse.fx :as fx]
            [repulse.session :as session]
            [repulse.plugins.compressor :as compressor-plugin]
            [clojure.string :as cstr]
            ["@codemirror/view" :refer [EditorView Decoration keymap lineNumbers]]
            ["@codemirror/state" :refer [EditorState StateEffect StateField]]
            ["@codemirror/commands" :refer [defaultKeymap historyKeymap history selectAll]]
            ["@codemirror/language" :refer [bracketMatching]]
            ["@codemirror/lint" :refer [setDiagnostics lintGutter]]
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

(defn- b64-encode
  "Unicode-safe base64 encode: handles em-dashes and other non-Latin-1 chars."
  [s]
  (js/btoa (js/unescape (js/encodeURIComponent s))))

(defn- b64-decode
  "Unicode-safe base64 decode: inverse of b64-encode."
  [s]
  (js/decodeURIComponent (js/escape (js/atob s))))

(defn- encode-session []
  (let [snap (session/build-session-snapshot)
        obj  (clj->js snap)]
    (str "#v2:" (b64-encode (js/JSON.stringify obj)))))

(defn- decode-session
  "Decode a URL hash into a session map. Handles #v1: and #v2: formats.
   Returns a keyword-keyed map or nil."
  [hash]
  (try
    (cond
      (and hash (cstr/starts-with? hash "#v2:"))
      (let [b64  (subs hash 4)
            data (js->clj (js/JSON.parse (b64-decode b64)) :keywordize-keys true)]
        (when (= (:v data) 2) data))

      (and hash (cstr/starts-with? hash "#v1:"))
      (let [b64  (subs hash 4)
            data (js->clj (js/JSON.parse (js/atob b64)) :keywordize-keys true)]
        ;; Normalize v1 {v bpm editor} to v2 shape so restore works uniformly
        (when data
          {:v       2
           :bpm     (:bpm data)
           :editor  (:editor data)
           :fx      []
           :bank    nil
           :sources []
           :muted   []
           :midi    {}}))

      :else nil)
    (catch :default _ nil)))

;; Closure Compiler cannot transpile dynamic import() expressions that appear
;; in ClojureScript-compiled (goog.module) code.  Wrapping the call inside a
;; Function constructor hides the syntax from Closure's static analysis;
;; the browser executes it natively at runtime.
(def ^:private dynamic-import!
  (js/Function. "url" "return import(url)"))

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
(declare set-diagnostics!)

;; Muted tracks restored from localStorage — applied after the first eval creates tracks.
(defonce ^:private pending-mutes (atom #{}))

;; Tracks which track names have been defined in the current evaluation pass.
;; Reset by evaluate! before each eval; checked by the `track` builtin to
;; detect duplicate track names in the same buffer.
(defonce ^:private seen-tracks (atom #{}))

;;; Environment — created once, reused across evaluations

(defonce env-atom
  (atom nil))

;; Keys present in the initial env — used to filter built-ins from user defs
(defonce ^:private builtin-names (atom #{}))

;; Freesound API key — set via (freesound-key! "...")
(defonce ^:private freesound-api-key (atom nil))

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

(defn maybe-hide-visual-panel! []
  ;; Hide the panel when no visual plugins remain in the registry
  (when (empty? (plugins/visual-plugins))
    (.add (.-classList (el "plugin-panel")) "hidden")))

;;; ── Demo templates ────────────────────────────────────────────────────

(def demo-templates
  {:techno
   {:bpm 130
    :code
";; TECHNO — four-on-the-floor kick, offbeat hats, snare on 2/4, acid bassline
(bpm 130)

(track :kick
  (seq :bd :bd :bd :bd))

(track :hat
  (->> (fast 2 (seq :_ :oh :_ :oh))
       (amp (seq 0.5 0.7 0.5 0.9))))

(track :snare
  (seq :_ :sd :_ :sd))

(track :bass
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

(track :pad
  (->> (chord :minor7 :a3)
       (amp 0.3)
       (attack 0.4)
       (decay 3.0)))

(track :melody
  (->> (scale :minor :a4 (seq 0 2 4 7 4 2))
       (slow 2)
       (amp 0.4)
       (attack 0.1)
       (decay 1.5)))

(track :pulse
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

(track :break
  (seq :bd :_ :_ :bd :_ :_ :sd :_
       :bd :_ :bd :_ :_ :sd :_ :_))

(track :hat
  (->> (fast 2 (seq :hh :hh :oh :hh))
       (amp (seq 0.6 0.4 0.8 0.4))))

(track :sub
  (->> (scale :minor :e1 (seq 0 :_ 0 :_ 3 :_ 5 :_))
       (amp 0.9)
       (decay 0.2)))
"}

   :minimal
   {:bpm 120
    :code
";; MINIMAL — sparse kick, subtle hi-hats, one-note bass
(bpm 120)

(track :kick
  (seq :bd :_ :_ :_ :bd :_ :_ :_))

(track :hat
  (->> (seq :_ :hh :_ :hh :_ :hh :_ :_)
       (amp 0.35)))

(track :bass
  (->> (pure :c2)
       (amp 0.6)
       (decay 0.12)))
"}

   :house
   {:bpm 124
    :code
";; HOUSE — classic four-on-the-floor, organ stab chords, open hat
(bpm 124)

(track :kick
  (seq :bd :bd :bd :bd))

(track :hat
  (->> (seq :_ :oh :_ :oh)
       (amp 0.5)))

(track :clap
  (seq :_ :sd :_ :sd))

(track :chord
  (->> (every 4 (fast 2) (chord :dom7 :c4))
       (amp 0.4)
       (attack 0.02)
       (decay 0.25)))

(track :bass
  (->> (scale :minor :c2 (seq 0 0 3 0 5 0 3 0))
       (amp 0.7)
       (decay 0.1)))
"}

   :dub
   {:bpm 140
    :code
";; DUB — heavy bass, delay-heavy snare, sparse hats
(bpm 140)

(track :kick
  (seq :bd :_ :_ :_ :_ :_ :bd :_))

(track :snare
  (->> (seq :_ :_ :_ :sd :_ :_ :_ :_)
       (amp 0.8)
       (decay 0.3)))

(track :hat
  (->> (seq :_ :hh :_ :_ :_ :_ :hh :_)
       (amp 0.3)))

(track :bass
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

(track :rhythm
  (every 3 rev
    (seq :bd :_ :sd :_ :bd :bd :_ :sd)))

(track :texture
  (->> (every 2 (fast 2) (seq :hh :oh :hh :_))
       (amp (seq 0.3 0.6 0.4 0.8))))

(track :melody
  (->> (scale :dorian :d3 (seq 0 2 4 6 7 4 2 0))
       (every 4 rev)
       (every 3 (fast 2))
       (amp 0.5)
       (decay 0.6)))

(track :drone
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
   ";; === Tutorial 6/8 — Multi-Track: track ===
;;
;; `track` defines a named track.  Each track runs
;; independently — you can update one without stopping others.

(track :kick
  (seq :bd :_ :bd :bd))

(track :snare
  (seq :_ :sd :_ :sd))

(track :hat
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
;; `scale` maps degree numbers (1, 2, 3, …) to a musical scale.
;; `chord` stacks the tones of a chord.

(track :bass
  (scale :minor :c3 (seq 1 1 4 6)))

(track :chords
  (slow 2 (chord :minor :c4)))

(track :melody
  (scale :minor :c4 (seq 1 3 5 8 5 3)))

(track :kick
  (seq :bd :bd :bd :bd))

;; Try changing :minor to :dorian or :blues.
;; Try (transpose 5 ...) around the melody.
;; Next: (tutorial 8)"

   ;; Chapter 8: Expression
   ";; === Tutorial 8/8 — Expression: amp, decay, ->> ===
;;
;; Per-event parameters make patterns expressive.
;; `->>` threads a pattern through a chain of transformers.

(track :kick
  (->> (seq :bd :bd :bd :bd)
       (amp (seq 0.9 0.5 0.7 0.5))))

(track :lead
  (->> (scale :minor :c4 (seq 0 2 4 7 4 2 0 :_))
       (amp 0.6)
       (attack 0.02)
       (decay 0.5)))

(track :pad
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

;;; ── First-visit demo ─────────────────────────────────────────────────────

(defn- set-editor-content!
  "Replace the editor buffer with text."
  [text]
  (when-let [view @editor-view]
    (.dispatch view
               #js {:changes #js {:from   0
                                  :to     (.. view -state -doc -length)
                                  :insert text}})))

(defn- first-visit-setup!
  "Load a random demo template on first visit (no localStorage) or after reset!."
  []
  (let [demos [:techno :ambient :house :dnb :minimal]
        pick  (rand-nth demos)
        demo  (get demo-templates pick)]
    (set-editor-content! (:code demo))
    (audio/set-bpm! (:bpm demo))
    (set-output!
      (str "Welcome to REPuLse! Loaded :" (name pick)
           " — press Alt+Enter to play")
      :success)))

(defn ensure-env! []
  (when (nil? @env-atom)
    (samples/init!)
    (reset! env-atom
            (assoc (leval/make-env (make-stop-fn) audio/set-bpm!)
                   :*register-synth-fn* synth/register-synth!
                   ;; --- Multi-track ---
                   "track"
                   (fn [track-name pat]
                     (let [name' (leval/unwrap track-name)
                           pat'  (leval/unwrap pat)
                           ;; Source position of the track-name keyword (for squiggle).
                           ;; Keywords are SourcedVal records; :source holds {:from N :to N}.
                           src   (:source track-name)]
                       ;; Duplicate track name in this evaluation pass → hard error.
                       (when (contains? @seen-tracks name')
                         (throw (ex-info (str "Duplicate track name :" (cljs.core/name name')
                                             " — each track must have a unique name in the buffer")
                                         (cond-> {:type :eval-error}
                                           src (merge {:from (:from src) :to (:to src)})))))
                       (swap! seen-tracks conj name')
                       (if (core/pattern? pat')
                         (do
                           (audio/play-track! name' pat' on-beat highlight-range!)
                           ;; Apply per-track FX from pattern metadata (clear old chain first)
                           (fx/clear-track-effects! name')
                           (doseq [{:keys [name params]} (:track-fx pat')]
                             (fx/add-track-effect! name' name)
                             (doseq [[k v] params]
                               (fx/set-track-param! name' name k v)))
                           (set-playing! true)
                           (str "=> track :" (cljs.core/name name') " playing"))
                         "Error: second argument to track must be a pattern")))
                   "play" (fn [& args] (throw (js/Error. "play is renamed to track — use (track :name pattern)")))
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
                               (set-diagnostics! view (:from result) (:to result) err)
                               (set-output! (str "Error: " err) :error))
                           (do (set-diagnostics! view nil nil nil)
                           (let [val (:result result)]
                             (cond
                               (core/pattern? val)
                               (do (audio/play-track! :_ val on-beat highlight-range!)
                                   (set-playing! true)
                                   (set-output! "updated" :success))
                               (nil? val) nil
                               (string? val) (set-output! val :success)
                               :else (set-output! (str "=> " (pr-str val)) :success))))))
                     ;; Always return nil so evaluate! does not re-process upd's output
                     nil))
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
                       (-> (dynamic-import! url')
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
                   "unload-plugin"
                   (fn [name]
                     (let [name' (leval/unwrap name)]
                       (if (get @plugins/registry name')
                         (do (plugins/unregister! name')
                             (maybe-hide-visual-panel!)
                             (str "unloaded: " name'))
                         {:error (str "no plugin named \"" name' "\"")})))
                   "bank"
                   (fn [prefix]
                     (samples/set-bank-prefix! (leval/unwrap prefix))
                     (str "bank: " (if prefix (name (leval/unwrap prefix)) "cleared")))
                   ;; --- Named audio/control buses ---
                   "bus"
                   (fn [& args]
                     (let [args'     (mapv leval/unwrap args)
                           bus-name  (first args')
                           bus-type  (or (second args') :control)]
                       (when-not (keyword? bus-name)
                         (throw (js/Error. "bus: first argument must be a keyword, e.g. (bus :lfo :control)")))
                       (when-not (#{:control :audio} bus-type)
                         (throw (js/Error. (str "bus: type must be :control or :audio, got " bus-type))))
                       (bus/create-bus! (audio/get-ctx) bus-name bus-type)
                       (str "=> bus " bus-name " (" (name bus-type) ")")))
                   ;; fx: context-aware — per-track transformer when called from ->>, global otherwise
                   "fx"
                   (fn [& raw-args]
                     (let [args'    (mapv leval/unwrap raw-args)
                           last-arg (last args')
                           ;; When ->> passes the pattern as last arg, route per-track
                           per-track? (and (> (count args') 1)
                                           (core/pattern? last-arg))]
                       (if per-track?
                         ;; ── Per-track mode: annotate pattern with FX metadata ──────────
                         (let [fx-args     (butlast args')
                               pat         last-arg
                               effect-name (cljs.core/name (first fx-args))
                               rest-fx     (rest fx-args)
                               params      (if (keyword? (first rest-fx))
                                             ;; All named: (fx :reverb :wet 0.3)
                                             (into {} (map (fn [[k v]] [(cljs.core/name k) v])
                                                           (partition 2 rest-fx)))
                                             ;; Positional first, then optional named: (fx :delay 0.25 :feedback 0.4 :wet 0.5)
                                             (let [named (rest rest-fx)]
                                               (into (when (seq rest-fx) {"value" (first rest-fx)})
                                                     (when (keyword? (first named))
                                                       (map (fn [[k v]] [(cljs.core/name k) v])
                                                            (partition 2 named))))))]
                           (update pat :track-fx (fnil conj []) {:name effect-name :params (or params {})}))
                         ;; ── Global chain mode: apply to master chain ─────────────────
                         (do
                           (let [first-arg (first args')]
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
                           nil))))
                   ;; --- Share ---
                   "share!"
                   (fn [] (share!) nil)
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
                                " — " n-cycles " cycles…")))))
                   ;; --- MIDI controller input ---
                   "midi-map"
                   (fn [& args]
                     (let [args' (mapv leval/unwrap args)
                           cc-num (int (nth args' 1))
                           target (nth args' 2)]
                       (-> (midi/ensure-access!)
                           (.then (fn [_]
                                    (midi/map-cc! cc-num target
                                      (fn [tgt val]
                                        (case tgt
                                          :filter (fx/set-param! "filter" "value" val)
                                          :amp    (.setValueAtTime (.-gain @audio/master-gain)
                                                                   (float val)
                                                                   (.-currentTime (audio/get-ctx)))
                                          :bpm    (audio/set-bpm! (+ 60 (* val 180)))
                                          nil)))))
                           (.catch (fn [e]
                                     (set-output! (str "MIDI error: " e) :error))))
                       "mapping MIDI CC…"))
                   ;; --- MIDI note output ---
                   "midi-out"
                   (fn
                     ([ch]   (params/midi-out (leval/unwrap ch)))
                     ([ch p] (params/midi-out (leval/unwrap ch) (leval/unwrap p))))
                   ;; --- MIDI clock output ---
                   "midi-clock-out!"
                   (fn [on?]
                     (let [on (leval/unwrap on?)]
                       (if on
                         (-> (midi/ensure-access!)
                             (.then (fn [_]
                                      (midi/start-clock! (audio/get-bpm))))
                             (.catch (fn [e]
                                       (set-output! (str "MIDI error: " e) :error))))
                         (midi/stop-clock!))
                       nil))
                   ;; --- MIDI file export ---
                   "midi-export"
                   (fn [& args]
                     (let [args'      (mapv leval/unwrap args)
                           track-name (first args')
                           n-cycles   (or (second args') 4)
                           state      @audio/scheduler-state
                           pattern    (get-in state [:tracks track-name])
                           bpm        (audio/get-bpm)
                           cycle-dur  (:cycle-dur state)]
                       (if-not pattern
                         (str "Error: no track :" (when track-name (name track-name)))
                         (let [events
                               (for [c    (range n-cycles)
                                     :let [sp {:start [c 1] :end [(inc c) 1]}]
                                     ev   (core/query pattern sp)
                                     :let [v  (:value ev)
                                           hz (cond
                                                (number? v) v
                                                (and (map? v) (number? (:note v))) (:note v)
                                                (and (map? v) (keyword? (:note v))
                                                     (theory/note-keyword? (:note v)))
                                                (theory/note->hz (:note v))
                                                (theory/note-keyword? v) (theory/note->hz v)
                                                :else nil)]
                                     :when hz
                                     :let [ps (core/rat->float (:start (:part ev)))
                                           pe (core/rat->float (:end   (:part ev)))]]
                                 {:time-sec     (* (- ps c) cycle-dur)
                                  :duration-sec (* (- pe ps) cycle-dur)
                                  :midi-note    (midi/hz->midi (double hz))
                                  :channel      1})
                               midi-data (midi/export-midi (vec events) bpm)
                               blob      (js/Blob. #js [midi-data] #js {:type "audio/midi"})
                               url       (.createObjectURL js/URL blob)
                               a         (.createElement js/document "a")]
                           (set! (.-href a) url)
                           (set! (.-download a) (str "repulse-" (name track-name) ".mid"))
                           (.appendChild (.-body js/document) a)
                           (.click a)
                           (.removeChild (.-body js/document) a)
                           (js/setTimeout #(.revokeObjectURL js/URL url) 1000)
                           (str "exported " n-cycles " cycles of :" (name track-name) " as MIDI")))))
                   ;; --- Freesound ---
                   "freesound-key!"
                   (fn [key]
                     (reset! freesound-api-key (leval/unwrap key))
                     "Freesound API key set")
                   "freesound!"
                   (fn [query]
                     (let [q   (leval/unwrap query)
                           key @freesound-api-key]
                       (if-not key
                         "Error: set API key first with (freesound-key! \"your-key\")"
                         (do
                           (-> (js/fetch (str "https://freesound.org/apiv2/search/text/"
                                              "?query=" (js/encodeURIComponent q)
                                              "&token=" key
                                              "&fields=id,name,previews"
                                              "&page_size=5"))
                               (.then #(.json %))
                               (.then (fn [data]
                                        (let [results (js->clj (.-results data) :keywordize-keys true)]
                                          (doseq [{:keys [id previews]} results]
                                            (when-let [url (get previews :preview-hq-mp3)]
                                              (samples/register-url! (str "freesound-" id) url)))
                                          (swap! samples/loaded-sources conj
                                                 {:type :freesound :query q :count (count results)})
                                          (set-output!
                                            (str "loaded " (count results) " sounds: "
                                                 (clojure.string/join ", "
                                                   (map #(str ":freesound-" (:id %)) results)))
                                            :success))))
                               (.catch (fn [e]
                                         (set-output! (str "Freesound error: " e) :error))))
                           "searching freesound…"))))
                   ;; --- Session reset ---
                   "reset!"
                   (fn []
                     (audio/stop!)
                     (session/wipe!)
                     (.reload js/window.location)
                     nil)
    ;; Wire the FX event notification callback (used by sidechain plugin)
    ))
    (swap! audio/scheduler-state assoc :on-fx-event fx/notify-fx-event!)
    ;; Snapshot built-in names so render-context-panel! can filter them out
    (reset! builtin-names (set (keys @env-atom)))))

;;; Evaluation

(defn- set-diagnostics!
  "Push a single error diagnostic into the editor, or clear all diagnostics.
   Pass nil for from/to/message to clear."
  [view from to message]
  (let [diags (if (and from to (< from to))
                #js [#js {:from from :to to :severity "error" :message message}]
                #js [])]
    (.dispatch view (setDiagnostics (.-state view) diags))))

(defn evaluate! [code]
  (ensure-env!)
  ;; Reset active flags so removed (fx ...) calls disappear from the panel
  (swap! fx/chain (fn [c] (mapv #(assoc % :active? false) c)))
  ;; Clear per-evaluation duplicate-track detector
  (reset! seen-tracks #{})
  ;; Keep stop fn up to date (in case env was reset)
  (let [env (assoc @env-atom "stop" (make-stop-fn))
        result (lisp/eval-string code env)]
    (if-let [err (:error result)]
      (do
        (clear-highlights!)
        (when-let [view @editor-view]
          (set-diagnostics! view (:from result) (:to result) err))
        (set-output! (str "Error: " err) :error))
      (let [val (:result result)]
        ;; Clear stale diagnostics only when there is a real result.
        ;; A nil val means the command handled output itself (e.g. (upd), (stop))
        ;; and may have already set its own diagnostics — don't clobber them.
        (when (some? val)
          (when-let [view @editor-view]
            (set-diagnostics! view nil nil nil)))
        (cond
          ;; Pattern — start playing (legacy single-pattern mode)
          (core/pattern? val)
          (do
            (audio/stop!)
            (clear-highlights!)
            (audio/start! val on-beat highlight-range!)
            (set-playing! true)
            (set-output! "playing pattern — Alt+Enter to re-evaluate, (stop) to stop" :success))

          ;; Plain event-value map: (saw :c4), (sound :tabla 0), (noise), etc.
          ;; Auto-wrap in pure so users don't need to write (pure (saw :c4)).
          (and (map? val) (or (:note val) (:bank val) (:synth val)))
          (do
            (audio/stop!)
            (clear-highlights!)
            (audio/start! (core/pure val) on-beat highlight-range!)
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
              (audio/play-track! :_ (first pats) on-beat highlight-range!))))

        ;; ── Apply restored mutes after first eval ─────────────────────────
        ;; pending-mutes is populated during session restore; applied once tracks exist.
        (when (seq @pending-mutes)
          (let [tracks (set (keys (:tracks @audio/scheduler-state)))]
            (when (seq tracks)
              (doseq [tk @pending-mutes]
                (when (contains? tracks tk)
                  (audio/mute-track! tk)))
              (reset! pending-mutes #{}))))))))

;;; Context panel

(defn- infer-type [v]
  (cond
    (core/pattern? v) "pattern"
    (fn? v)          "fn"
    (number? v)      "number"
    (string? v)      "string"
    (keyword? v)     "keyword"
    :else            "value"))

(defn- fmt-pv [v]
  (if (number? v)
    (if (== v (Math/round v)) (str (int v)) (.toFixed v 2))
    (str v)))

(def ^:private TRACK-PARAM-KEYS
  [:amp :pan :decay :attack :release :synth :bank :rate :begin :end])

;; Parameters that get interactive sliders, with their range config
(def ^:private SLIDER-PARAMS
  {:amp     {:min 0    :max 1   :step 0.01}
   :pan     {:min -1   :max 1   :step 0.01}
   :decay   {:min 0    :max 4   :step 0.01}
   :attack  {:min 0    :max 2   :step 0.001}
   :release {:min 0    :max 4   :step 0.01}
   :rate    {:min 0.1  :max 4   :step 0.01}
   :begin   {:min 0    :max 1   :step 0.01}
   :end     {:min 0    :max 1   :step 0.01}})

;; FX slider config: {effect-name {param-name {:min :max :step}}}
(def ^:private FX-SLIDER-PARAMS
  {"reverb"          {"wet"      {:min 0   :max 1    :step 0.01}}
   "dattorro-reverb" {"wet"      {:min 0   :max 1    :step 0.01}}
   "delay"           {"time"     {:min 0   :max 2    :step 0.01}
                      "feedback" {:min 0   :max 0.95 :step 0.01}
                      "wet"      {:min 0   :max 1    :step 0.01}}
   "filter"          {"freq"     {:min 20  :max 8000 :step 1}
                      "q"        {:min 0.1 :max 20   :step 0.1}}
   "chorus"          {"wet"      {:min 0   :max 1    :step 0.01}
                      "rate"     {:min 0.1 :max 10   :step 0.1}}
   "phaser"          {"wet"      {:min 0   :max 1    :step 0.01}
                      "rate"     {:min 0.1 :max 10   :step 0.1}}
   "tremolo"         {"depth"    {:min 0   :max 1    :step 0.01}
                      "rate"     {:min 0.1 :max 20   :step 0.1}}
   "overdrive"       {"drive"    {:min 0   :max 1    :step 0.01}}
   "bitcrusher"      {"wet"       {:min 0    :max 1   :step 0.01}}
   "sidechain"       {"amount"    {:min 0    :max 1   :step 0.01}}
   "compressor"      {"wet"       {:min 0    :max 1   :step 0.01}
                      "threshold" {:min -60  :max 0   :step 0.5}
                      "ratio"     {:min 1    :max 20  :step 0.5}
                      "attack"    {:min 0    :max 1   :step 0.001}
                      "release"   {:min 0    :max 1   :step 0.01}
                      "knee"      {:min 0    :max 40  :step 0.5}}})

;; Which getParams key corresponds to the positional (fx :name NUMBER) form
(def ^:private FX-PRIMARY-PARAM
  {"reverb"          "wet"
   "dattorro-reverb" "wet"
   "delay"           "time"
   "filter"          "freq"
   "chorus"          "wet"
   "phaser"          "wet"
   "tremolo"         "depth"
   "overdrive"       "drive"
   "bitcrusher"      "wet"
   "sidechain"       "amount"
   "compressor"      "wet"})

(defn- extract-track-params
  "Query cycle 0 of a pattern and collect the first value for each known param key."
  [pattern]
  (try
    (let [events (core/query pattern {:start [0 1] :end [1 1]})
          maps   (filter map? (map :value events))]
      (reduce (fn [acc m]
                (reduce (fn [a k]
                          (if (and (contains? m k) (not (contains? a k)))
                            (assoc a k (get m k))
                            a))
                        acc TRACK-PARAM-KEYS))
              {} maps))
    (catch :default _ {})))

(defn- render-track-slider [track-name param-key value]
  (when-let [{:keys [min max step]} (get SLIDER-PARAMS param-key)]
    (let [tn  (name track-name)
          pn  (name param-key)]
      (str "<div class=\"ctx-slider-row\">"
           "<label class=\"ctx-param-key\">" pn "</label>"
           "<input type=\"range\" class=\"ctx-slider\""
           " data-track=\"" tn "\""
           " data-param=\"" pn "\""
           " min=\"" min "\" max=\"" max "\" step=\"" step "\""
           " value=\"" value "\">"
           "<span class=\"ctx-param-val\">" (fmt-pv value) "</span>"
           "</div>"))))

(defn- render-fx-slider [effect-name param-name value]
  (when-let [{:keys [min max step]} (get-in FX-SLIDER-PARAMS [effect-name param-name])]
    (str "<div class=\"ctx-slider-row\">"
         "<label class=\"ctx-param-key\">" param-name "</label>"
         "<input type=\"range\" class=\"ctx-slider\""
         " data-fx=\"" effect-name "\""
         " data-param=\"" param-name "\""
         " min=\"" min "\" max=\"" max "\" step=\"" step "\""
         " value=\"" value "\">"
         "<span class=\"ctx-param-val\">" (fmt-pv value) "</span>"
         "</div>")))
(defn- render-track-fx-slider [track-name effect-name param-name value]
  (when-let [{:keys [min max step]} (get-in FX-SLIDER-PARAMS [effect-name param-name])]
    (str "<div class=\"ctx-slider-row\">"
         "<label class=\"ctx-param-key\">" param-name "</label>"
         "<input type=\"range\" class=\"ctx-slider\""
         " data-track=\"" (cljs.core/name track-name) "\""
         " data-fx=\"" effect-name "\""
         " data-param=\"" param-name "\""
         " min=\"" min "\" max=\"" max "\" step=\"" step "\""
         " value=\"" value "\">"
         "<span class=\"ctx-param-val\">" (fmt-pv value) "</span>"
         "</div>")))

(defn- render-track-fx-subsection [track-name]
  (when-let [tn (get @audio/track-nodes track-name)]
    (let [active-fx (filterv #(not (:bypassed? %)) (:fx-chain tn))]
      (when (seq active-fx)
        (str "<details open class=\"ctx-track-fx\">"
             "<summary class=\"ctx-track-fx-title\">fx (" (count active-fx) ")</summary>"
             (apply str
               (map (fn [{:keys [name plugin]}]
                      (let [params    (try (js->clj (.getParams ^js plugin))
                                          (catch :default _ {}))
                            fx-sliders (get FX-SLIDER-PARAMS name)
                            sliders    (when fx-sliders
                                         (apply str
                                           (keep (fn [[pname _]]
                                                   (when-let [v (get params pname)]
                                                     (render-track-fx-slider track-name name pname v)))
                                                 fx-sliders)))]
                        (str "<div class=\"ctx-fx-row\">"
                             "<span class=\"ctx-fx-name\">" name "</span>"
                             "</div>"
                             (or sliders ""))))
                    active-fx))
             "</details>")))))



;;; Code patching for live slider updates

(defonce ^:private slider-timeout (atom nil))

(defn- patch-param-in-editor!
  "Find the first `(param-name NUMBER` in editor text, starting from the position
   of `:track-name`, and replace the number with new-val."
  [track-name param-name new-val]
  (when-let [view @editor-view]
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

(defn- slider-patch-and-eval! [track-name param-name new-val]
  (patch-param-in-editor! track-name param-name new-val)
  (when @slider-timeout (js/clearTimeout @slider-timeout))
  (reset! slider-timeout
    (js/setTimeout
      (fn []
        (reset! slider-timeout nil)
        (when-let [view @editor-view]
          (evaluate! (.. view -state -doc (toString)))))
      150)))

(defn- patch-fx-param-in-editor!
  "Update or insert a param in a (fx :effect-name ...) call.
   Named :param-name NUMBER exists -> replace the number.
   Positional (fx :effect-name NUMBER) for primary param -> replace.
   Not found -> insert :param-name value before the closing )."
  [effect-name param-name new-val]
  (when-let [view @editor-view]
    (let [doc      (.. view -state -doc (toString))
          primary? (= param-name (get FX-PRIMARY-PARAM effect-name))
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


(defn- fx-slider-patch-and-eval! [effect-name param-name new-val]
  (fx/set-param! effect-name param-name new-val)
  (patch-fx-param-in-editor! effect-name param-name new-val)
  (when @slider-timeout (js/clearTimeout @slider-timeout))
  (reset! slider-timeout
    (js/setTimeout
      (fn []
        (reset! slider-timeout nil)
        (when-let [view @editor-view]
          (evaluate! (.. view -state -doc (toString)))))
      150)))

(defn- patch-per-track-fx-param-in-editor!
  "Update or insert a param in (fx :effect-name ...) scoped to (track :track-name ...)."
  [track-name effect-name param-name new-val]
  (when-let [view @editor-view]
    (let [doc         (.. view -state -doc (toString))
          track-kw    (str "(track :" track-name)
          track-start (.indexOf doc track-kw)
          ;; Scope: from track-start to next (track : or end of doc
          next-start  (let [p (.indexOf doc "(track :" (inc track-start))]
                        (if (>= p 0) p (.-length doc)))
          sub-doc     (when (>= track-start 0) (.substring doc track-start next-start))
          primary?    (= param-name (get FX-PRIMARY-PARAM effect-name))
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

(defn- per-track-fx-slider-patch-and-eval! [track-name effect-name param-name new-val]
  (fx/set-track-param! (keyword track-name) effect-name param-name new-val)
  (patch-per-track-fx-param-in-editor! track-name effect-name param-name new-val)
  (when @slider-timeout (js/clearTimeout @slider-timeout))
  (reset! slider-timeout
    (js/setTimeout
      (fn []
        (reset! slider-timeout nil)
        (when-let [view @editor-view]
          (evaluate! (.. view -state -doc (toString)))))
      150)))


(defn- render-status-section []
  (let [bpm      (Math/round (/ 240.0 (:cycle-dur @audio/scheduler-state)))
        playing? (audio/playing?)
        backend  (if @audio/worklet-ready? "[wasm]" "[js]")
        pfx      @samples/active-bank-prefix]
    (str "<div class=\"ctx-status\">"
         "<span class=\"ctx-bpm\">" bpm " BPM</span>"
         "<span class=\"ctx-backend\">" backend "</span>"
         (when pfx (str "<span class=\"ctx-bank\">" pfx "</span>"))
         "<span class=\"" (if playing? "ctx-playing" "ctx-stopped") "\">"
         (if playing? "&#9679; playing" "&#9675; stopped")
         "</span>"
         "</div>")))

(defn- render-tracks-section []
  (let [state  @audio/scheduler-state
        tracks (:tracks state)
        muted  (:muted state)]
    (when (seq tracks)
      (let [n-active   (- (count tracks) (count muted))
            solo-track (when (and (> (count tracks) 1) (= n-active 1))
                         (first (remove #(contains? muted %) (keys tracks))))]
        (str "<div class=\"ctx-section\">"
             "<div class=\"ctx-section-title\">Tracks</div>"
             (apply str
               (map (fn [[track-name pattern]]
                      (let [muted?       (contains? muted track-name)
                            solo?        (= track-name solo-track)
                            icon         (cond muted? "&#9632;" solo? "&#9733;" :else "&#9654;")
                            params       (when-not muted? (extract-track-params pattern))
                            ;; Text params: non-numeric or not in SLIDER-PARAMS (synth, bank)
                            text-pkeys   (filter #(and (contains? params %)
                                                       (or (not (contains? SLIDER-PARAMS %))
                                                           (not (number? (get params %)))))
                                                 TRACK-PARAM-KEYS)
                            ;; Slider params: numeric values with range config
                            slider-pkeys (filter #(and (contains? params %)
                                                       (contains? SLIDER-PARAMS %)
                                                       (number? (get params %)))
                                                 TRACK-PARAM-KEYS)]
                        (str "<div class=\"ctx-track" (when muted? " ctx-track-muted") "\">"
                             "<span class=\"ctx-track-icon\">" icon "</span>"
                             "<span class=\"ctx-track-name\">:" (name track-name) "</span>"
                             (cond
                               muted? "<span class=\"ctx-track-status\">(muted)</span>"
                               solo?  "<span class=\"ctx-track-status ctx-track-solo\">(solo)</span>"
                               (seq text-pkeys)
                               (str "<span class=\"ctx-track-params\">"
                                    (apply str
                                      (map (fn [k]
                                             (str "<span class=\"ctx-param-key\">" (name k) " </span>"
                                                  "<span class=\"ctx-param-val\">" (fmt-pv (get params k)) "</span>"
                                                  " "))
                                           text-pkeys))
                                    "</span>")
                               :else "")
                             "</div>"
                             ;; Slider rows below track header
                             (when (and (not muted?) (seq slider-pkeys))
                               (apply str
                                 (map #(render-track-slider track-name % (get params %))
                                      slider-pkeys)))
                             ;; Per-track FX subsection
                             (render-track-fx-subsection track-name))))
                    (sort-by (comp name first) tracks)))
             "</div>")))))

(defn- render-fx-section []
  (let [active (filter :active? @fx/chain)]
    (when (seq active)
      (str "<div class=\"ctx-section\">"
           "<div class=\"ctx-section-title\">FX</div>"
           (apply str
             (map (fn [{:keys [name plugin bypassed?]}]
                    (let [params      (when (and plugin (not bypassed?))
                                        (try (js->clj (.getParams ^js plugin))
                                             (catch :default _ {})))
                          fx-sliders  (get FX-SLIDER-PARAMS name)
                          slider-html (when (and (not bypassed?) fx-sliders)
                                        (apply str
                                          (keep (fn [[pname _]]
                                                  (when-let [v (get params pname)]
                                                    (render-fx-slider name pname v)))
                                                fx-sliders)))
                          first-kv    (first (seq (apply dissoc params (keys fx-sliders))))
                          pstr        (when (and first-kv (not (seq slider-html)))
                                        (str (first first-kv) " "
                                             (fmt-pv (second first-kv))))]
                      (str "<div class=\"ctx-row\">"
                           "<span class=\"ctx-name\">" name "</span>"
                           (cond
                             bypassed? "<span class=\"ctx-bypass\">off</span>"
                             pstr      (str "<span class=\"ctx-param\">" pstr "</span>")
                             :else     "")
                           "</div>"
                           (or slider-html ""))))
                  active))
           "</div>"))))

(defn- render-midi-section []
  (let [mappings @midi/cc-mappings]
    (when (seq mappings)
      (str "<div class=\"ctx-section\">"
           "<div class=\"ctx-section-title\">MIDI</div>"
           (apply str
             (map (fn [[cc-num {:keys [target]}]]
                    (str "<div class=\"ctx-row\">"
                         "<span class=\"ctx-name\">CC #" cc-num "</span>"
                         "<span class=\"ctx-type\">"
                         (when target (str "&#8594; " (name target)))
                         "</span>"
                         "</div>"))
                  (sort-by key mappings)))
           "</div>"))))

(defn- render-sources-section []
  (let [sources @samples/loaded-sources]
    (when (seq sources)
      (str "<div class=\"ctx-section\">"
           "<div class=\"ctx-section-title\">Sources</div>"
           (apply str
             (map (fn [{:keys [type id banks query count]}]
                    (str "<div class=\"ctx-source\">&#9835; "
                         (case type
                           :github    (str "github:" id
                                          (when (pos? (or banks 0))
                                            (str " (" banks " banks)")))
                           :freesound (str "freesound: " query
                                          (when count (str " (" count ")")))
                           (str id))
                         "</div>"))
                  sources))
           "</div>"))))

(defn- render-buses-section []
  (let [buses (bus/active-buses)]
    (when (seq buses)
      (str "<div class=\"ctx-section\">"
           "<div class=\"ctx-section-title\">Buses</div>"
           (apply str
             (map (fn [[bus-name {:keys [type]}]]
                    (str "<div class=\"ctx-row\">"
                         "<span class=\"ctx-name\">" bus-name "</span>"
                         "<span class=\"ctx-type\">" (name type) "</span>"
                         "</div>"))
                  (sort-by (comp name key) buses)))
           "</div>"))))

(defn- render-bindings-section []
  (let [env      (or @env-atom {})
        builtins @builtin-names
        user-defs (sort (remove builtins (keys env)))]
    (when (seq user-defs)
      (str "<div class=\"ctx-section\">"
           "<div class=\"ctx-section-title\">Bindings</div>"
           (apply str
             (map (fn [k]
                    (str "<div class=\"ctx-row\">"
                         "<span class=\"ctx-name\">" k "</span>"
                         "<span class=\"ctx-type\">" (infer-type (get env k)) "</span>"
                         "</div>"))
                  user-defs))
           "</div>"))))

(defonce ^:private render-scheduled? (atom false))
(defonce ^:private slider-active? (atom false))   ; true while any ctx-slider is held

(defn- render-context-panel! []
  (when-let [panel-el (el "context-panel")]
    (set! (.-innerHTML panel-el)
          (str (render-status-section)
               (render-tracks-section)
               (render-fx-section)
               (render-midi-section)
               (render-buses-section)
               (render-sources-section)
               (render-bindings-section)))))

(defn- schedule-render! []
  ;; Skip tick-driven re-renders while the user is dragging a slider —
  ;; replacing the DOM element mid-drag breaks the native range interaction.
  (when (and (not @render-scheduled?) (not @slider-active?))
    (reset! render-scheduled? true)
    (js/requestAnimationFrame
      (fn []
        (reset! render-scheduled? false)
        (render-context-panel!)))))

;;; CodeMirror editor

;; On every document change, schedule a debounced full-session save.
(def ^:private save-listener
  (.of EditorView.updateListener
       (fn [^js update]
         (when (.-docChanged update)
           (session/schedule-save!)))))

;; Clear diagnostics immediately when the user starts editing, so stale
;; squiggles don't linger after the source has changed.
(def ^:private clear-diag-listener
  (.of EditorView.updateListener
       (fn [^js update]
         (when (.-docChanged update)
           (.dispatch (.-view update)
                      (setDiagnostics (.-state update) #js []))))))

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
                        (lintGutter)
                        save-listener
                        clear-diag-listener
                        (.-lineWrapping EditorView)
                        (.of keymap (.concat
                                     #js [escape-binding eval-binding upd-binding upd-f9-binding]
                                     (clj->js defaultKeymap)
                                     (clj->js historyKeymap)))]
        state (.. EditorState
                  (create #js {:doc initial-value
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
               "  <div id=\"context-panel\" class=\"context-panel\"></div>"
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

(defn- attach-slider-listener! []
  (when-let [panel (el "context-panel")]
    ;; Pause context panel re-renders while a slider is held down
    (.addEventListener panel "pointerdown"
      (fn [^js e]
        (when (.contains (.-classList (.-target e)) "ctx-slider")
          (reset! slider-active? true))))
    (.addEventListener js/document "pointerup"
      (fn [_]
        (when @slider-active?
          (reset! slider-active? false)
          ;; Trigger a deferred re-render once the drag is done
          (js/requestAnimationFrame render-context-panel!))))
    (.addEventListener panel "input"
      (fn [^js e]
        (let [target (.-target e)]
          (when (and (= "range" (.-type target))
                     (.contains (.-classList target) "ctx-slider"))
            (let [fx-name    (.. target -dataset -fx)
                  track-name (.. target -dataset -track)
                  param-name (.. target -dataset -param)
                  new-val    (js/parseFloat (.-value target))
                  row        (.-parentNode target)
                  val-el     (when row (.querySelector row ".ctx-param-val"))
                  fmtd       (if (== new-val (Math/round new-val))
                               (str (int new-val))
                               (.toFixed new-val 2))]
              (when val-el (set! (.-textContent val-el) fmtd))
              (cond
                ;; Per-track FX slider: has both data-track and data-fx
                (and (seq fx-name) (seq track-name))
                (per-track-fx-slider-patch-and-eval! track-name fx-name param-name new-val)
                ;; Global FX slider: has data-fx only
                (seq fx-name)
                (fx-slider-patch-and-eval! fx-name param-name new-val)
                ;; Track param slider: has data-track only
                :else
                (slider-patch-and-eval! track-name param-name new-val)))))))))

(defn init []
  (build-dom!)
  (attach-slider-listener!)
  (ensure-env!)
  (setBankNamesProvider (fn [] (clj->js (samples/bank-names))))
  (setFxNamesProvider   (fn [] (clj->js (mapv :name @fx/chain))))

  ;; Register global mute-toggle for track panel onclick
  (set! (.-_repulseMuteToggle js/window)
        (fn [track-name-str]
          (let [kw (keyword track-name-str)]
            (if (contains? (:muted @audio/scheduler-state) kw)
              (audio/unmute-track! kw)
              (audio/mute-track! kw)))))

  (reset! cmd-view (make-cmd-editor (el "cmd-container")))

  ;; ── Session restore ───────────────────────────────────────────────────────
  ;; Precedence: URL hash (#v2:/#v1:) > localStorage v2 > legacy keys > first visit
  (let [hash        (.-hash js/location)
        url-session (decode-session hash)
        stored      (or (session/load-session) (session/migrate-legacy!))
        active-sess (or url-session stored)
        init-text   (or (:editor active-sess) "")]

    ;; Remove hash from URL (avoid re-consuming on F5)
    (when url-session
      (.replaceState js/history nil nil (.-pathname js/location)))

    ;; Create editor with the session content (or empty for first-visit)
    (let [container (el "editor-container")
          view      (make-editor container init-text evaluate!)]
      (reset! editor-view view)
      (.focus view))

    ;; Register the editor-text getter for session snapshots
    (reset! session/editor-text-fn
      (fn [] (when-let [v @editor-view] (.. v -state -doc (toString)))))

    (if active-sess
      ;; ── Restore existing session ──────────────────────────────────────────
      (do
        (audio/set-bpm! (or (:bpm active-sess) 120))
        ;; Bank prefix (stored as string, set-bank-prefix! accepts string or keyword)
        (when (:bank active-sess)
          (samples/set-bank-prefix! (:bank active-sess)))
        ;; Re-fetch external github sample sources asynchronously
        (doseq [{:keys [type id]} (or (:sources active-sess) [])]
          (when (and id (= "github" (str type)))
            (samples/load-external! (str "github:" id))))
        ;; Restore effect params + bypass after plugins finish loading (~500ms)
        (js/setTimeout
          (fn []
            (doseq [{:keys [name params bypassed]} (or (:fx active-sess) [])]
              (when name
                (doseq [[k v] (or params {})]
                  (fx/set-param! name (cljs.core/name k) v))
                (when bypassed
                  (fx/bypass! name true))))
            ;; set-param! marks effects :active? true — reset so the context panel
            ;; only shows effects that the user's code explicitly activates via (fx ...).
            (swap! fx/chain (fn [c] (mapv #(assoc % :active? false) c)))
            ;; Store mutes for application after the user's first eval creates tracks
            (reset! pending-mutes (set (map keyword (or (:muted active-sess) [])))))
          500))

      ;; ── First visit / after (reset!) ─────────────────────────────────────
      (first-visit-setup!)))

  ;; ── Session save watchers ────────────────────────────────────────────────
  ;; Debounced; the editor's save-listener covers document changes.
  (add-watch audio/scheduler-state  :session-save (fn [_ _ _ _] (session/schedule-save!)))
  (add-watch fx/chain               :session-save (fn [_ _ _ _] (session/schedule-save!)))
  (add-watch samples/active-bank-prefix :session-save (fn [_ _ _ _] (session/schedule-save!)))
  (add-watch samples/loaded-sources     :session-save (fn [_ _ _ _] (session/schedule-save!)))

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
  (-> (dynamic-import! "/plugins/spectrum.js")
      (.then (fn [m]
               (let [plug (.-default m)]
                 (plugins/register! plug (make-host))
                 (mount-visual! plug))))
      (.catch (fn [e]
                (js/console.warn "[REPuLse] spectrum load failed:" e))))

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
               "/plugins/bitcrusher.js"
               "/plugins/sidechain.js"]]
    (-> (dynamic-import! url)
        (.then (fn [m]
                 (let [plug (.-default m)]
                   (plugins/register! plug (make-host))
                   (fx/add-effect! plug))))
        (.catch (fn [e]
                  (js/console.warn "[REPuLse] Effect load failed:" url e)))))

  ;; Reactive context panel + track panel
  (add-watch env-atom                   ::ctx (fn [_ _ _ _] (schedule-render!)))
  (add-watch fx/chain                   ::ctx (fn [_ _ _ _] (schedule-render!)))
  (add-watch audio/scheduler-state      ::ctx (fn [_ _ _ _]
                                                (schedule-render!)
                                                (render-track-panel!)))
  (add-watch samples/active-bank-prefix ::ctx (fn [_ _ _ _] (schedule-render!)))
  (add-watch samples/loaded-sources     ::ctx (fn [_ _ _ _] (schedule-render!)))
  (add-watch midi/cc-mappings           ::ctx (fn [_ _ _ _] (schedule-render!)))

  (render-context-panel!)
  (start-playhead-raf!))

(defn reload []
  ;; Hot-reload hook: reset env so new/changed built-in bindings take effect
  ;; without a full page reload.  User defs are lost but that is expected.
  (reset! env-atom nil))
