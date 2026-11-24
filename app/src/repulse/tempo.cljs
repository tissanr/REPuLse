(ns repulse.tempo
  "Phase QA3 A1 — tempo anchor math. Pure, no requires.
   The scheduler state carries :anchor-time (audio seconds) and :anchor-cycle
   (integer). Cycle position x starts at anchor-time + (x − anchor-cycle) ×
   cycle-dur. A tempo change moves the anchor so that time stays continuous:
   the bars already scheduled keep the old tempo and the next bar starts
   exactly where the last one ends — no gap, no catch-up burst.")

(defn- anchor-time [state] (or (:anchor-time state) 0.0))
(defn- anchor-cycle [state] (or (:anchor-cycle state) 0))

(defn time-at-cycle
  "Audio time (seconds) at which cycle position x (integer or fractional)
   starts under state's anchor and :cycle-dur."
  [state x]
  (+ (anchor-time state) (* (- x (anchor-cycle state)) (:cycle-dur state))))

(defn cycle-at-time
  "Inverse of time-at-cycle: the (fractional) cycle position at audio time t."
  [state t]
  (+ (anchor-cycle state) (/ (- t (anchor-time state)) (:cycle-dur state))))

(defn retempo
  "state with :cycle-dur new-cd and the anchor moved so time stays continuous.
   Playing: re-anchor at the next unscheduled cycle (:cycle) at the time it
   would have started under the old tempo. Stopped: re-anchor at the next whole
   cycle after now (ceil of the cycle position at now)."
  [state now new-cd]
  (let [x (if (:playing? state)
            (:cycle state)
            (int (Math/ceil (cycle-at-time state now))))]
    (assoc state
           :cycle-dur    new-cd
           :anchor-time  (time-at-cycle state x)
           :anchor-cycle x)))
