(ns repulse.offline-render
  "Phase EXP1 — faster-than-real-time export that sounds like playback.
   Builds an OfflineAudioContext graph that mirrors the real-time graph
   (master gain → global FX chain → destination; per-track FX → master gain),
   loads the WASM worklet into it, schedules the export's events through the
   shared audio/play-event dispatch and renders. No DOM, no WAV encoding."
  (:require [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.bus :as bus]
            [repulse.core :as core]
            [repulse.song :as song]))

;; Silent pre-roll trimmed from the result, so parameter glides (filter
;; setTargetAtTime, cab ramps) settle before bar 0 (decision E4).
(def pre-roll-secs 0.25)

(def ^:private ready-timeout-ms 5000)

(defn- fallback-error
  "Rejection value that makes the export fall back to real-time capture."
  [reason]
  (ex-info reason {:fallback true :reason reason}))

(defn- plugin-failure [plugin-name reason]
  (str "plugin \"" plugin-name "\" can't be rebuilt offline (" reason ")"))

;;; ── Pure planning ──────────────────────────────────────────────────────

(defn- plugin-type [^js p]
  (when p (.-type p)))

(defn- default-get-params? [^js p]
  (boolean (and p (aget p "__repulseDefaultGetParams"))))

(defn fx-plan
  "Pure. entries — global chain entries ({:name :plugin …}); used — set of
   effect names in use (active global entries + names in exported tracks'
   :track-fx). Visual plugins are skipped (they don't change the sound); a used
   plugin whose getParams is the installed default can't be rebuilt (E8).
   → {:build [entry…] :skip [name…] :fail {:plugin name :reason str} or nil}"
  [entries used]
  (reduce (fn [acc {:keys [name plugin] :as entry}]
            (cond
              (= "visual" (plugin-type plugin))
              (update acc :skip conj name)

              (and (contains? used name) (default-get-params? plugin))
              (cond-> acc
                (nil? (:fail acc)) (assoc :fail {:plugin name :reason "no getParams"}))

              :else
              (update acc :build conj entry)))
          {:build [] :skip [] :fail nil}
          entries))

(defn used-effects
  "Names of the effects an export uses: active global chain entries plus every
   effect named in the exported tracks' :track-fx metadata."
  [entries tracks]
  (into (set (keep #(when (:active? %) (:name %)) entries))
        (for [[_ pat] tracks
              {:keys [name]} (:track-fx pat)]
          name)))

(defn fx-plan-reason
  "The fallback reason for a failed plan, or nil."
  [plan]
  (when-let [{:keys [plugin reason]} (:fail plan)]
    (plugin-failure plugin reason)))

(defn job-frames
  "Frames of the rendered result (without pre-roll) for a job at sample rate sr."
  [{:keys [kind n tail cycle-dur] s :song} sr]
  (case kind
    :cycles (audio/export-frames (* n cycle-dur) sr)
    :song   (audio/export-frames (+ (song/song-duration-secs s (/ 240.0 cycle-dur))
                                    (or tail 0))
                                 sr)))

(defn job-events
  "Pure. The export's onsets as [{:track kw :event ev :t secs}] with t relative
   to bar 0 (exports ignore mute, E12).
   :cycles — cycles 0…n−1 of every track; :song — one pass of the song."
  [{:keys [kind tracks n cycle-dur] s :song}]
  (case kind
    :cycles
    (vec
     (for [c         (range n)
           [kw pat]  tracks
           :when     pat
           ev        (core/query-onsets pat {:start [c 1] :end [(inc c) 1]})
           :let      [t (* (core/rat->float (:start (:whole ev))) cycle-dur)]
           :when     (>= t 0)]
       {:track kw :event ev :t t}))

    :song
    (vec
     (for [bar (range (song/song-length s))
           {:keys [track event]} (song/song-onsets s tracks #{} bar)]
       {:track track :event event
        :t (* (core/rat->float (:start (:whole event))) cycle-dur)}))))

;;; ── Graph building ─────────────────────────────────────────────────────

(defn- offline-host [^js offline sr master]
  #js {:audioCtx     offline
       :sampleRate   sr
       :masterGain   master
       :analyser     nil
       :version      "1.0.0"
       :registerLisp (fn [_ _] nil)})

(defn- build-instance!
  "Instantiate effect-name's registered plugin in the offline context (decision
   2 / E7): fresh instance → init(offline host) → createNodes → validate.
   → {:name :plugin :input :output}. Throws a fallback error on failure."
  [effect-name ^js plugin ^js offline host]
  (try
    (let [^js fresh (fx/instantiate-plugin plugin)]
      (when (fn? (.-init fresh)) (.init fresh host))
      (let [^js nodes (fx/validate-effect-nodes! effect-name (.createNodes fresh offline))]
        {:name   effect-name
         :plugin fresh
         :input  (.-inputNode nodes)
         :output (.-outputNode nodes)}))
    (catch :default e
      (throw (fallback-error (plugin-failure effect-name (or (ex-message e) (str e))))))))

(defn- replay-global-params!
  "Copy the live state of a global entry onto its offline instance: getParams
   → setParam (decision 2), then the set-param! log (E6), then bypass."
  [{:keys [name plugin bypassed?]} ^js fresh]
  (let [^js live plugin]
    (when (fn? (.-getParams live))
      (let [ps (.getParams live)]
        (when ps
          (doseq [[k v] (js/Object.entries ps)]
            (when (some? v) (.setParam fresh k v))))))
    (doseq [[k v] (get @fx/param-log name)]
      (.setParam fresh k v))
    (when bypassed?
      (.bypass fresh true))))

(defn- wire-chain!
  "Connect from → e1 → … → eN → to (from → to when empty)."
  [^js from instances ^js to]
  (if (empty? instances)
    (.connect from to)
    (do (.connect from (:input (first instances)))
        (doseq [[a b] (partition 2 1 instances)]
          (.connect ^js (:output a) (:input b)))
        (.connect ^js (:output (last instances)) to))))

(defn- build-graph!
  "Build master + global chain + per-track chains in offline.
   → {:master node :instances [inst…] :track-dests {kw node}}."
  [^js offline sr plan tracks]
  (let [master   (.createGain offline)
        _        (set! (.. master -gain -value)
                       (if-let [^js mg @audio/master-gain] (.. mg -gain -value) 1.0))
        host     (offline-host offline sr master)
        global   (vec (for [{:keys [name plugin] :as entry} (:build plan)]
                        (let [inst (build-instance! name plugin offline host)]
                          (replay-global-params! entry (:plugin inst))
                          inst)))
        _        (wire-chain! master global (.-destination offline))
        by-name  (into {} (map (juxt :name :plugin) (:build plan)))
        tracks'  (vec
                  (for [[kw pat] tracks
                        :let [insts (vec
                                     (for [{:keys [name params]} (:track-fx pat)
                                           :let [p (get by-name name)]
                                           :when p]
                                       (let [inst (build-instance! name p offline host)]
                                         ;; same as fx/apply-track-effects!: metadata params only
                                         (doseq [[k v] params]
                                           (.setParam ^js (:plugin inst) k v))
                                         inst)))]
                        :when (seq insts)]
                    (let [gain (.createGain offline)]
                      (wire-chain! gain insts master)
                      [kw insts])))]
    {:master      master
     :instances   (into global (mapcat second tracks'))
     :track-dests (into {} (map (fn [[kw insts]] [kw (:input (first insts))]) tracks'))}))

(defn- await-ready!
  "Promise.all of ready() of every instance that has it, with a 5 s timeout.
   Rejects with a fallback error naming the plugin."
  [instances]
  (let [pending (atom (set (map :name (filter #(fn? (.-ready ^js (:plugin %))) instances))))
        waits   (for [{:keys [name plugin]} instances
                      :when (fn? (.-ready ^js plugin))]
                  (-> (js/Promise.resolve (.ready ^js plugin))
                      (.then (fn [_] (swap! pending disj name) true))
                      (.catch (fn [e]
                                (throw (fallback-error
                                        (plugin-failure name (or (ex-message e) (str e)))))))))
        timeout (js/Promise.
                 (fn [_ reject]
                   (js/setTimeout
                    #(reject (fallback-error
                              (plugin-failure (or (first @pending) "?")
                                              "not ready after 5 s")))
                    ready-timeout-ms)))]
    (js/Promise.race #js [(js/Promise.all (into-array waits)) timeout])))

;;; ── Render ─────────────────────────────────────────────────────────────

(defn- thenable? [x]
  (and (some? x) (fn? (.-then ^js x))))

(defn- precondition-failure
  "Reason the offline path can't run at all, or nil."
  [plan]
  (cond
    (or (not (exists? js/OfflineAudioContext))
        (not (exists? js/AudioWorkletNode)))
    "AudioWorklet unavailable offline"

    (seq (bus/active-buses))
    "buses (bus …) are not rendered offline yet"

    :else (fx-plan-reason plan)))

(defn render!
  "job — {:kind :cycles|:song, :tracks {kw pattern}, :song s (for :song), :n n,
          :tail secs, :cycle-dur cd}
   → Promise<{:left Float32Array :right Float32Array :sample-rate sr :frames n}>;
   rejects with (ex-info reason {:fallback true}) when the offline path can't run."
  [{:keys [tracks cycle-dur] :as job}]
  (let [entries @fx/chain
        plan    (fx-plan entries (used-effects entries tracks))]
    (if-let [reason (precondition-failure plan)]
      (js/Promise.reject (fallback-error reason))
      (let [sr      (.-sampleRate ^js (audio/get-ctx))
            pre     (audio/export-frames pre-roll-secs sr)
            frames  (job-frames job sr)
            offline (js/OfflineAudioContext. 2 (+ pre frames) sr)]
        (if (nil? (.-audioWorklet offline))
          (js/Promise.reject (fallback-error "AudioWorklet unavailable offline"))
          (-> (js/Promise.resolve nil)
              (.then (fn [_] (build-graph! offline sr plan tracks)))
              (.then
               (fn [{:keys [master instances track-dests]}]
                 (-> (audio/init-offline-worklet! offline master)
                     (.catch (fn [e]
                               (throw (fallback-error
                                       (str "WASM worklet failed to start offline: "
                                            (or (ex-message e) (str e)))))))
                     (.then (fn [node]
                              (-> (await-ready! instances)
                                  (.then (fn [_] {:node node :master master
                                                  :instances instances
                                                  :track-dests track-dests}))))))))
              (.then
               (fn [{:keys [node master instances track-dests]}]
                 (audio/register-offline-graph! offline {:master-gain  master
                                                         :worklet-node node
                                                         :track-dests  track-dests})
                 (let [plugins  (map :plugin instances)
                       tween-st (into {} (for [[kw pat] tracks]
                                           [kw (audio/tween-state-for pat pre-roll-secs)]))
                       ;; Sample voices schedule their source once the buffer is
                       ;; fetched (samples/play! returns that promise): wait for
                       ;; all of them, so no sample is created after rendering starts.
                       pending  (into []
                                      (keep (fn [{:keys [track event t]}]
                                              (let [t'    (+ pre-roll-secs t)
                                                    value (audio/resolve-tweens
                                                           (:value event) (get tween-st track)
                                                           t' cycle-dur)
                                                    ret   (audio/play-event offline t' value track)]
                                                (fx/notify-plugins! plugins value t')
                                                (when (thenable? ret)
                                                  (.catch ret (fn [_] nil))))))
                                      (job-events job))]
                   (-> (js/Promise.all (into-array pending))
                       (.then (fn [_] (audio/sync-worklet! node)))))))
              (.then (fn [_] (.startRendering offline)))
              (.then (fn [^js buf]
                       {:left        (.slice (.getChannelData buf 0) pre (+ pre frames))
                        :right       (.slice (.getChannelData buf 1) pre (+ pre frames))
                        :sample-rate sr
                        :frames      frames}))))))))
