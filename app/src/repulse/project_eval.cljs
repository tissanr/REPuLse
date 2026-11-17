(ns repulse.project-eval
  "Multi-buffer project evaluation (Phase SONG4).
   run-project evaluates the main buffer with `use` enabled; `use` evaluates
   other buffers depth-first, in argument order, at most once per project
   evaluation, all in one shared environment. Every buffer is read with its
   global offset base (repulse.project/offset-bases), so errors and event
   sources carry one global position that repulse.project/locate maps back.
   No DOM."
  (:require [clojure.string :as str]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.project :as project]
            [repulse.song-transport :as song-transport]))

;; {:project :env :bases :stack [names] :done #{names} :order [names]} while
;; run-project is active, else nil.
(defonce ^:private ctx (atom nil))

(defn- fail!
  ([msg] (throw (ex-info msg {:type :eval-error})))
  ([msg src]
   (throw (ex-info msg (cond-> {:type :eval-error}
                         (:from src) (assoc :from (:from src) :to (:to src)))))))

(defn- use-one!
  "Evaluate one buffer named by keyword argument `arg` (a SourcedVal)."
  [arg]
  (let [{:keys [project env bases stack done]} @ctx
        kw    (leval/unwrap arg)
        src   (leval/source-of arg)
        bname (name kw)
        names (project/buffer-names project)]
    (cond
      (not (some #(= bname %) names))
      (fail! (str "Unknown buffer :" bname
                  (when-let [h (leval/typo-hint bname names)]
                    (str " — did you mean :" h "?")))
             src)

      (some #(= bname %) stack)
      (fail! (str "Circular use: " (str/join " → " (conj stack bname))) src)

      (contains? done bname)
      nil

      :else
      (do
        (swap! ctx #(-> % (update :stack conj bname) (update :order conj bname)))
        (let [r (lisp/eval-string (project/code-of project bname) env
                                  {:offset (get bases bname)})]
          (when (lisp/eval-error? r)
            (if-let [esrc (:source r)]
              (fail! (:message r) esrc)
              (fail! (str "in buffer :" bname ": " (:message r)) src))))
        (swap! ctx #(-> % (update :stack pop) (update :done conj bname)))
        nil))))

(defn use-builtin
  "(use :drums :bass …) — evaluate other project buffers first."
  [& args]
  (when (nil? @ctx)
    (fail! "use only works when the project is evaluated (Alt+Enter in the editor)"))
  (when (empty? args)
    (fail! "use needs at least one buffer name, e.g. (use :drums)"))
  (doseq [a args]
    (let [v (leval/unwrap a)]
      (when-not (keyword? v)
        (fail! (str "use expects buffer names as keywords, e.g. (use :drums) — got " (pr-str v))
               (leval/source-of a)))))
  (doseq [a args] (use-one! a))
  (str "=> used " (str/join " " (map #(str ":" (name (leval/unwrap %))) args))))

(defn run-project
  "Evaluate (:main project) in env with `use` enabled.
   → {:value <{:result v} | EvalError> :order [names in start order] :bases {...}}"
  [project env]
  (let [main  (:main project)
        bases (project/offset-bases project)]
    (reset! ctx {:project project :env env :bases bases
                 :stack [main] :done #{main} :order [main]})
    (reset! song-transport/buffer-of
            (fn [src]
              (when-let [f (:from src)]
                (:buffer (project/locate bases f f)))))
    (try
      (let [value (lisp/eval-string (project/code-of project main) env {:offset 0})]
        {:value value :order (:order @ctx) :bases bases})
      (finally
        (reset! ctx nil)
        (reset! song-transport/buffer-of nil)))))
