(ns repulse.specs
  (:require [cljs.spec.alpha :as s]
            [clojure.string :as str]))

(defn non-empty-string?
  [x]
  (and (string? x) (not (str/blank? x))))

(defn connectable-node?
  "Predicate for Web Audio node-like JS objects used at the plugin boundary."
  [x]
  (and x
       (fn? (aget x "connect"))
       (fn? (aget x "disconnect"))))

(defn effect-nodes?
  [^js x]
  (and x
       (connectable-node? (.-inputNode x))
       (connectable-node? (.-outputNode x))))

(defn safe-midi-channel?
  [x]
  (and (integer? x) (<= 1 x 16)))

(defn tween-descriptor?
  [x]
  (and (map? x)
       (= :tween (:type x))
       (contains? x :curve)
       (number? (:start x))
       (number? (:end x))
       (pos? (:duration-bars x))))

(defn event-payload?
  [x]
  (cond
    (keyword? x) true
    (and (map? x) (:note x)) (keyword? (:note x))
    (and (map? x) (:bank x)) (and (keyword? (:bank x)) (number? (:n x)))
    (and (map? x) (:synth x)) (and (keyword? (:synth x))
                                   (or (nil? (:freq x)) (number? (:freq x)))
                                   (or (nil? (:midi-ch x))
                                       (safe-midi-channel? (:midi-ch x))))
    (and (map? x) (= :tween (:type x))) (tween-descriptor? x)
    :else true))

(defn fx-entry?
  [x]
  (and (map? x)
       (non-empty-string? (:name x))
       (contains? x :plugin)
       (connectable-node? (:input x))
       (connectable-node? (:output x))))

(defn track-fx-entry?
  [x]
  (and (map? x)
       (non-empty-string? (:name x))
       (map? (:params x))))

(defn sample-registry?
  [x]
  (and (map? x)
       (every? (fn [[bank urls]]
                 (and (non-empty-string? bank)
                      (vector? urls)
                      (seq urls)
                      (every? non-empty-string? urls)))
               x)))

