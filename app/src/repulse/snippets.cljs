(ns repulse.snippets
  "Snippet library registry — loads library.json or /api/snippets, provides
   search/filter, and exports the `snippet` Lisp built-in factory.
   Exports: library-atom, loaded?, load!, reload!, all-snippets, all-tags,
            filter-snippets, by-id, snippet-builtin,
            sort-order, author-filter, starred-ids, toggle-starred!"
  (:require [repulse.lisp.eval :as leval]
            [repulse.auth :as auth]
            [repulse.api :as api]
            [clojure.string :as cstr]))

;;; State

(defonce library-atom  (atom {:version 1 :snippets []}))
(defonce loaded?       (atom false))
(defonce ^:private loading? (atom false))

;; Sort / filter state (server-side params)
(defonce sort-order    (atom "most-starred"))  ; "newest" | "most-starred" | "most-used" | "trending"
(defonce author-filter (atom nil))             ; string or nil

;; Starred-snippet IDs — optimistic set, populated/updated as user stars/unstars
(defonce starred-ids   (atom #{}))

;;; Data access

(defn all-snippets [] (:snippets @library-atom))

(defn all-tags []
  (->> (all-snippets)
       (mapcat :tags)
       distinct
       sort))

(defn by-id [id]
  (first (filter #(= (:id %) (str id)) (all-snippets))))

(defn filter-snippets
  "Return snippets matching query (searches title, description, code) and tag.
   Sort and author filtering happen server-side."
  [query tag]
  (let [q       (when (and query (seq (cstr/trim query)))
                  (cstr/lower-case (cstr/trim query)))
        snippets (all-snippets)]
    (cond->> snippets
      tag (filter #(some #{tag} (:tags %)))
      q   (filter (fn [s]
                    (or (cstr/includes? (cstr/lower-case (or (:title s) "")) q)
                        (cstr/includes? (cstr/lower-case (or (:description s) "")) q)
                        (cstr/includes? (cstr/lower-case (or (:code s) "")) q)))))))

;;; Starred helpers

(defn starred? [snippet-id]
  (contains? @starred-ids (str snippet-id)))

(defn toggle-starred!
  "Toggle local starred state and update the star count in library-atom.
   Returns :starred or :unstarred."
  [snippet-id]
  (let [id    (str snippet-id)
        delta (if (starred? id)
                (do (swap! starred-ids disj id) -1)
                (do (swap! starred-ids conj id)  1))]
    ;; Update star_count in library-atom optimistically
    (swap! library-atom update :snippets
           (fn [snips]
             (mapv (fn [s]
                     (if (= (:id s) id)
                       (update s :star_count + delta)
                       s))
                   snips)))
    (if (pos? delta) :starred :unstarred)))

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
