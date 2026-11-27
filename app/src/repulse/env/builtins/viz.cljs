(ns repulse.env.builtins.viz
  "Phase VIZ1 — in-code visualizers: pianoroll punchcard spiral scope spectrum.
   Each takes ([pat]) or ([opts pat]) and returns pat with a visualizer entry
   appended to its :viz vector. Events are unchanged, so playback, export and
   tests are unaffected; the editor shows the views under the track's form
   (repulse.ui.inline-viz).

   A combinator applied after a visualizer builds a new pattern and drops
   :viz. viz-calls records the visualizer calls of the current evaluation so
   `track` can warn when a call had no effect (decision 7)."
  (:require [clojure.string :as str]
            [repulse.core :as core]
            [repulse.lisp.eval :as leval]
            [repulse.ui.viz-model :as vm]))

;; Visualizer calls not yet claimed by a `track` of the current evaluation:
;; [{:kind kw :pattern p}]. Reset per evaluation and after every `track`.
(defonce viz-calls (atom []))

(def max-per-track 3)

(defn- src-data [x]
  (when-let [{:keys [from to]} (:source x)]
    (when from {:from from :to to})))

(defn- show [v]
  (let [s (pr-str (leval/deep-unwrap v))]
    (if (> (count s) 40) (str (subs s 0 39) "…") s)))

(defn- viz-fn [kind]
  (let [nm (name kind)]
    (fn
      ([] (throw (ex-info (str nm " expects a pattern as its last argument, e.g. (" nm " (seq :c3 :e3))")
                          {:type :eval-error})))
      ([pat] ((viz-fn kind) nil pat))
      ([opts pat & more]
       (when (seq more)
         (throw (ex-info (str nm " takes an options map and a pattern, e.g. (" nm " {…} (seq :c3 :e3))")
                         (merge {:type :eval-error} (src-data (first more))))))
       (let [p (leval/unwrap pat)
             o (leval/deep-unwrap opts)]
         (when-not (core/pattern? p)
           (throw (ex-info (str nm " expects a pattern as its last argument, got " (show pat))
                           (merge {:type :eval-error} (src-data pat)))))
         (let [{:keys [ok error]} (vm/validate-opts kind o)]
           (when error
             (throw (ex-info error (merge {:type :eval-error} (or (src-data opts) (src-data pat))))))
           (let [out (update p :viz (fnil conj []) {:kind kind :opts ok})]
             (swap! viz-calls conj {:kind kind :pattern out})
             out)))))))

(defn check-track!
  "Called by `track` with its pattern. Warns (warn! {:message :from :to})
   when a visualizer of this evaluation had no effect, or when more than 3 are
   stacked. → the pattern to register (:viz cut to 3)."
  [warn! track-name pat src]
  (let [calls @viz-calls
        vz    (:viz pat)
        tn    (str ":" (name track-name))]
    (reset! viz-calls [])
    (when (and (empty? vz) (seq calls))
      (let [nm (name (:kind (peek calls)))]
        (warn! (merge {:message (str nm " has no effect here — it must wrap the whole track pattern, e.g. (track "
                                     tn " (" nm " …))")}
                      src))))
    (if (> (count vz) max-per-track)
      (do (warn! (merge {:message (str "track " tn " shows at most " max-per-track " visualizers — ignoring "
                                       (str/join " " (map (comp name :kind) (drop max-per-track vz))))}
                        src))
          (assoc pat :viz (vec (take max-per-track vz))))
      pat)))

(defn reset-calls! [] (reset! viz-calls []))

(defn make-builtins
  "The five visualizer built-ins."
  [_ctx]
  (into {} (map (fn [k] [(name k) (viz-fn k)])) vm/viz-kinds))
