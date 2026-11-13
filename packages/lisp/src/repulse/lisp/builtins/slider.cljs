(ns repulse.lisp.builtins.slider
  "slider — a number that the editor draws as a draggable widget (Phase WS3).
   Evaluates to its value, clamped to [min, max].
   Exports: make-slider-fn, make-builtins."
  (:require [repulse.core :as core]
            [repulse.lisp.util :as u]))

(defn- show
  "Printed argument for error texts: \"a pattern\" or pr-str, ≤ 40 chars."
  [x]
  (let [v (u/unwrap x)]
    (if (core/pattern? v)
      "a pattern"
      (let [s (pr-str (u/deep-unwrap x))]
        (if (> (count s) 40) (str (subs s 0 39) "…") s)))))

(defn- fail!
  "Throw an eval error; uses the arg's source range when it has one, otherwise
   the evaluator falls back to the call head (the `slider` symbol)."
  [msg arg]
  (throw (ex-info msg (merge {:type :eval-error}
                             (when-let [src (when (some? arg) (u/source-of arg))]
                               (select-keys src [:from :to]))))))

(defn- finite-num
  "The arg as a finite JS number, or nil."
  [x]
  (let [n (u/->num x)]
    (when (and (number? n) (js/isFinite n)) n)))

(defn make-slider-fn
  "on-warning: nil or (fn [{:keys [message from to]}]) — called when v is clamped."
  [on-warning]
  (fn [& args]
    (let [n (count args)]
      (when-not (<= 3 n 4)
        (fail! (str "slider expects 3 or 4 arguments: (slider value min max) or "
                    "(slider value min max :log) — got " n)
               nil))
      (let [[va mina maxa mode-arg] args
            v  (or (finite-num va)
                   (fail! (str "slider value must be a number — got " (show va)) va))
            lo (or (finite-num mina)
                   (fail! (str "slider min must be a number — got " (show mina)) mina))
            hi (or (finite-num maxa)
                   (fail! (str "slider max must be a number — got " (show maxa)) maxa))
            mode (when (= n 4) (u/unwrap mode-arg))]
        (when (and (= n 4) (not (#{:log :lin} mode)))
          (fail! (str "slider scale must be :log or :lin — got " (show mode-arg)) mode-arg))
        (when-not (< lo hi)
          (fail! (str "slider min must be less than max — got min " lo ", max " hi) nil))
        (when (and (= mode :log) (<= lo 0))
          (fail! (str "slider :log needs min > 0 — got min " lo) mode-arg))
        (let [c (max lo (min hi v))]
          (when (and on-warning (not= c v))
            (on-warning (merge {:message (str "slider value " v " is outside [" lo ", " hi
                                              "] — clamped to " c)}
                               (select-keys (u/source-of va) [:from :to]))))
          c)))))

(defn make-builtins [] {"slider" (make-slider-fn nil)})
