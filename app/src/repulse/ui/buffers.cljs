(ns repulse.ui.buffers
  "Multi-buffer projects in the main editor (Phase SONG4).
   One EditorView, one EditorState per buffer: switching swaps the view's state
   (view.setState), so undo history and diagnostics stay per buffer and every
   module that uses @editor/editor-view works on the active buffer.
   Owns the buffer tab bar (create / rename / delete / switch, .repulse
   export/import). The pure operations live in repulse.project.
   Exports: init!, active-name, current-project, switch-to!, create-buffer!,
            rename-buffer!, delete-buffer!, replace-project!,
            clear-all-diagnostics!, add-switch-hook!, render-tabs!."
  (:require [clojure.string :as str]
            [repulse.project :as project]
            [repulse.session :as session]
            [repulse.ui.editor :as editor]
            [repulse.ui.goto :as goto]
            [repulse.ui.html :refer [escape-html]]
            ["@codemirror/lint" :refer [setDiagnostics]]))

;;; State

;; The project without codes: {:main name :active name :names [name …]}.
(defonce ^:private meta-state (atom nil))
;; {name → EditorState} — the active buffer's entry may be stale; the live
;; state is always (.-state view).
(defonce ^:private states (atom {}))
;; Extension array for new states.
(defonce ^:private extensions (atom #js []))
;; Fns of no args called after every buffer switch (outline refresh, …).
(defonce ^:private switch-hooks (atom []))
;; (fn [msg status]) — the app's output line.
(defonce ^:private output-fn (atom nil))
;; Inline tab-bar editing. Native prompt/confirm dialogs block the main
;; thread and with it the audio scheduler, so playback stopped while one was
;; open. nil | {:mode :add :value str} | {:mode :rename :tab name}
(defonce ^:private editing (atom nil))
;; Buffer whose × was clicked once (second click deletes), else nil.
(defonce ^:private confirm-delete (atom nil))
(defonce ^:private confirm-timer (atom nil))
(def ^:private confirm-ms 3000)

(defn- view [] @editor/editor-view)

(defn- set-output! [msg status]
  (when-let [f @output-fn] (f msg status)))

(defn- error! [reason]
  (set-output! (str "Error: " reason) :error))

(defn add-switch-hook! [f]
  (swap! switch-hooks conj f))

(defn active-name []
  (:active @meta-state))

(defn current-project
  "{:main :active :buffers [{:name :code} …]} with live codes."
  []
  (let [{:keys [main active names]} @meta-state
        v (view)]
    {:main    main
     :active  active
     :buffers (mapv (fn [n]
                      {:name n
                       :code (if (and v (= n active))
                               (.. v -state -doc (toString))
                               (if-let [st (get @states n)] (.. st -doc (toString)) ""))})
                    names)}))

(defn- sync-meta! [p]
  (reset! meta-state {:main (:main p) :active (:active p) :names (project/buffer-names p)}))

(defn- store-active-state! []
  (when-let [v (view)]
    (when-let [a (active-name)]
      (swap! states assoc a (.-state v)))))

;;; Tabs

(defn- name-input-html [value label]
  (str "<input class=\"buf-name-input\" type=\"text\" maxlength=\"32\""
       " spellcheck=\"false\" autocomplete=\"off\" aria-label=\"" label "\""
       " title=\"Enter to confirm, Esc to cancel\" value=\"" (escape-html value) "\">"))

(defn- tabs-html []
  (let [{:keys [main active names]} @meta-state
        {:keys [mode tab value]} @editing
        pending-delete @confirm-delete]
    (str
     (apply str
            (for [n names]
              (if (and (= mode :rename) (= tab n))
                (name-input-html n "rename buffer")
                (let [m? (= n main)
                      confirm? (= n pending-delete)]
                  (str "<button class=\"buf-tab" (when (= n active) " active") (when m? " main")
                       "\" data-buffer=\"" (escape-html n) "\""
                       (when m? " title=\"main buffer — Alt+Enter evaluates from here\"")
                       ">" (escape-html n)
                       (when-not m?
                         (if confirm?
                           "<span class=\"buf-close confirm\" data-act=\"delete\" title=\"click again to delete this buffer\">delete?</span>"
                           "<span class=\"buf-close\" data-act=\"delete\" title=\"delete buffer\">×</span>"))
                       "</button>")))))
     (if (= mode :add)
       (name-input-html value "new buffer name")
       "<button class=\"buf-add\" data-act=\"add\" title=\"new buffer\">+</button>")
     "<span class=\"buf-spacer\"></span>"
     "<button class=\"buf-io\" data-act=\"export\" title=\"export the project as a .repulse file\">export</button>"
     "<button class=\"buf-io\" data-act=\"import\" title=\"import a .repulse file\">import</button>"
     "<input type=\"file\" class=\"buf-import-input\" accept=\".repulse\" hidden>")))

(defn render-tabs! []
  (when-let [el (.getElementById js/document "buffer-tabs")]
    (set! (.-innerHTML el) (tabs-html))
    (when-let [input (.querySelector el ".buf-name-input")]
      (.focus input)
      (.select input))))

;;; Operations

(defn- after-switch! []
  (editor/clear-highlights!)
  (doseq [f @switch-hooks] (f))
  (render-tabs!)
  (session/schedule-save!))

(defn switch-to!
  "Show buffer `name` in the editor."
  [name]
  (when-let [v (view)]
    (when (and (some #(= name %) (:names @meta-state))
               (not= name (active-name)))
      (store-active-state!)
      (.setState v (get @states name))
      (swap! meta-state assoc :active name)
      (after-switch!))
    (.focus v)))

(defn create-buffer!
  "Add a buffer with `code` and make it active. {:ok name} | {:error reason}"
  [name code]
  (let [r (project/add-buffer (current-project) name (or code ""))]
    (if-let [p (:ok r)]
      (do
        (swap! states assoc name (editor/make-state (or code "") @extensions))
        (swap! meta-state assoc :names (project/buffer-names p))
        (switch-to! name)
        {:ok name})
      r)))

(defn rename-buffer!
  "{:ok project} | {:error reason}. `use` references are not rewritten."
  [old new]
  (let [r (project/rename-buffer (current-project) old new)]
    (when-let [p (:ok r)]
      (when (not= old new)
        (store-active-state!)
        (swap! states (fn [m] (-> m (assoc new (get m old)) (dissoc old))))
        (swap! goto/jump-stack
               (fn [s] (mapv #(if (= old (:buffer %)) (assoc % :buffer new) %) s)))
        (sync-meta! p)
        (render-tabs!)
        (session/schedule-save!)))
    r))

(defn delete-buffer!
  "{:ok project} | {:error reason}. The main buffer can't be deleted."
  [name]
  (let [r (project/remove-buffer (current-project) name)]
    (when-let [p (:ok r)]
      (when (= name (active-name))
        (switch-to! (:main p)))
      (swap! states dissoc name)
      (sync-meta! (assoc p :active (active-name)))
      (render-tabs!)
      (session/schedule-save!))
    r))

(defn- load-project! [p]
  (reset! states (into {} (map (fn [{:keys [name code]}]
                                 [name (editor/make-state code @extensions)]))
                       (:buffers p)))
  (sync-meta! p))

(defn replace-project!
  "Replace every buffer (import). The project's :active buffer is shown."
  [p]
  (when-let [v (view)]
    (load-project! p)
    (.setState v (get @states (:active p)))
    (reset! goto/jump-stack [])
    (after-switch!)))

(defn clear-all-diagnostics!
  "Clear diagnostics in the shown buffer and in every inactive state."
  []
  (when-let [v (view)]
    (.dispatch v (setDiagnostics (.-state v) #js [])))
  (let [active (active-name)]
    (swap! states
           (fn [m]
             (into {} (map (fn [[n st]]
                             [n (if (= n active)
                                  st
                                  (.-state (.update st (setDiagnostics st #js []))))]))
                   m)))))

;;; Export / import

(defn- export! []
  (let [p    (current-project)
        blob (js/Blob. #js [(project/project->edn p)] #js {:type "application/edn"})
        url  (js/URL.createObjectURL blob)
        a    (.createElement js/document "a")]
    (set! (.-href a) url)
    (set! (.-download a) (str (:main p) ".repulse"))
    (.appendChild (.-body js/document) a)
    (.click a)
    (.remove a)
    (js/setTimeout #(js/URL.revokeObjectURL url) 1000)
    (set-output! (str "exported " (:main p) ".repulse — " (count (:buffers p)) " buffers") :success)))

(defn- import-file! [^js file]
  (-> (.text file)
      (.then (fn [text]
               (let [r (project/edn->project text)]
                 (if-let [p (:ok r)]
                   (let [n (count (:buffers p))]
                     (when (js/confirm (str "Replace the current project ("
                                            (count (:names @meta-state)) " buffers) with "
                                            (.-name file) "?"))
                       (replace-project! p)
                       (set-output! (str "imported " (.-name file) " — " n " buffers") :success)))
                   (set-output! (:error r) :error)))))
      (.catch (fn [_] (set-output! "Import failed: could not read the file" :error)))))

;;; DOM events

(defn- clear-confirm! []
  (when-let [t @confirm-timer] (js/clearTimeout t))
  (reset! confirm-timer nil)
  (reset! confirm-delete nil))

(defn- arm-confirm! [tab]
  (clear-confirm!)
  (reset! confirm-delete tab)
  (reset! confirm-timer
          (js/setTimeout (fn [] (reset! confirm-timer nil)
                           (reset! confirm-delete nil)
                           (render-tabs!))
                         confirm-ms))
  (render-tabs!))

(defn- start-editing! [state]
  (clear-confirm!)
  (reset! editing state)
  (render-tabs!))

(defn- cancel-editing! []
  (when @editing
    (reset! editing nil)
    (render-tabs!)
    (some-> (view) (.focus))))

(defn- commit-editing!
  "Create or rename from the inline name input. On an invalid name the input
   stays open with the typed text selected."
  [^js input]
  (when-let [{:keys [mode tab]} @editing]
    (let [n (str/trim (.-value input))
          r (if (= mode :add)
              (do (reset! editing nil) (create-buffer! n ""))
              (do (reset! editing nil) (rename-buffer! tab n)))]
      (if-let [err (:error r)]
        (do (reset! editing (if (= mode :add) {:mode :add :value n} {:mode :rename :tab tab}))
            (error! err)
            (.select input))
        (do (render-tabs!)
            (some-> (view) (.focus)))))))

(defn- on-click [^js e]
  (let [t   (.-target e)
        act (some-> (.closest t "[data-act]") (.getAttribute "data-act"))
        tab (some-> (.closest t "[data-buffer]") (.getAttribute "data-buffer"))]
    (when-not (.closest t ".buf-name-input")
      (if (and (= act "delete") tab)
        (if (= tab @confirm-delete)
          (do (clear-confirm!)
              (when-let [err (:error (delete-buffer! tab))] (error! err))
              (render-tabs!))
          (arm-confirm! tab))
        (do
          (when @confirm-delete (clear-confirm!) (render-tabs!))
          (case act
            "add"
            (start-editing! {:mode :add :value (project/next-buffer-name (current-project))})

            "export" (export!)

            "import"
            (when-let [input (.querySelector (.-currentTarget e) ".buf-import-input")]
              (set! (.-value input) "")
              (.click input))

            (when tab (switch-to! tab))))))))

(defn- on-dblclick [^js e]
  (let [t (.-target e)]
    (when-not (.closest t "[data-act]")
      (when-let [tab (some-> (.closest t "[data-buffer]") (.getAttribute "data-buffer"))]
        (start-editing! {:mode :rename :tab tab})))))

(defn- on-keydown [^js e]
  (let [t (.-target e)]
    (when (.contains (.-classList t) "buf-name-input")
      (case (.-key e)
        "Enter"  (do (.preventDefault e) (commit-editing! t))
        "Escape" (do (.preventDefault e) (cancel-editing!))
        nil)
      (.stopPropagation e))))

(defn- on-focusout [^js e]
  (let [t (.-target e)]
    (when (and (.contains (.-classList t) "buf-name-input") (.-isConnected t))
      (cancel-editing!))))

(defn- on-change [^js e]
  (let [input (.-target e)]
    (when (.contains (.-classList input) "buf-import-input")
      (when-let [file (aget (.-files input) 0)]
        (import-file! file)))))

;;; Init

(defn init!
  "Create one EditorState per buffer of `project` (exts: the extension array)
   and show the active buffer in `view`.
   opts — {:set-output! f}"
  ([view project exts] (init! view project exts nil))
  ([view project exts {:keys [set-output!]}]
   (reset! extensions exts)
   (reset! output-fn set-output!)
   (load-project! project)
   (.setState view (get @states (:active project)))
   (when-let [el (.getElementById js/document "buffer-tabs")]
     (.addEventListener el "click" on-click)
     (.addEventListener el "dblclick" on-dblclick)
     (.addEventListener el "change" on-change)
     (.addEventListener el "keydown" on-keydown)
     (.addEventListener el "focusout" on-focusout))
   (render-tabs!)
   ;; Test/debug handle (used by e2e/buffers.spec.ts).
   (set! (.-__REPULSE_BUFFERS__ js/window)
         #js {:project  #(clj->js (current-project))
              :switchTo switch-to!
              :create   (fn [n c] (clj->js (create-buffer! n c)))})))