(defn loaded-source?
  [x]
  (and (map? x)
       (contains? #{:github :freesound} (:type x))
       (or (and (= :github (:type x))
                (non-empty-string? (:id x))
                (or (nil? (:banks x)) (nat-int? (:banks x))))
           (and (= :freesound (:type x))
                (non-empty-string? (:query x))
                (or (nil? (:count x)) (nat-int? (:count x)))))))

(defn midi-cc-mapping?
  [x]
  (and (map? x)
       (keyword? (:target x))
       (number? (:min x))
       (number? (:max x))
       (<= (:min x) (:max x))
       (or (nil? (:on-change x)) (fn? (:on-change x)))))

(defn midi-export-event?
  "A note of a MIDI file export (Phase SK1): ticks, not seconds."
  [x]
  (and (map? x)
       (integer? (:tick x)) (<= 0 (:tick x))
       (integer? (:dur x)) (<= 1 (:dur x))
       (integer? (:note x)) (<= 0 (:note x) 127)
       (integer? (:velocity x)) (<= 1 (:velocity x) 127)
       (integer? (:channel x)) (<= 1 (:channel x) 16)))

(s/def ::non-empty-string non-empty-string?)
(s/def ::event-payload event-payload?)
(s/def ::fx-entry fx-entry?)
(s/def ::track-fx-entry track-fx-entry?)
(s/def ::sample-registry sample-registry?)
(s/def ::loaded-source loaded-source?)
(s/def ::midi-cc-mapping midi-cc-mapping?)
(s/def ::midi-export-event midi-export-event?)

(s/def ::v #{2})
(s/def ::editor string?)
(s/def ::bpm #(and (number? %) (<= 20 % 640)))
(s/def ::fx vector?)
(s/def ::bank (s/nilable string?))
(s/def ::sources (s/coll-of #(and (map? %)
                                  (= "github" (str (:type %)))
                                  (non-empty-string? (:id %)))
                            :kind vector?))
(s/def ::muted (s/coll-of string? :kind vector?))
(s/def ::midi (s/map-of string? (s/nilable string?)))
(s/def ::session (s/keys :req-un [::v ::editor ::bpm ::fx ::bank ::sources ::muted ::midi]))

;; Shared by the v2 and v3 sanitizers: everything except the editor/buffers.
(defn- sanitize-session-common
  [data]
  {:bpm     (:bpm data)
   :fx      []
   :bank    (when (string? (:bank data)) (:bank data))
   :sources (->> (or (:sources data) [])
                 (filter #(and (map? %)
                               (= "github" (str (:type %)))
                               (non-empty-string? (:id %))))
                 (mapv #(select-keys % [:type :id])))
   :muted   (->> (or (:muted data) [])
                 (filter string?)
                 vec)
   :midi    (into {}
                  (keep (fn [[k v]]
                          (when (and (or (string? k) (keyword? k))
                                     (or (nil? v) (string? v)))
                            [(name k) v])))
                  (or (:midi data) {}))})

(defn sanitize-session-v2
  "Return a sanitized v2 session map, or nil if the input is not a session map."
  [data]
  (when (and (map? data) (= 2 (:v data)))
    (let [session (merge {:v      2
                          :editor (if (string? (:editor data)) (:editor data) "")}
                         (sanitize-session-common data))]
      (when (s/valid? ::session session)
        session))))

;;; ── Phase SONG4: projects (multi-buffer) ─────────────────────────────────

(def max-buffers 32)

(def buffer-name-re #"^[a-z][a-z0-9-]{0,31}$")

(defn valid-buffer-name? [x]
  (and (string? x) (boolean (re-matches buffer-name-re x))))

(defn invalid-buffer-name-message [n]
  (str "invalid buffer name " (pr-str n)
       " — use a-z, 0-9 and -, starting with a letter (max 32)"))

(defn project-problem
  "nil when p is a valid project map, else the first problem (a string)."
  [p]
  (cond
    (not (map? p)) "expected a map"
    (not (and (vector? (:buffers p)) (seq (:buffers p))))
    ":buffers must be a non-empty vector"
    (> (count (:buffers p)) max-buffers) (str "too many buffers (max " max-buffers ")")
    (not (every? #(and (map? %) (string? (:name %)) (string? (:code %))) (:buffers p)))
    "each buffer needs a :name and a :code string"
    :else
    (let [names (mapv :name (:buffers p))]
      (or (some #(when-not (valid-buffer-name? %) (invalid-buffer-name-message %)) names)
          (some (fn [[n c]] (when (> c 1) (str "duplicate buffer name " (pr-str n))))
                (let [fs (frequencies names)] (map (fn [n] [n (fs n)]) names)))
          (when-not (some #(= (:main p) %) names)
            (str "main buffer " (pr-str (:main p)) " not found"))))))

(defn sanitize-project
  "{:main … :buffers [{:name :code} …]} (only those keys) or nil."
  [p]
  (when-not (project-problem p)
    {:main    (:main p)
     :buffers (mapv #(select-keys % [:name :code]) (:buffers p))}))

(s/def :repulse.specs.v3/v #{3})
(s/def ::main valid-buffer-name?)
(s/def ::active valid-buffer-name?)
(s/def ::buffers (s/coll-of (s/keys :req-un [::name ::code]) :kind vector?))
(s/def ::name valid-buffer-name?)
(s/def ::code string?)
(s/def ::session-v3 (s/keys :req-un [:repulse.specs.v3/v ::main ::active ::buffers ::bpm ::fx ::bank
                                     ::sources ::muted ::midi]))

(defn sanitize-session-v3
  "Return a sanitized v3 session map, or nil."
  [data]
  (when (and (map? data) (= 3 (:v data)))
    (when-let [proj (sanitize-project data)]
      (let [names   (set (map :name (:buffers proj)))
            active  (if (contains? names (:active data)) (:active data) (:main proj))
            session (merge {:v       3
                            :main    (:main proj)
                            :active  active
                            :buffers (:buffers proj)}
                           (sanitize-session-common data))]
        (when (s/valid? ::session-v3 session)
          session)))))
