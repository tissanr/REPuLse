(ns repulse.history
  "Pure eval-history and snapshot store (Phase SK2).

   Content-addressed blobs (canonical text of a sanitized v3 session map),
   bounded history, named snapshots, restore points and line diffs — all pure
   functions over a store map. Persistence goes through the HistoryStorage
   protocol (in-memory adapter here, IndexedDB adapter in repulse.history-idb).
   No DOM, no IndexedDB, no js/Date."
  (:require [clojure.string :as str]
            [cljs.reader :as reader]
            [repulse.specs :as specs]
            [repulse.lisp.eval :as leval]))

(def MAX-ENTRIES 500)
(def MAX-CHARS 20000000)
(def MAX-SNAPSHOTS 100)
(def MAX-NAME 64)
(def MAX-DIFF-CELLS 1000000)
(def MAX-ERROR 200)
(def default-limits {:max-entries MAX-ENTRIES :max-chars MAX-CHARS})

;; ── hashing / serialisation (K3, K4) ─────────────────────────────────────────

(defn fnv1a32
  "FNV-1a 32-bit over the UTF-16 code units of s (unsigned)."
  [s]
  (let [n (count s)]
    (loop [i 0 h 0x811c9dc5]
      (if (< i n)
        (recur (inc i)
               (unsigned-bit-shift-right
                (js/Math.imul (bit-xor h (.charCodeAt s i)) 0x01000193) 0))
        h))))

(defn- hex8 [n]
  (.padStart (.toString n 16) 8 "0"))

(defn- reverse-code-units [s]
  (.join (.reverse (.split s "")) ""))

(defn content-hash
  "16 lowercase hex chars: FNV-1a of the text, then FNV-1a of the reversed text."
  [s]
  (str (hex8 (fnv1a32 s)) (hex8 (fnv1a32 (reverse-code-units s)))))

(defn- key-compare [a b] (compare (pr-str a) (pr-str b)))

(defn- canon [x]
  (cond
    (map? x) (into (sorted-map-by key-compare)
                   (map (fn [[k v]] [k (canon v)]) x))
    (sequential? x) (mapv canon x)
    :else x))

(defn canonical-str
  "pr-str of x with every map turned into a key-sorted map."
  [x]
  (pr-str (canon x)))

(defn state->blob
  "Canonical blob text of a project state; :active is normalised to :main."
  [state]
  (canonical-str (assoc state :active (:main state))))

(defn blob->state
  "Read a blob back and re-validate it; nil when unreadable or invalid."
  [text]
  (try
    (specs/sanitize-session-v3 (reader/read-string text))
    (catch :default _ nil)))

;; ── store ────────────────────────────────────────────────────────────────────
;; {:next-id 1
;;  :history   [{:id :t :hash :kind :ok? :error} …]   oldest first
;;  :snapshots [{:name :t :hash} …]                   oldest write first
;;  :blobs     {hash text}}

(def empty-store {:next-id 1 :history [] :snapshots [] :blobs {}})

(defn meta-of [store]
  (-> store (dissoc :blobs) (assoc :version 1)))

(defn total-chars [store]
  (reduce + 0 (map count (vals (:blobs store)))))

(defn snapshot-names [store]
  (vec (reverse (map :name (:snapshots store)))))

(defn entries-newest-first [store]
  (vec (reverse (:history store))))

