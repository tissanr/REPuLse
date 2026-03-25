(ns repulse.midi)

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
  (swap! cc-mappings assoc cc-num
         {:target target-kw :min 0.0 :max 1.0 :on-change on-change-fn})
  (register-cc-listener!))

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

(defn start-clock!
  "Start sending 24ppqn MIDI clock at the given BPM.
   Sends MIDI Start (0xFA) then begins 24ppqn pulse interval."
  [bpm]
  (stop-clock!)
  (send-clock-byte! 0xFA)   ; MIDI Start
  (let [interval-ms (* (/ 60.0 bpm 24.0) 1000)]
    (reset! clock-interval-id
            (js/setInterval #(send-clock-byte! 0xF8) interval-ms))))

(defn stop-clock!
  "Stop MIDI clock output, sending MIDI Stop (0xFC)."
  []
  (when-let [id @clock-interval-id]
    (js/clearInterval id)
    (reset! clock-interval-id nil)
    (send-clock-byte! 0xFC)))   ; MIDI Stop

(defn update-clock-bpm!
  "Update clock tempo in-place (restarts the interval)."
  [bpm]
  (when @clock-interval-id
    (start-clock! bpm)))

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

(defn export-midi
  "Export a list of note events to a Standard MIDI File (Type 0).
   Each event: {:time-sec f :duration-sec f :midi-note n :channel n}
   Returns a Uint8Array."
  [events bpm]
  (let [ppqn        480
        us-per-beat (int (/ 60000000.0 bpm))
        sorted      (sort-by :time-sec events)
        sec->tick   (fn [s] (int (* s (/ bpm 60.0) ppqn)))
        ;; Tempo meta event: FF 51 03 tt tt tt
        tempo-evt   (concat [0x00 0xFF 0x51 0x03]
                            [(bit-and (unsigned-bit-shift-right us-per-beat 16) 0xFF)
                             (bit-and (unsigned-bit-shift-right us-per-beat 8) 0xFF)
                             (bit-and us-per-beat 0xFF)])
        ;; Build note on/off pairs, sorted by onset tick
        track-bytes (:bytes
                      (reduce
                        (fn [{:keys [bytes last-tick]}
                             {:keys [time-sec duration-sec midi-note channel]}]
                          (let [ch       (dec (or channel 1))
                                on-tick  (sec->tick time-sec)
                                off-tick (sec->tick (+ time-sec duration-sec))
                                delta-on (- on-tick last-tick)
                                on-bytes (concat (write-vlq delta-on)
                                                 [(+ 0x90 ch) midi-note 100])
                                delta-off (- off-tick on-tick)
                                off-bytes (concat (write-vlq delta-off)
                                                  [(+ 0x80 ch) midi-note 0])]
                            {:bytes (into bytes (concat on-bytes off-bytes))
                             :last-tick off-tick}))
                        {:bytes [] :last-tick 0}
                        sorted))
        eot         [0x00 0xFF 0x2F 0x00]
        mtk-data    (vec (concat tempo-evt track-bytes eot))
        header      (concat [0x4D 0x54 0x68 0x64]   ; MThd
                            (write-u32 6)             ; header length
                            (write-u16 0)             ; format 0
                            (write-u16 1)             ; 1 track
                            (write-u16 ppqn))
        trk-header  (concat [0x4D 0x54 0x72 0x6B]   ; MTrk
                            (write-u32 (count mtk-data)))]
    (js/Uint8Array. (clj->js (concat header trk-header mtk-data)))))
