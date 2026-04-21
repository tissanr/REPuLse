(ns repulse.snippets
  "Snippet library registry — loads library.json or /api/snippets, provides
   search/filter, and exports the `snippet` Lisp built-in factory.
   Exports: library-atom, loaded?, load!, all-snippets, all-tags,
            filter-snippets, by-id, snippet-builtin."
  (:require [repulse.lisp.eval :as leval]
            [repulse.auth :as auth]
            [repulse.api :as api]
            [clojure.string :as cstr]))

;;; State

(defonce library-atom (atom {:version 1 :snippets []}))
(defonce loaded? (atom false))
(defonce ^:private loading? (atom false))

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
  "Return snippets matching query (searches title, description, code) and tag."
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

;;; Loading

(defn- load-from-api! []
  (-> (api/fetch-snippets)
      (.then (fn [result]
               (if-let [snippets (:data result)]
                 (do (reset! library-atom {:version 2 :snippets snippets})
                     (reset! loaded? true)
                     (reset! loading? false))
                 (do (js/console.warn "[REPuLse] API snippet load failed:" (:error result))
                     (load-from-static!)))))
      (.catch (fn [e]
                (js/console.warn "[REPuLse] API snippet fetch error:" e)
                (load-from-static!)))))

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

(defn load!
  "Populate library-atom from the API (when authenticated) or static JSON.
   No-ops if already loaded or loading."
  []
  (when (and (not @loaded?) (not @loading?))
    (reset! loading? true)
    (if (auth/session)
      (load-from-api!)
      (load-from-static!))))

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
