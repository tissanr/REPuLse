(ns repulse.song
  "Song form: named sections with bar lengths and per-section track lists.
   Pure — no audio, no DOM, no Lisp. 1 bar = 1 cycle; bars are 0-based integers."
  (:require [repulse.core :as core]))

;;; ── Helpers ───────────────────────────────────────────────────────

(defn- song-error [msg data]
  (ex-info msg (merge {:type :song-error} data)))

(defn- kw-str [k] (str ":" (name k)))

(defn- fmt-value
  "Readable rendering of a user value for error messages."
  [v]
  (cond
    (and (vector? v) (= 2 (count v)) (every? integer? v)) (str (first v) "/" (second v))
    (core/pattern? v) "a pattern"
    (fn? v)           "a function"
    :else             (pr-str v)))

(defn- whole-bars? [bars]
  (and (number? bars) (integer? bars) (>= bars 1)))

;;; ── Sections and songs ────────────────────────────────────────────

(defn section? [x] (and (map? x) (= :section (:type x))))
(defn song?    [x] (and (map? x) (= :song (:type x))))

(defn make-section
  "Build a section map. name — keyword; bars — integer ≥ 1;
   tracks — :all or a vector of track keywords (duplicates removed, order kept)."
  [name bars tracks]
  (when-not (keyword? name)
    (throw (song-error (str "section: name must be a keyword like :verse, got " (fmt-value name))
                       {:arg :name})))
  (when-not (whole-bars? bars)
    (throw (song-error (str "section " (kw-str name) ": length must be a whole number of bars ≥ 1, got "
                            (fmt-value bars))
                       {:arg :bars :section name})))
  (when-not (or (= tracks :all)
                (and (vector? tracks) (every? keyword? tracks)))
    (throw (song-error (str "section " (kw-str name) ": tracks must be :all or a vector of track names like [:kick :snare], got "
                            (fmt-value tracks))
                       {:arg :tracks :section name})))
  {:type   :section
   :name   name
   :bars   bars
   :tracks (if (= tracks :all) :all (vec (distinct tracks)))})

(defn make-song
  "Build a song value from section maps.
   opts         — {:end :stop|:loop} (default :stop)
   known-tracks — vector of track keywords registered above the song (buffer order)
   hint-fn      — (fn [name-str known-strs] → closest known str or nil)"
  [sections opts known-tracks hint-fn]
  (when (empty? sections)
    (throw (song-error "song needs at least one (section …)" {})))
  (let [end   (or (:end opts) :stop)
        known (vec known-tracks)
        kset  (set known)]
    (when-not (#{:stop :loop} end)
      (throw (song-error (str "song: :end must be :stop or :loop, got " (fmt-value end)) {:option :end})))
    ;; Duplicate section names
    (reduce (fn [seen {:keys [name]}]
              (when (contains? seen name)
                (throw (song-error (str "Duplicate section name " (kw-str name)
                                        " — section names must be unique in a song")
                                   {:section name})))
              (conj seen name))
            #{} sections)
    ;; Unknown track names
    (doseq [{:keys [name tracks]} sections
            :when (vector? tracks)
            t tracks
            :when (not (contains? kset t))]
      (let [hint (when hint-fn (hint-fn (kw-str t) (mapv kw-str known)))]
        (throw (song-error (str "Unknown track " (kw-str t) " in section " (kw-str name)
                                (cond
                                  hint          (str " — did you mean " hint "?")
                                  (seq known)   (str " — tracks defined above the song: "
                                                     (apply str (interpose " " (map kw-str known))))
                                  :else         " — define tracks with (track :name …) above the song"))
                           {:section name :track t}))))
    (let [resolved (loop [secs sections start 0 acc []]
                     (if (empty? secs)
                       acc
                       (let [s    (first secs)
                             all? (= :all (:tracks s))
                             end' (+ start (:bars s))]
                         (recur (rest secs) end'
                                (conj acc (cond-> (assoc s
                                                         :tracks (if all? known (:tracks s))
                                                         :start start
                                                         :end end')
                                            all? (assoc :all? true)))))))]
      {:type     :song
       :end      end
       :total    (:end (peek resolved))
       :tracks   known
       :sections resolved})))

;;; ── Queries ───────────────────────────────────────────────────────

(defn song-length [song] (:total song))

(defn section-start-bar [song section-name]
  (some #(when (= section-name (:name %)) (:start %)) (:sections song)))

(defn section-at-bar
  "Section with start ≤ bar < end, or nil. No wrapping."
  [song bar]
  (when (and (integer? bar) (>= bar 0))
    (some #(when (and (<= (:start %) bar) (< bar (:end %))) %) (:sections song))))

(defn active-tracks-at-bar [song bar]
  (set (:tracks (section-at-bar song bar))))

(defn wrap-bar
  "Song bar to query for scheduler bar `bar` (relative to the song origin)."
  [song bar]
  (let [total (:total song)]
    (cond
      (neg? bar)                nil
      (= :loop (:end song))     (mod bar total)
      (< bar total)             bar
      :else                     nil)))

(defn song-duration-secs
  "Duration of one pass in seconds; 4 beats per bar. bpm must already be clamped."
  [song bpm]
  (/ (* (:total song) 240) bpm))

(defn position-after-reeval
  "Decision 6: keep the bar position on re-evaluation; past the end of a
   shortened song, continue at the first bar of its last section."
  [song bar]
  (cond
    (neg? bar)             bar
    (< bar (:total song))  bar
    :else                  (:start (peek (:sections song)))))

(defn song-status
  "Display info for scheduler bar `bar`, or nil outside the song."
  [song bar]
  (when-let [q (wrap-bar song bar)]
    (let [sec (section-at-bar song q)]
      {:bar          q
       :total        (:total song)
       :section      (:name sec)
       :section-bar  (- q (:start sec))
       :section-bars (:bars sec)})))

(defn- shift-span [sp offset]
  (when sp
    (core/span (core/rat+ (:start sp) offset) (core/rat+ (:end sp) offset))))

(defn- shift-event [ev offset]
  (cond-> ev
    (:whole ev) (update :whole shift-span offset)
    (:part ev)  (update :part shift-span offset)))

(defn song-onsets
  "Onsets to schedule for scheduler bar `bar` (relative to the song origin).
   tracks — map keyword → Pattern; muted — set of keywords (mute always wins).
   Each track pattern is queried in song time (bar q = (wrap-bar song bar)) and
   its events are shifted to bar `bar`. Returns [{:track kw :event ev} …]."
  [song tracks muted bar]
  (if-let [q (wrap-bar song bar)]
    (let [active (active-tracks-at-bar song q)
          offset (core/int->rat (- bar q))
          sp     (core/cycle-span q)]
      (vec
       (for [t     (:tracks song)
             :when (and (contains? active t)
                        (not (contains? muted t))
                        (core/pattern? (get tracks t)))
             ev    (core/query-onsets (get tracks t) sp)]
         {:track t :event (if (zero? (- bar q)) ev (shift-event ev offset))})))
    []))
