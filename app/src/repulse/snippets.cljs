(ns repulse.snippets
  "Snippet library registry — loads library.json or /api/snippets, provides
   search/filter, and exports the `snippet` Lisp built-in factory.
   Exports: library-atom, loaded?, load!, reload!, all-snippets, all-tags,
            filter-snippets, by-id, snippet-builtin,
            sort-order, author-filter, ratings, get-rating, set-rating!,
            load-ratings!"
  (:require [repulse.lisp.eval :as leval]
            [repulse.auth :as auth]
            [repulse.api :as api]
            [clojure.string :as cstr]))

;;; State

(defonce library-atom  (atom {:version 1 :snippets []}))
(defonce loaded?       (atom false))
(defonce ^:private loading? (atom false))

;; Sort / filter state (server-side params)
(defonce sort-order    (atom "top-rated"))  ; "newest" | "top-rated" | "most-used" | "trending"
(defonce author-filter (atom nil))             ; string or nil

;; Per-snippet ratings — optimistic map of snippet-id (string) → 1–5 (absent = no rating)
(defonce ratings       (atom {}))

;;; Data access

(defn all-snippets [] (:snippets @library-atom))

(defn all-tags []
  (->> (all-snippets)
       (mapcat :tags)
       distinct
       sort))

(defn by-id [id]
  (first (filter #(= (:id %) (str id)) (all-snippets))))

(defn- top-rated-key [snippet]
  [(or (:avg_rating snippet) 0)
   (or (:star_count snippet) 0)
   (or (:weighted_rating snippet) 0)
   (or (:created_at snippet) "")])

(defn- apply-client-sort [snippets]
  (case @sort-order
    "top-rated" (sort #(compare (top-rated-key %2) (top-rated-key %1)) snippets)
    snippets))

(defn filter-snippets
  "Return snippets matching query (searches title, description, code) and tag.
   Sort and author filtering happen server-side."
  [query tag]
  (let [q       (when (and query (seq (cstr/trim query)))
                  (cstr/lower-case (cstr/trim query)))
        snippets (all-snippets)]
    (->> snippets
         (cond->>
           tag (filter #(some #{tag} (:tags %)))
           q   (filter (fn [s]
                         (or (cstr/includes? (cstr/lower-case (or (:title s) "")) q)
                             (cstr/includes? (cstr/lower-case (or (:description s) "")) q)
                             (cstr/includes? (cstr/lower-case (or (:code s) "")) q)))))
         (apply-client-sort))))

;;; Rating helpers

(defn get-rating
  "Return the user's current rating (1–5) for snippet-id, or 0 if not rated."
  [snippet-id]
  (get @ratings (str snippet-id) 0))

(defn- weighted-rating [avg-rating star-count]
  (/ (+ (* star-count avg-rating) (* 5 3.0))
     (+ star-count 5)))

(defn- apply-rating-summary [snippet old-rating new-rating]
  (let [old-count  (or (:star_count snippet) 0)
        old-avg    (or (:avg_rating snippet) 0)
        old-total  (* old-avg old-count)
        new-count  (cond
                     (and (zero? old-rating) (pos? new-rating))  (inc old-count)
                     (and (pos? old-rating)  (zero? new-rating)) (max 0 (dec old-count))
                     :else old-count)
        new-total  (cond
                     (and (zero? old-rating) (pos? new-rating))  (+ old-total new-rating)
                     (and (pos? old-rating)  (zero? new-rating)) (- old-total old-rating)
                     (and (pos? old-rating)  (pos? new-rating))  (+ (- old-total old-rating) new-rating)
                     :else old-total)
        new-avg    (if (pos? new-count) (/ new-total new-count) 0)]
    (assoc snippet
           :star_count new-count
           :avg_rating new-avg
           :weighted_rating (weighted-rating new-avg new-count))))

(defn set-rating!
  "Optimistically update ratings atom and star_count in library-atom.
   new-rating is 1–5 to rate, 0 to remove the rating."
  [snippet-id new-rating]
  (let [id      (str snippet-id)
        old-r   (get-rating id)]
    ;; Update ratings map
    (if (zero? new-rating)
      (swap! ratings dissoc id)
      (swap! ratings assoc id new-rating))
    ;; Update rating summary optimistically so top-rated ordering changes immediately.
    (swap! library-atom update :snippets
           (fn [snips]
             (mapv (fn [s]
                     (if (= (:id s) id)
                       (apply-rating-summary s old-r new-rating)
                       s))
                   snips)))))

;;; Rating persistence

(defn load-ratings!
  "Fetch the logged-in user's star ratings from the API and populate `ratings`.
   No-ops when not authenticated."
  []
  (when (auth/session)
    (-> (api/fetch-my-ratings!)
        (.then (fn [result]
                 (if-let [err (:error result)]
                   (js/console.warn "[REPuLse] load-ratings! failed:" err)
                   (when-let [stars (let [data (:data result)]
                                      (if (map? data) (:data data) data))]
                     (reset! ratings
                       (into {} (map (fn [s]
                                      [(:snippet_id s) (:rating s)])
                                     stars)))))))
        (.catch (fn [e] (js/console.warn "[REPuLse] load-ratings! error:" (.-message e)))))))

;;; Loading

(defn- apply-api-result! [snippets]
  (reset! library-atom {:version 2 :snippets snippets})
  (reset! loaded? true)
  (reset! loading? false))

(defn- load-from-static! []
  (-> (js/fetch "/snippets/library.json")
      (.then #(.json %))
      (.then (fn [data]
               (let [d (js->clj data :keywordize-keys true)]
                 (reset! library-atom d)
                 (reset! loaded? true)
                 (reset! loading? false))))
      (.catch (fn [e]
                (reset! loading? false)
                (js/console.warn "[REPuLse] snippet library load failed:" e)))))

(defn- load-from-api!
  ([] (load-from-api! {}))
  ([opts]
   (-> (api/fetch-snippets (merge {:sort @sort-order
                                   :author @author-filter}
                                  opts))
       (.then (fn [result]
                (if-let [snippets (:data result)]
                  (apply-api-result! snippets)
                  (do (js/console.warn "[REPuLse] API snippet load failed:" (:error result))
                      (load-from-static!)))))
       (.catch (fn [e]
                 (js/console.warn "[REPuLse] API snippet fetch error:" e)
                 (load-from-static!))))))

(defn load!
  "Populate library-atom from the API (when authenticated) or static JSON.
   No-ops if already loaded or loading."
  []
  (when (and (not @loaded?) (not @loading?))
    (reset! loading? true)
    (if (auth/session)
      (load-from-api!)
      (load-from-static!))))

(defn reload!
  "Force re-fetch with current sort-order / author-filter params.
   Always fires (ignores loaded? flag)."
  []
  (reset! loaded? false)
  (reset! loading? true)
  (if (auth/session)
    (load-from-api!)
    (load-from-static!)))

;;; Lisp built-in factory

(defn snippet-builtin
  "Returns the Lisp `snippet` built-in fn.
   editor-view-atom — atom holding the current CodeMirror EditorView.
   evaluate-ref     — atom holding the evaluate! fn (populated after eval-orchestrator init)."
  [editor-view-atom evaluate-ref]
  (fn [& args]
    (load!)
    (let [id-arg (when (seq args) (leval/unwrap (first args)))]
      (if (nil? id-arg)
        ;; No args: list available snippet IDs
        (if @loaded?
          (str "available snippets: "
               (cstr/join " " (map #(str ":" (:id %)) (all-snippets))))
          "loading snippet library…")
        ;; With arg: insert snippet by ID
        (let [id      (cljs.core/name id-arg)
              snippet (by-id id)]
          (if snippet
            (do
              (when-let [view @editor-view-atom]
                (let [code    (:code snippet)
                      doc-len (.. view -state -doc -length)]
                  (.dispatch view
                             #js {:changes #js {:from doc-len
                                                :to   doc-len
                                                :insert (str "\n\n" code)}})
                  (js/setTimeout #(when-let [f @evaluate-ref] (f "(upd)")) 50)))
              (str "=> inserted snippet :" id))
            (str "unknown snippet :" id
                 " — try (snippet) to list available")))))))
