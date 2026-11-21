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

(defn ramp?       [x] (and (map? x) (= :ramp (:type x))))
(defn automation? [x] (and (map? x) (= :automation (:type x))))
(defn fill?       [x] (and (map? x) (= :fill (:type x))))

(def ^:private ramp-curves #{:linear :exp :sine})

(defn make-ramp
  "A ramp curve value {:type :ramp :curve :from :to} (A6)."
  [curve from to]
  (when-not (contains? ramp-curves curve)
    (throw (song-error (str "ramp: unknown curve " (fmt-value curve) " — use :linear, :exp or :sine")
                       {:arg :curve})))
  (when-not (and (number? from) (number? to))
    (throw (song-error (str "ramp: from and to must be numbers, got "
                            (fmt-value from) " and " (fmt-value to))
                       {:arg :from})))
  (when (and (= curve :exp) (not (and (pos? from) (pos? to))))
    (throw (song-error (str "ramp :exp needs both values > 0, got " (fmt-value from) " and "
                            (fmt-value to) " — use :linear or :sine")
                       {:arg :from})))
  {:type :ramp :curve curve :from from :to to})

(defn make-automation
  "An automation lane. track — keyword (:all allowed); param — keyword;
   curve — number | ramp | pattern; opts — {:bars [a b] :op :set|:mul|:add}."
  [track param curve opts]
  (when-not (keyword? track)
    (throw (song-error (str "automate: track must be a track name like :hats or :all, got "
                            (fmt-value track))
                       {:arg :track})))
  (when-not (keyword? param)
    (throw (song-error (str "automate: parameter must be a keyword like :amp, got "
                            (fmt-value param))
                       {:arg :param})))
  (when-not (or (number? curve) (ramp? curve) (core/pattern? curve))
    (throw (song-error (str "automate " (kw-str track) " " (kw-str param)
                            ": expected a number, (ramp …) or a pattern, got " (fmt-value curve))
                       {:arg :curve})))
  (let [bars (:bars opts)
        op   (or (:op opts) :set)]
    (when (and (some? bars)
               (not (and (vector? bars) (= 2 (count bars)) (every? integer? bars))))
      (throw (song-error (str "automate: :bars must be [from to] with whole numbers, got "
                              (fmt-value bars))
                         {:arg :bars})))
    (when-not (#{:set :mul :add} op)
      (throw (song-error (str "automate: :op must be :set, :mul or :add, got " (fmt-value op))
                         {:arg :op})))
    {:type :automation :track track :param param :curve curve :bars bars :op op}))

(defn make-fill
  "A fill. pattern — :_ (silence), a pattern, or a single value (coerced with pure)."
  [track bars pattern]
  (when (= :all track)
    (throw (song-error "fill: needs one track name, got :all" {:arg :track})))
  (when-not (keyword? track)
    (throw (song-error (str "fill: track must be a track name like :snare, got " (fmt-value track))
                       {:arg :track})))
  (when-not (whole-bars? bars)
    (throw (song-error (str "fill " (kw-str track) ": bars must be a whole number ≥ 1, got "
                            (fmt-value bars))
                       {:arg :bars})))
  {:type    :fill
   :track   track
   :bars    bars
   :pattern (cond
              (= :_ pattern)         nil
              (core/pattern? pattern) pattern
              :else                  (core/pure pattern))})

(defn- check-items!
  "Validate section items against the section length."
  [name bars items]
  (doseq [[i item] (map-indexed vector items)
          :when (not (or (automation? item) (fill? item)))]
    (throw (song-error (str "section " (kw-str name) ": unexpected argument " (fmt-value item)
                            " — after the tracks, sections take (automate …) and (fill …)")
                       {:arg :item :section name :item-index i})))
  (doseq [item items
          :when (and (automation? item) (:bars item))]
    (let [[a b] (:bars item)]
      (when-not (and (<= 0 a) (< a b) (<= b bars))
        (throw (song-error (str "automate " (kw-str (:track item)) " " (kw-str (:param item))
                                ": :bars [" a " " b "] is outside section "
                                (kw-str name) " (" bars " bars) — use 0 ≤ from < to ≤ " bars)
                           {:arg :item :section name :item item})))))
  (reduce (fn [seen item]
            (if-not (fill? item)
              seen
              (do
                (when (> (:bars item) bars)
                  (throw (song-error (str "fill " (kw-str (:track item)) ": " (:bars item)
                                          " bars is longer than section " (kw-str name)
                                          " (" bars " bars)")
                                     {:arg :item :section name :item item})))
                (when (contains? seen (:track item))
                  (throw (song-error (str "Two fills for " (kw-str (:track item)) " in section "
                                          (kw-str name) " — use one (fill …) per track")
                                     {:arg :item :section name :item item})))
                (conj seen (:track item)))))
          #{} items))

(defn make-section
  "Build a section map. name — keyword; bars — integer ≥ 1;
   tracks — :all or a vector of track keywords (duplicates removed, order kept);
   items — (automate …) and (fill …) values (SONG2)."
  ([name bars tracks] (make-section name bars tracks []))
  ([name bars tracks items]
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
   (let [items (vec items)]
     (check-items! name bars items)
     (let [autos (filterv automation? items)
           fills (filterv fill? items)]
       (cond-> {:type   :section
                :name   name
                :bars   bars
                :tracks (if (= tracks :all) :all (vec (distinct tracks)))}
         (seq autos) (assoc :automations autos)
         (seq fills) (assoc :fills fills))))))

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
    ;; Unknown track names in (automate …) / (fill …) items (SONG2)
    (doseq [{:keys [name automations fills]} sections
            [kind items] [["automate" automations] ["fill" fills]]
            item items
            :let [t (:track item)]
            :when (and (not= :all t) (not (contains? kset t)))]
      (let [hint (when hint-fn (hint-fn (kw-str t) (mapv kw-str known)))]
        (throw (song-error (str "Unknown track " (kw-str t) " in (" kind " …) in section " (kw-str name)
                                (cond
                                  hint          (str " — did you mean " hint "?")
                                  (seq known)   (str " — tracks defined above the song: "
                                                     (apply str (interpose " " (map kw-str known))))
                                  :else         " — define tracks with (track :name …) above the song"))
                           {:section name :track t :item-source (:source item)}))))
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
                                            all? (assoc :all? true)
                                            (seq (:automations s))
                                            (assoc :automations
                                                   (mapv (fn [a]
                                                           (let [[x y] (:bars a)]
                                                             (assoc a
                                                                    :from (if x (+ start x) start)
                                                                    :to   (if y (+ start y) end'))))
                                                         (:automations s)))
                                            (seq (:fills s))
                                            (assoc :fills
                                                   (mapv #(assoc % :from (- end' (:bars %)) :to end')
                                                         (:fills s)))))))))]
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

;;; ── Automation and fills (SONG2) ──────────────────────────────────

(defn ramp-value
  "Value of ramp r at progress u (float in [0,1)) — A6."
  [{:keys [curve from to]} u]
  (case curve
    :linear (+ from (* (- to from) u))
    :exp    (* from (Math/pow (/ to from) u))
    :sine   (+ from (* (- to from) 0.5 (- 1 (Math/cos (* Math/PI u)))))))

(defn sample-at
  "Value of pattern pat at rational local time x (A8): first event of
   [x, x + 1/1000000); nil when there is none."
  [pat x]
  (:value (first (core/query pat (core/span x (core/rat+ x [1 1000000]))))))

(defn curve-value
  "Curve value at local time x (rational bars) in a lane of len bars."
  [curve x len]
  (cond
    (number? curve)       curve
    (ramp? curve)         (ramp-value curve (/ (core/rat->float x) len))
    (core/pattern? curve) (sample-at curve x)))

(defn- rest-value? [v]
  (or (= :_ v) (and (map? v) (= :_ (:note v)))))

(defn- apply-lane
  "Apply automation lane a (with absolute :from/:to) to value at song time t."
  [a value t]
  (let [x   (core/rat- t (core/int->rat (:from a)))
        len (- (:to a) (:from a))
        nv  (curve-value (:curve a) x len)]
    (if-not (number? nv)
      value
      (let [m    (if (map? value) value {:note value})
            base (get m (:param a))
            nv'  (if (number? base)
                   (case (:op a) :mul (* base nv) :add (+ base nv) nv)
                   nv)]
        (assoc m (:param a) nv')))))

(defn- lanes-for
  "Lanes of the section that apply to `track` at song time t."
  [lanes track t]
  (filterv #(and (or (= :all (:track %)) (= track (:track %)))
                 (core/rat<= (core/int->rat (:from %)) t)
                 (core/rat< t (core/int->rat (:to %))))
           lanes))

(defn- apply-lanes [lanes value t]
  (if (rest-value? value)
    value
    (reduce (fn [v a] (apply-lane a v t)) value lanes)))

(defn automate-value
  "Apply every lane of the section containing song time t (rational) whose
   track is `track` or :all and whose [from,to) contains t, in written order
   (A9/A10). Rests (A12) are returned unchanged."
  [song track value t]
  (let [t   (if (integer? t) (core/int->rat t) t)
        sec (section-at-bar song (quot (first t) (second t)))]
    (if-let [lanes (seq (:automations sec))]
      (apply-lanes (lanes-for lanes track t) value t)
      value)))

(defn automation-value
  "Value the lanes give parameter `param` of `track` at song time t, or nil."
  [song track param t]
  (get (automate-value song track {} t) param))

(defn fill-at
  "The fill of `track` covering song bar q, or nil."
  [song track q]
  (some #(when (and (= track (:track %)) (<= (:from %) q) (< q (:to %))) %)
        (:fills (section-at-bar song q))))

(defn song-onsets
  "Onsets to schedule for scheduler bar `bar` (relative to the song origin).
   tracks — map keyword → Pattern; muted — set of keywords (mute always wins).
   Each track pattern is queried in song time (bar q = (wrap-bar song bar)) and
   its events are shifted to bar `bar`. Fills replace a track's pattern in the
   last bars of a section; automation lanes rewrite event values (SONG2).
   With `on-error` (fn [track-kw error]), a track whose query throws adds no
   events and is reported through it; the other tracks are unaffected. Without
   it, the error propagates.
   Returns [{:track kw :event ev} …]."
  ([song tracks muted bar] (song-onsets song tracks muted bar nil))
  ([song tracks muted bar on-error]
   (if-let [q (wrap-bar song bar)]
     (let [sec    (section-at-bar song q)
           fills  (into {} (comp (filter #(and (<= (:from %) q) (< q (:to %))))
                                 (map (juxt :track identity)))
                        (:fills sec))
           lanes  (filterv #(and (<= (:from %) q) (< q (:to %))) (:automations sec))
           active (into (set (:tracks sec)) (keys fills))
           offset (core/int->rat (- bar q))
           sp     (core/cycle-span q)
           track-onsets
           (fn [t pat]
             (let [tl (when (seq lanes) (filterv #(or (= :all (:track %)) (= t (:track %))) lanes))]
               (mapv (fn [ev]
                       (let [ev (if (seq tl)
                                  (update ev :value #(apply-lanes tl % (:start (:whole ev))))
                                  ev)]
                         {:track t :event (if (zero? (- bar q)) ev (shift-event ev offset))}))
                     (core/query-onsets pat sp))))]
       (into []
             (mapcat
              (fn [t]
                (let [pat (if-let [f (get fills t)] (:pattern f) (get tracks t))]
                  (when (and (contains? active t) (not (contains? muted t))
                             (core/pattern? pat))
                    (if on-error
                      (try (track-onsets t pat)
                           (catch :default e (on-error t e) nil))
                      (track-onsets t pat))))))
             (:tracks song)))
     [])))
