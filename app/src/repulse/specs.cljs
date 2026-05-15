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

(def param-keys
  #{:amp :attack :decay :release :pan :rate :begin :end :loop})

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
  [x]
  (and (map? x)
       (number? (:time-sec x))
       (not (neg? (:time-sec x)))
       (number? (:duration-sec x))
       (pos? (:duration-sec x))
       (integer? (:midi-note x))
       (<= 0 (:midi-note x) 127)
       (safe-midi-channel? (or (:channel x) 1))))

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

(defn sanitize-session-v2
  "Return a sanitized v2 session map, or nil if the input is not a session map."
  [data]
  (when (and (map? data) (= 2 (:v data)))
    (let [session {:v       2
                   :editor  (if (string? (:editor data)) (:editor data) "")
                   :bpm     (:bpm data)
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
                                  (or (:midi data) {}))}]
      (when (s/valid? ::session session)
        session))))

(defn explain-str
  [spec value]
  (with-out-str (s/explain spec value)))
