(ns repulse.project
  "Pure multi-buffer project model (Phase SONG4).
   A project is {:main \"main\" :active \"main\" :buffers [{:name \"main\" :code \"…\"} …]}.
   No DOM, no CodeMirror, no audio — every function here is unit-tested."
  (:require [cljs.reader :as edn]
            [clojure.string :as str]
            [repulse.specs :as specs]))

(def MAX-BUFFERS specs/max-buffers)
(def OFFSET-STRIDE 1000000000)
(def MAX-IMPORT-CHARS 5000000)

(defn empty-project
  "A project with a single empty main buffer."
  []
  {:main "main" :active "main" :buffers [{:name "main" :code ""}]})

(defn buffer-names [project]
  (mapv :name (:buffers project)))

(defn has-buffer? [project name]
  (boolean (some #(= name %) (buffer-names project))))

(defn code-of
  "The code of buffer `name`, or nil."
  [project name]
  (some #(when (= name (:name %)) (:code %)) (:buffers project)))

(defn name-problem
  "nil when `name` is a valid, unused buffer name for project, else a reason."
  [project name]
  (cond
    (not (specs/valid-buffer-name? name)) (specs/invalid-buffer-name-message name)
    (has-buffer? project name)            (str "a buffer named " (pr-str name) " already exists")))

(defn- unknown [name]
  {:error (str "unknown buffer " (pr-str name))})

(defn next-buffer-name
  "The first free \"buffer-N\" with N ≥ 2."
  [project]
  (some #(let [n (str "buffer-" %)] (when-not (has-buffer? project n) n))
        (iterate inc 2)))

(defn add-buffer
  "Append a buffer and make it active. {:ok project'} | {:error reason}"
  [project name code]
  (if-let [p (name-problem project name)]
    {:error p}
    (if (>= (count (:buffers project)) MAX-BUFFERS)
      {:error (str "too many buffers (max " MAX-BUFFERS ")")}
      {:ok (-> project
               (update :buffers conj {:name name :code (or code "")})
               (assoc :active name))})))

(defn rename-buffer
  "Rename a buffer; :main and :active follow. Code is never rewritten.
   {:ok project'} | {:error reason}"
  [project old new]
  (cond
    (not (has-buffer? project old)) (unknown old)
    (= old new)                     {:ok project}
    :else
    (if-let [p (name-problem project new)]
      {:error p}
      (let [ren #(if (= % old) new %)]
        {:ok (-> project
                 (update :buffers (fn [bs] (mapv #(update % :name ren) bs)))
                 (update :main ren)
                 (update :active ren))}))))

(defn remove-buffer
  "Delete a buffer (never main). Active falls back to main when removed.
   {:ok project'} | {:error reason}"
  [project name]
  (cond
    (not (has-buffer? project name)) (unknown name)
    (= name (:main project))         {:error "the main buffer can't be deleted"}
    :else
    {:ok (-> project
             (update :buffers (fn [bs] (filterv #(not= name (:name %)) bs)))
             (update :active #(if (= % name) (:main project) %)))}))

(defn set-active
  "{:ok project'} | {:error reason}"
  [project name]
  (if (has-buffer? project name)
    {:ok (assoc project :active name)}
    (unknown name)))

;;; ── Global source offsets (decision 5) ──────────────────────────────────

(defn offset-bases
  "{buffer-name → offset base}: main → 0, every other buffer →
   OFFSET-STRIDE × (1 + its index among the non-main buffers in tab order)."
  [project]
  (let [main   (:main project)
        others (remove #(= main %) (buffer-names project))]
    (into {main 0}
          (map-indexed (fn [i n] [n (* OFFSET-STRIDE (inc i))]) others))))

(defn locate
  "Map a global source range back to {:buffer name :from local :to local}, or nil."
  [bases from to]
  (when (number? from)
    (when-let [[buffer base] (->> bases
                                  (filter (fn [[_ b]] (<= b from)))
                                  (sort-by second)
                                  last)]
      {:buffer buffer
       :from   (- from base)
       :to     (- (if (number? to) to from) base)})))

;;; ── .repulse EDN files (decision 4 / 15) ────────────────────────────────

(defn project->edn
  "Stable, diff-friendly EDN text: one buffer per line, trailing newline."
  [project]
  (let [bufs (map (fn [{:keys [name code]}]
                    (str "{:name " (pr-str name) " :code " (pr-str code) "}"))
                  (:buffers project))]
    (str "{:version 3\n"
         " :main " (pr-str (:main project)) "\n"
         " :buffers [" (str/join "\n           " bufs) "]}\n")))

(defn edn->project
  "Parse a .repulse file. {:ok project} (main active) | {:error \"Import failed: …\"}"
  [text]
  (let [fail #(hash-map :error (str "Import failed: " %))]
    (if (> (count text) MAX-IMPORT-CHARS)
      (fail "file too large (max 5 MB)")
      (let [data (try {:v (edn/read-string text)}
                      (catch :default _ nil))]
        (cond
          (nil? data)                (fail "could not read EDN")
          (not (map? (:v data)))     (fail "expected a map")
          (not= 3 (:version (:v data)))
          (fail (str "unsupported version " (pr-str (:version (:v data)))
                     " — expected :version 3"))
          :else
          (if-let [problem (specs/project-problem (:v data))]
            (fail problem)
            (let [p (specs/sanitize-project (:v data))]
              {:ok (assoc p :active (:main p))})))))))
