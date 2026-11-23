(ns repulse.ui.outline-model
  "Pure run-state model for the outline (Phase WS6).")

(defn track-state
  "Run state of a code track outside a song: :playing | :muted | :stopped.
   kw is the track keyword, running = set of scheduled keywords, muted = set."
  [kw running muted]
  (cond (not (contains? running kw)) :stopped
        (contains? muted kw)         :muted
        :else                        :playing))

(defn- preview? [kw] (.startsWith (name kw) "__"))

(defn running-rows
  "Rows for the running group, sorted by name with :_ last.
   running       = set of scheduled track keywords,
   muted         = set of muted keywords,
   active-tracks = set of keywords declared in the active buffer,
   owner         = {kw buffer-name} for tracks declared in other buffers.
   → [{:kw :extra :state :playing :origin :none}
      {:kw :hats  :state :muted   :origin :buffer :buffer \"drums\"}
      {:kw :_     :state :playing :origin :anonymous} …]"
  [running muted active-tracks owner]
  (->> running
       (remove preview?)
       (remove #(contains? active-tracks %))
       (map (fn [kw]
              (let [base {:kw kw :state (track-state kw running muted)}]
                (cond (= kw :_)             (assoc base :origin :anonymous)
                      (contains? owner kw)  (assoc base :origin :buffer :buffer (get owner kw))
                      :else                 (assoc base :origin :none)))))
       (sort-by (fn [{:keys [kw]}] [(if (= kw :_) 1 0) (name kw)]))
       vec))