(defn entry-by-id [store id]
  (first (filter #(= id (:id %)) (:history store))))

(defn- find-snapshot [store nm]
  (first (filter #(= nm (:name %)) (:snapshots store))))

(defn- referenced [store]
  (into #{} (concat (map :hash (:history store))
                    (map :hash (:snapshots store)))))

(defn- gc* [store]
  (let [refs (referenced store)]
    (assoc store :blobs (into {} (filter (fn [[h _]] (contains? refs h)) (:blobs store))))))

(defn- ops-between
  "Ops that turn the persisted form of old into that of new:
   all :put-blob, one :put-meta, all :delete-blob."
  [old new]
  (if (= old new)
    []
    (let [puts (sort (remove #(contains? (:blobs old) %) (keys (:blobs new))))
          dels (sort (remove #(contains? (:blobs new) %) (keys (:blobs old))))]
      (vec (concat (map (fn [h] [:put-blob h (get (:blobs new) h)]) puts)
                   [[:put-meta (pr-str (meta-of new))]]
                   (map (fn [h] [:delete-blob h]) dels))))))

(defn- result [old new & kvs]
  (apply assoc {:store new :ops (ops-between old new)} kvs))

(defn- enforce-limits [store {:keys [max-entries max-chars]}]
  (loop [s (gc* store)]
    (if (and (> (count (:history s)) 1)
             (or (> (count (:history s)) max-entries)
                 (> (total-chars s) max-chars)))
      (recur (gc* (update s :history subvec 1)))
      s)))

(defn- truncate-error [ok? error]
  (when (and (not ok?) error)
    (subs error 0 (min MAX-ERROR (count error)))))

(defn append-eval
  "Record one project evaluation (K5 collapse, K6 eviction + GC)."
  ([store text now ok? error] (append-eval store text now ok? error default-limits))
  ([store text now ok? error limits]
   (let [h      (content-hash text)
         err    (truncate-error ok? error)
         newest (peek (:history store))
         base   (assoc-in store [:blobs h] text)]
     (if (and newest (= h (:hash newest)))
       (let [s' (assoc base :history
                       (conj (pop (:history store))
                             (assoc newest :t now :ok? (boolean ok?) :error err :kind :eval)))]
         (result store s' :id (:id newest)))
       (let [id (:next-id store)
             s' (-> base
                    (assoc :next-id (inc id))
                    (update :history conj {:id id :t now :hash h :kind :eval
                                           :ok? (boolean ok?) :error err}))
             s' (enforce-limits s' limits)]
         (result store s' :id id))))))

(defn add-restore-point
  "Record the current state before a restore (K8); reuse the newest entry when
   it already holds the same state."
  [store text now]
  (let [h      (content-hash text)
        newest (peek (:history store))]
    (if (and newest (= h (:hash newest)))
      {:store store :ops [] :id (:id newest)}
      (let [id (:next-id store)
            s' (-> store
                   (assoc-in [:blobs h] text)
                   (assoc :next-id (inc id))
                   (update :history conj {:id id :t now :hash h :kind :restore-point
                                          :ok? nil :error nil})
                   (enforce-limits default-limits))]
        (result store s' :id id)))))

;; ── snapshots ────────────────────────────────────────────────────────────────

(defn- valid-name? [nm]
  (and (string? nm) (<= 1 (count nm) MAX-NAME)))

(def ^:private bad-name-msg "snapshot name must be 1–64 characters")

(defn- closest-name [store nm]
  (let [best (reduce (fn [[bn bd :as acc] s]
                       (let [d (leval/levenshtein nm (:name s))]
                         (if (or (nil? bn) (< d bd)) [(:name s) d] acc)))
                     nil
                     (reverse (:snapshots store)))]
    (when (and best (<= (second best) 2)) (first best))))

(defn- unknown-snapshot [store nm]
  (if-let [m (closest-name store nm)]
    {:error (str "unknown snapshot " (pr-str nm) " — did you mean " (pr-str m) "?")}
    {:error (str "unknown snapshot " (pr-str nm) " — (snapshots) lists the saved names")}))

(defn save-snapshot [store nm text now]
  (let [nm (str/trim (str nm))]
    (if-not (valid-name? nm)
      {:error bad-name-msg}
      (let [existing (find-snapshot store nm)]
        (if (and (not existing) (>= (count (:snapshots store)) MAX-SNAPSHOTS))
          {:error (str "too many snapshots (max " MAX-SNAPSHOTS
                       ") — delete one with (delete-snapshot! \"name\")")}
          (let [h  (content-hash text)
                s' (-> store
                       (assoc-in [:blobs h] text)
                       (assoc :snapshots
                              (conj (vec (remove #(= nm (:name %)) (:snapshots store)))
                                    {:name nm :t now :hash h}))
                       gc*)]
            (result store s' :status (if existing :updated :saved) :name nm)))))))

(defn delete-snapshot [store nm]
  (let [nm (str/trim (str nm))]
    (if-let [_ (find-snapshot store nm)]
      (let [s' (gc* (update store :snapshots (fn [v] (vec (remove #(= nm (:name %)) v)))))]
        (result store s' :name nm))
      (unknown-snapshot store nm))))

(defn rename-snapshot [store old new]
  (let [old (str/trim (str old))
        new (str/trim (str new))]
    (cond
      (not (find-snapshot store old)) (unknown-snapshot store old)
      (not (valid-name? new)) {:error bad-name-msg}
      (= old new) {:store store :ops [] :old old :new new}
      (find-snapshot store new)
      {:error (str "a snapshot named " (pr-str new) " already exists")}
      :else
      (let [s' (update store :snapshots
                       (fn [v] (mapv #(if (= old (:name %)) (assoc % :name new) %) v)))]
        (result store s' :old old :new new)))))

;; ── restore ──────────────────────────────────────────────────────────────────

(defn resolve-target
  "A snapshot name (string) or history entry id (integer) → {:text :label}."
  [store target]
  (if (string? target)
    (let [nm (str/trim target)]
      (if-let [s (find-snapshot store nm)]
        {:text (get (:blobs store) (:hash s)) :label (str "snapshot " (pr-str nm))}
        (unknown-snapshot store nm)))
    (if-let [e (entry-by-id store target)]
      {:text (get (:blobs store) (:hash e)) :label (str "history #" target)}
      {:error (str "unknown history entry #" target)})))

(defn prepare-restore [store target current-text now]
  (let [r (resolve-target store target)]
    (if (:error r)
      r
      (let [rp (add-restore-point store current-text now)]
        (assoc rp :text (:text r) :label (:label r) :restore-point (:id rp))))))

(defn restore-message [label restore-point]
  (str "=> restored " label " — previous state is history #" restore-point))

(defn clear-history [store]
  (result store (gc* (assoc store :history []))))

(defn gc [store]
  (result store (gc* store)))

;; ── load & repair (K15) ──────────────────────────────────────────────────────

(defn- valid-entry? [blobs e]
  (and (map? e) (integer? (:id e)) (number? (:t e))
       (contains? #{:eval :restore-point} (:kind e))
       (contains? blobs (:hash e))))

(defn- valid-snap? [blobs s]
  (and (map? s) (valid-name? (:name s)) (number? (:t s))
       (contains? blobs (:hash s))))

(defn load->store [meta-text blobs]
  (let [loaded (when meta-text
                 (try (reader/read-string meta-text) (catch :default _ nil)))
        loaded (when (and (map? loaded) (= 1 (:version loaded))) loaded)
        blobs  (or blobs {})]
    (if-not loaded
      (let [s (assoc empty-store :blobs {})]
        {:store s
         :ops (vec (concat [[:put-meta (pr-str (meta-of s))]]
                           (map (fn [h] [:delete-blob h]) (sort (keys blobs)))))})
      (let [hist  (vec (filter #(valid-entry? blobs %)
                               (when (sequential? (:history loaded)) (:history loaded))))
            snaps (reduce (fn [acc s]
                            (conj (vec (remove #(= (:name s) (:name %)) acc)) s))
                          []
                          (filter #(valid-snap? blobs %)
                                  (when (sequential? (:snapshots loaded)) (:snapshots loaded))))
            next-id (max (if (integer? (:next-id loaded)) (:next-id loaded) 1)
                         (inc (reduce max 0 (map :id hist)))
                         1)
            s     (gc* {:next-id next-id :history hist :snapshots snaps :blobs blobs})
            dels  (sort (remove #(contains? (:blobs s) %) (keys blobs)))
            m     (meta-of s)]
        {:store s
         :ops (if (and (= m loaded) (empty? dels))
                []
                (vec (concat [[:put-meta (pr-str m)]]
                             (map (fn [h] [:delete-blob h]) dels))))}))))

;; ── diff (K17) ───────────────────────────────────────────────────────────────

(defn split-lines* [s]
  (if (= "" s) [] (vec (.split s "\n"))))

(defn- lcs-diff [a b]
  (let [n  (count a)
        m  (count b)
        w  (inc m)
        tb (js/Int32Array. (* (inc n) w))]
    (loop [i (dec n)]
      (when (>= i 0)
        (loop [j (dec m)]
          (when (>= j 0)
            (aset tb (+ (* i w) j)
                  (if (= (nth a i) (nth b j))
                    (inc (aget tb (+ (* (inc i) w) (inc j))))
                    (max (aget tb (+ (* (inc i) w) j))
                         (aget tb (+ (* i w) (inc j))))))
            (recur (dec j))))
        (recur (dec i))))
    (loop [i 0 j 0 out (transient [])]
      (cond
        (and (< i n) (< j m) (= (nth a i) (nth b j)))
        (recur (inc i) (inc j) (conj! out [:same (nth a i)]))

        (and (< i n)
             (or (>= j m)
                 (>= (aget tb (+ (* (inc i) w) j))
                     (aget tb (+ (* i w) (inc j))))))
        (recur (inc i) j (conj! out [:del (nth a i)]))

        (< j m)
        (recur i (inc j) (conj! out [:add (nth b j)]))

        :else (persistent! out)))))

(defn line-diff
  "Line diff old → new: [[:same|:del|:add line] …]."
  [old new]
  (let [a (split-lines* old)
        b (split-lines* new)
        na (count a)
        nb (count b)
        lim (min na nb)
        pre (loop [k 0] (if (and (< k lim) (= (nth a k) (nth b k))) (recur (inc k)) k))
        lim2 (- lim pre)
        suf (loop [k 0]
              (if (and (< k lim2) (= (nth a (- na 1 k)) (nth b (- nb 1 k)))) (recur (inc k)) k))
        ma (subvec a pre (- na suf))
        mb (subvec b pre (- nb suf))
        middle (if (> (* (count ma) (count mb)) MAX-DIFF-CELLS)
                 (vec (concat (map (fn [l] [:del l]) ma) (map (fn [l] [:add l]) mb)))
                 (lcs-diff ma mb))]
    (vec (concat (map (fn [l] [:same l]) (subvec a 0 pre))
                 middle
                 (map (fn [l] [:same l]) (subvec a (- na suf)))))))

(defn- code-map [state]
  (into {} (map (juxt :name :code) (:buffers state))))

(defn project-diff
  "Per-buffer diff between two project states (nil = no buffers)."
  [old-state new-state]
  (let [olds (code-map old-state)
        news (code-map new-state)
        in-new (for [{nm :name code :code} (:buffers new-state)]
                 (if (contains? olds nm)
                   (let [lines (line-diff (get olds nm) code)]
                     {:buffer nm
                      :status (if (every? #(= :same (first %)) lines) :same :changed)
                      :lines lines})
                   {:buffer nm :status :added :lines (line-diff "" code)}))
        removed (for [{nm :name code :code} (:buffers old-state)
                      :when (not (contains? news nm))]
                  {:buffer nm :status :removed :lines (line-diff code "")})]
    (vec (concat in-new removed))))

(defn diff-stats [pdiff]
  (let [ops (mapcat :lines pdiff)]
    {:added   (count (filter #(= :add (first %)) ops))
     :removed (count (filter #(= :del (first %)) ops))}))

(defn summary-text [{:keys [added removed]}]
  (if (and (zero? added) (zero? removed))
    "no changes"
    (str "+" added " -" removed)))

;; ── storage adapter protocol ─────────────────────────────────────────────────

(defprotocol HistoryStorage
  (-load [this on-done])
  (-write! [this ops]))

(defn memory-storage
  "In-memory adapter over an atom {:meta nil :blobs {}} — unit tests and the
   IndexedDB-unavailable fallback."
  ([] (memory-storage (atom {:meta nil :blobs {}})))
  ([a]
   (reify HistoryStorage
     (-load [_ on-done] (on-done @a))
     (-write! [_ ops]
       (doseq [[op x y] ops]
         (case op
           :put-blob   (swap! a assoc-in [:blobs x] y)
           :put-meta   (swap! a assoc :meta x)
           :delete-blob (swap! a update :blobs dissoc x)))))))
