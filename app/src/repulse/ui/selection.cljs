(ns repulse.ui.selection
  "Pure outline selection model (Phase WS1).  Selection ids are strings that
   stay stable across edits: \"def:kick\", \"defn:humanize\", \"defsynth:pad\",
   \"track::drums\", \"section:<index>\", \"fx\" (the top-level fx group) and
   \"fx:<index>\" (index among :fx entries).  A selection is a vector of ids,
   newest last.  The song row is not selectable.
   Exports: entry-id, index-entries, pick, prune, ranges, primary, header."
  (:require [clojure.string :as str]))

(defn entry-id
  "Selection id of an outline entry; index = section index within the song or
   fx index among the :fx entries.  nil for non-selectable entries."
  [e index]
  (case (:kind e)
    :def      (str "def:" (:name e))
    :defn     (str "defn:" (:name e))
    :defsynth (str "defsynth:" (:name e))
    :track    (str "track:" (:name e))
    :section  (str "section:" index)
    :fx       (str "fx:" index)
    nil))

(defn index-entries
  "{id entry} for every selectable item of the outline entries."
  [entries]
  (let [song (first (filter #(= :song (:kind %)) entries))
        fxs  (filterv #(= :fx (:kind %)) entries)]
    (cond-> (into {}
                  (concat
                    (keep (fn [e]
                            (when (contains? #{:def :defn :defsynth :track} (:kind e))
                              [(entry-id e nil) e]))
                          entries)
                    (map-indexed (fn [i s] [(entry-id s i) s]) (:children song))
                    (map-indexed (fn [i f] [(entry-id f i) f]) fxs)))
      (seq fxs) (assoc "fx" {:kind :fx-group
                             :name "fx"
                             :from (:from (first fxs))
                             :to   (:to (last fxs))
                             :children fxs}))))

(defn- track-id? [id] (str/starts-with? id "track:"))

(defn pick
  "New selection after clicking `id` (shift? = ⇧-click): ⇧-click on a track
   while only tracks are selected toggles it; a plain click on the only
   selected row deselects; anything else selects just `id`."
  [sel id shift?]
  (let [sel (vec sel)]
    (cond
      (and shift? (track-id? id) (every? track-id? sel))
      (if (some #{id} sel)
        (vec (remove #{id} sel))
        (conj sel id))

      (= sel [id]) []

      :else [id])))

(defn prune
  "sel without ids that no longer resolve in idx."
  [sel idx]
  (vec (filter #(contains? idx %) sel)))

(defn ranges
  "Sorted, merged [{:from :to}] of the selected entries (the fx group
   contributes each child's range)."
  [sel idx]
  (let [rs (->> sel
                (keep idx)
                (mapcat (fn [e]
                          (if (= :fx-group (:kind e))
                            (map #(select-keys % [:from :to]) (:children e))
                            [(select-keys e [:from :to])])))
                (sort-by :from))]
    (reduce (fn [acc {:keys [from to] :as r}]
              (let [prev (peek acc)]
                (if (and prev (<= from (:to prev)))
                  (conj (pop acc) (assoc prev :to (max to (:to prev))))
                  (conj acc r))))
            [] rs)))

(defn primary
  "Entry of the most recently picked id that still resolves, or nil."
  [sel idx]
  (some idx (reverse sel)))

(def ^:private KIND-LABEL
  {:track "track" :section "section" :def "def" :defn "defn"
   :defsynth "synth" :fx "fx" :fx-group "fx"})

(defn header
  "{:kind :title :from} for the inspector header, or nil for an empty
   selection.  Several ids (only tracks can be multi-selected) →
   {:kind \"tracks\" :title \"<n>\" :from nil}."
  [sel idx]
  (let [ids (prune sel idx)]
    (cond
      (empty? ids) nil

      (> (count ids) 1)
      {:kind "tracks" :title (str (count ids)) :from nil}

      :else
      (let [e (idx (first ids))]
        {:kind  (KIND-LABEL (:kind e))
         :title (:name e)
         :from  (:from e)}))))
