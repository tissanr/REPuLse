(ns repulse.audio-clock
  "Pure mapping from AudioContext time to performance.now() time.

   The scheduler places sounds on the AudioContext clock, but visuals (editor
   highlights, the beat dot) and MIDI timestamps live on the performance.now()
   clock. A sound scheduled at context time t is *heard* later than the moment
   currentTime reaches t, by the output latency of the device — which differs
   per browser (Safari's is larger and its currentTime advances in coarse
   render-quantum steps) and is large on Bluetooth outputs.

   Everything here is a pure function of an injected clock snapshot so it can be
   unit-tested; repulse.audio takes the snapshot from the live context.")

;; An output timestamp older than this is treated as stale (e.g. suspended context).
(def ^:private max-output-ts-age-ms 500)

(defn- positive? [x]
  (and (number? x) (not (js/isNaN x)) (pos? x)))

(defn latency-s
  "Output latency in seconds: outputLatency when the browser reports it,
   else baseLatency, else 0. Safari often lacks outputLatency or reports 0."
  [{:keys [output-latency base-latency]}]
  (cond
    (positive? output-latency) output-latency
    (positive? base-latency)   base-latency
    :else                      0))

(defn sane-output-ts?
  "True when an AudioContext.getOutputTimestamp() result is usable: both fields
   positive, not ahead of the clocks they were sampled against, and recent."
  [{:keys [output-ts current-time perf-now]}]
  (let [{:keys [context-time performance-time]} output-ts]
    (boolean
     (and (positive? context-time)
          (positive? performance-time)
          (<= context-time (+ current-time 0.001))
          (<= performance-time (+ perf-now 1))
          (< (- perf-now performance-time) max-output-ts-age-ms)))))

(defn audible-perf-ms
  "performance.now() timestamp (ms) at which a sound scheduled at context time
   t (seconds) is heard.

   snapshot — {:current-time   ac.currentTime (s)
               :perf-now       performance.now() sampled with it (ms)
               :output-ts      {:context-time s :performance-time ms} or nil
               :output-latency ac.outputLatency (s) or nil
               :base-latency   ac.baseLatency (s) or nil}

   getOutputTimestamp() already describes when a context-time sample leaves the
   speakers, so latency is added only on the fallback path."
  [snapshot t]
  (if (sane-output-ts? snapshot)
    (let [{:keys [context-time performance-time]} (:output-ts snapshot)]
      (+ performance-time (* 1000 (- t context-time))))
    (+ (:perf-now snapshot)
       (* 1000 (- t (:current-time snapshot)))
       (* 1000 (latency-s snapshot)))))

;;; Frame queue — pending visual callbacks keyed by audible time

(defn enqueue
  "Add entry {:at ms ...} to queue, keeping it sorted by :at."
  [queue entry]
  (vec (sort-by :at (conj queue entry))))

(defn split-due
  "Split queue for a frame at time now (ms). Entries due within half a frame
   (slack-ms) fire on this frame — the frame closest to their audible time.
   Entries more than stale-ms late (e.g. the tab was hidden and rAF paused)
   are dropped rather than fired in a burst.
   Returns {:due [...] :pending [...]}."
  [queue now slack-ms stale-ms]
  (let [[ready pending] (split-with #(<= (:at %) (+ now slack-ms)) queue)]
    {:due     (filterv #(>= (:at %) (- now stale-ms)) ready)
     :pending (vec pending)}))
