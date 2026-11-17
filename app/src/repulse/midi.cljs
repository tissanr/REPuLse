(ns repulse.midi
  (:require [repulse.specs :as specs]))

;;; ── State ──────────────────────────────────────────────────────────

(defonce midi-access       (atom nil))
(defonce cc-mappings       (atom {}))   ; {cc-num {:target kw :min f :max f :on-change fn}}
(defonce clock-interval-id (atom nil))
(defonce midi-outputs      (atom []))

;;; ── Access ─────────────────────────────────────────────────────────

(defn ensure-access!
  "Request MIDI access if not already granted. Returns a Promise."
  []
  (if @midi-access
    (js/Promise.resolve @midi-access)
    (if-not (.-requestMIDIAccess js/navigator)
      (js/Promise.reject
        (js/Error. "MIDI not supported in this browser — use Chrome or Edge"))
      (-> (.requestMIDIAccess js/navigator #js {:sysex false})
          (.then (fn [access]
                   (reset! midi-access access)
                   (let [outs (array)]
                     (.forEach (.-outputs access) #(.push outs %))
                     (reset! midi-outputs (vec outs)))
                   access))))))

;;; ── Input: CC mapping ──────────────────────────────────────────────

(defn- on-midi-message [event]
  (let [data   (.-data event)
        status (aget data 0)
        cmd    (bit-and status 0xF0)]
    (when (= cmd 0xB0)
      (let [cc-num (aget data 1)
            value  (aget data 2)]   ; 0–127
        (when-let [mapping (get @cc-mappings cc-num)]
          (let [{:keys [min max on-change]} mapping
                normalized (/ value 127.0)
                scaled     (+ min (* normalized (- max min)))]
            (when on-change
              (on-change (:target mapping) scaled))))))))

(defn- register-cc-listener!
  "Attach CC listener to all currently known MIDI inputs."
  []
  (when-let [access @midi-access]
    (.forEach (.-inputs access)
              (fn [input]
                (set! (.-onmidimessage input) on-midi-message)))))

(defn map-cc!
  "Map a MIDI CC number to a parameter target.
   on-change-fn is called with (target-kw scaled-value) on each CC message."
  [cc-num target-kw on-change-fn]
  (let [mapping {:target target-kw :min 0.0 :max 1.0 :on-change on-change-fn}]
    (if (and (integer? cc-num) (<= 0 cc-num 127) (specs/midi-cc-mapping? mapping))
      (do
        (swap! cc-mappings assoc cc-num mapping)
        (register-cc-listener!))
      (js/console.warn "[REPuLse] Ignoring invalid MIDI CC mapping:" cc-num target-kw))))

;;; ── Output: Note On/Off ────────────────────────────────────────────

(defn hz->midi
  "Convert Hz frequency to MIDI note number (0–127)."
  [hz]
  (let [m (+ 69 (* 12 (/ (Math/log (/ hz 440.0)) (Math/log 2))))]
    (Math/round (Math/max 0 (Math/min 127 m)))))

(defn send-note-on!
  "Send MIDI Note On on the given channel (1-indexed) to all outputs.
   timestamp is a performance.now() value in ms (nil = send immediately)."
  [channel note velocity timestamp]
  (let [status (+ 0x90 (dec channel))
        msg    (js/Uint8Array. #js [status note velocity])]
    (doseq [^js output @midi-outputs]
      (if timestamp
        (.send output msg timestamp)
        (.send output msg)))))

(defn send-note-off!
  "Send MIDI Note Off on the given channel to all outputs."
  [channel note timestamp]
  (let [status (+ 0x80 (dec channel))
        msg    (js/Uint8Array. #js [status note 0])]
    (doseq [^js output @midi-outputs]
      (if timestamp
        (.send output msg timestamp)
        (.send output msg)))))

;;; ── Output: Clock ──────────────────────────────────────────────────

(defn- send-clock-byte! [byte-val]
  (let [msg (js/Uint8Array. #js [byte-val])]
    (doseq [^js output @midi-outputs]
      (.send output msg))))

(declare stop-clock!)

(defn- start-pulses! [bpm]
  (let [interval-ms (* (/ 60.0 bpm 24.0) 1000)]
    (reset! clock-interval-id
            (js/setInterval #(send-clock-byte! 0xF8) interval-ms))))

(defn start-clock!
  "Start sending 24ppqn MIDI clock at the given BPM.
   Sends MIDI Start (0xFA) then begins 24ppqn pulse interval."
  [bpm]
  (stop-clock!)
  (send-clock-byte! 0xFA)   ; MIDI Start
  (start-pulses! bpm))

(defn stop-clock!
  "Stop MIDI clock output, sending MIDI Stop (0xFC)."
  []
  (when-let [id @clock-interval-id]
    (js/clearInterval id)
    (reset! clock-interval-id nil)
    (send-clock-byte! 0xFC)))   ; MIDI Stop

(defn update-clock-bpm!
  "Change the tempo of a running clock. Only the pulse interval is restarted —
   no MIDI Stop/Start, so downstream sequencers keep their song position."
  [bpm]
  (when-let [id @clock-interval-id]
    (js/clearInterval id)
    (start-pulses! bpm)))

;;; ── MIDI file export ───────────────────────────────────────────────

(defn- write-vlq
  "Encode n as a MIDI variable-length quantity."
  [n]
  (if (< n 128)
    [n]
    (loop [v n acc []]
      (if (zero? v)
        (let [rev (vec (reverse acc))]
          (into (mapv #(bit-or % 0x80) (butlast rev)) [(last rev)]))
        (recur (unsigned-bit-shift-right v 7)
               (conj acc (bit-and v 0x7F)))))))

(defn- write-u16 [n]
  [(bit-and (unsigned-bit-shift-right n 8) 0xFF)
   (bit-and n 0xFF)])

(defn- write-u32 [n]
  [(bit-and (unsigned-bit-shift-right n 24) 0xFF)
   (bit-and (unsigned-bit-shift-right n 16) 0xFF)
   (bit-and (unsigned-bit-shift-right n 8) 0xFF)
   (bit-and n 0xFF)])

(def ppqn 480)
(def ticks-per-bar (* 4 ppqn))   ; 1 cycle = 1 bar = 4 quarter notes (K9)

(defn amp->velocity
  "Decision 3 / K15: amp 0–1 → velocity 1–127, linear.
   A missing, non-numeric or NaN amp counts as 1.0 → 127."
  [amp]
  (if (and (number? amp) (not (js/isNaN amp)))
    (-> (Math/round (* amp 127)) (Math/min 127) (Math/max 1))
    127))

(defn tempo-us
  "K11: microseconds per quarter note for a cycle of cycle-dur seconds
   (60 000 000 / bpm with bpm = 240 / cycle-dur), rounded."
  [cycle-dur]
  (Math/round (* cycle-dur 250000)))

(defn- write-u24 [n]
  [(bit-and (unsigned-bit-shift-right n 16) 0xFF)
   (bit-and (unsigned-bit-shift-right n 8) 0xFF)
   (bit-and n 0xFF)])

(defn- utf8-bytes [s]
  (vec (array-seq (.encode (js/TextEncoder.) s))))

(defn- meta-text [type text]
  (let [bs (utf8-bytes text)]
    (into [0xFF type] (concat (write-vlq (count bs)) bs))))

(defn- smf-chunk [tag data]
  (vec (concat tag (write-u32 (count data)) data)))

(def ^:private mtrk [0x4D 0x54 0x72 0x6B])

(defn- with-deltas
  "events [[tick bytes] …] sorted by tick, then an end-of-track at end-tick →
   flat byte vector of delta-time + event pairs."
  [events end-tick]
  (let [end (reduce max end-tick (map first events))
        all (conj (vec events) [end [0xFF 0x2F 0x00]])]
    (:bytes
     (reduce (fn [{:keys [bytes last]} [tick evt]]
               {:bytes (into (into bytes (write-vlq (- tick last))) evt)
                :last  tick})
             {:bytes [] :last 0}
             all))))

(defn- conductor-track [cycle-dur markers end-tick]
  (let [us (tempo-us cycle-dur)
        head (vec (concat [0x00 0xFF 0x58 0x04 0x04 0x02 0x18 0x08]
                          [0x00 0xFF 0x51 0x03] (write-u24 us)))
        evts (mapv (fn [{:keys [tick text]}] [tick (meta-text 0x06 text)])
                   (sort-by :tick markers))]
    (smf-chunk mtrk (into head (with-deltas evts end-tick)))))

(defn- note-track [name' notes end-tick]
  (let [evts (->> notes
                  (filter specs/midi-export-event?)
                  (mapcat (fn [{:keys [tick dur note velocity channel]}]
                            (let [ch (dec channel)]
                              [[[tick 1 channel note] [tick [(+ 0x90 ch) note velocity]]]
                               [[(+ tick dur) 0 channel note]
                                [(+ tick dur) [(+ 0x80 ch) note 0]]]])))
                  (sort-by first)
                  (mapv second))
        head (into [0x00] (meta-text 0x03 name'))]
    (smf-chunk mtrk (into head (with-deltas evts end-tick)))))

(defn midi-file
  "Pure. → Uint8Array of a format-1 Standard MIDI File (K9, K16).
   {:cycle-dur cd :end-tick t :markers [{:tick t :text str}]
    :tracks [{:name str :notes [{:tick t :dur d :note n :velocity v :channel ch}]}]}
   Track 0 is the conductor (time signature, tempo, section markers)."
  [{:keys [cycle-dur end-tick markers tracks]}]
  (let [end-tick (or end-tick 0)
        header   (vec (concat [0x4D 0x54 0x68 0x64]
                              (write-u32 6)
                              (write-u16 1)
                              (write-u16 (inc (count tracks)))
                              (write-u16 ppqn)))
        body     (into (conductor-track cycle-dur markers end-tick)
                       (mapcat #(note-track (:name %) (:notes %) end-tick))
                       tracks)]
    (js/Uint8Array. (clj->js (into header body)))))
