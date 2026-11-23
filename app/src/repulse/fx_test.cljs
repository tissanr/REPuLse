(ns repulse.fx-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.audio :as audio]
            [repulse.core :as core]
            [repulse.env.builtins.fx :as b-fx]
            [repulse.fx :as fx]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.midi :as midi]
            [repulse.theory :as theory]
            [repulse.plugins :as plugins]
            [repulse.song-transport :as st]))

(defn- node-stub []
  #js {:connect (fn [_] nil)
       :disconnect (fn [] nil)})

(defn- thrown-message [f]
  (try
    (f)
    nil
    (catch :default e
      (.-message e))))

(deftest plugin-validation-normalizes-optional-methods
  (testing "documented optional visual and effect methods are installed as defaults"
    (let [visual #js {:type "visual"
                      :name "minimal-visual"
                      :mount (fn [_] nil)
                      :unmount (fn [] nil)}
          effect #js {:type "effect"
                      :name "minimal-effect"
                      :createNodes (fn [_] #js {:inputNode (node-stub)
                                                :outputNode (node-stub)})
                      :setParam (fn [_ _] nil)
                      :destroy (fn [] nil)}]
      (plugins/validate-plugin! visual)
      (plugins/normalize-plugin! visual)
      (is (fn? (.-init visual)))
      (is (fn? (.-destroy visual)))
      (plugins/validate-plugin! effect)
      (plugins/normalize-plugin! effect)
      (is (fn? (.-init effect)))
      (is (fn? (.-bypass effect)))
      (is (fn? (.-getParams effect))))))

(deftest plugin-validation-rejects-missing-required-methods
  (testing "invalid plugins fail with clear field names"
    (let [bad #js {:type "effect" :name "bad-effect" :createNodes (fn [_] #js {})}]
      (is (re-find #"bad-effect.*setParam.*destroy"
                   (thrown-message #(plugins/validate-plugin! bad)))))))

(deftest effect-node-validation-rejects-disconnected-contracts
  (testing "createNodes must return connectable input and output nodes"
    (is (re-find #"createNodes"
                 (thrown-message
                  #(fx/validate-effect-nodes! "bad" #js {:inputNode (node-stub)}))))
    (is (= "ok"
           (do (fx/validate-effect-nodes! "ok" #js {:inputNode (node-stub)
                                                    :outputNode (node-stub)})
               "ok")))))

(deftest apply-track-effects-replaces-chain-and-applies-params
  (testing "pattern FX metadata is applied as a fresh track chain"
    (let [calls (atom [])]
      (with-redefs [fx/clear-track-effects! (fn [track-name]
                                              (swap! calls conj [:clear track-name]))
                    fx/add-track-effect!    (fn [track-name effect-name]
                                              (swap! calls conj [:add track-name effect-name]))
                    fx/set-track-param!     (fn [track-name effect-name param-name value]
                                              (swap! calls conj [:set track-name effect-name param-name value]))]
        (fx/apply-track-effects! :_ [{:name "delay"
                                      :params (array-map "time" 0.5 "wet" 0.3)}
                                     {:name "distort"
                                      :params (array-map "drive" 100)}]))
      (is (= [[:clear :_]
              [:add :_ "delay"]
              [:set :_ "delay" "time" 0.5]
              [:set :_ "delay" "wet" 0.3]
              [:add :_ "distort"]
              [:set :_ "distort" "drive" 100]]
             @calls)))))

(deftest per-track-waveshape-accepts-parenthesized-curve
  (testing "DST5 waveshape curve data can use a parenthesized numeric list"
    (let [played (atom nil)
          env    (merge (leval/make-env (fn [] nil) (fn [_] nil))
                        (b-fx/make-builtins nil)
                        {"track" (fn [_name pat]
                                   (let [pat' (leval/unwrap pat)]
                                     (reset! played pat')
                                     pat'))})
          code   "(track :melody
                    (->> (scale :minor :a4 (seq 0 2 4 7 4 2))
                         (slow 2)
                         (amp 0.4)
                         (attack 0.1)
                         (fx :waveshape :curve (-1.0 -0.8 -0.3 0 0.3 0.9 1.0) :drive 3)))"
          result (lisp/eval-string code env)
          pat    @played
          fx-meta (first (:track-fx pat))]
      (is (not (lisp/eval-error? result)) (:message result))
      (is (core/pattern? pat))
      (is (= "waveshape" (:name fx-meta)))
      (is (= [-1.0 -0.8 -0.3 0 0.3 0.9 1.0]
             (vec (get-in fx-meta [:params "curve"]))))
      (is (= 3 (get-in fx-meta [:params "drive"]))))))

(deftest dattorro-alias-targets-dattorro-reverb
  (testing "the short :dattorro spelling works for global FX"
    (let [calls (atom [])]
      (with-redefs [fx/set-param! (fn [effect-name param-name value]
                                    (swap! calls conj [effect-name param-name value]))]
        ((get (b-fx/make-builtins nil) "fx") :dattorro 0.35)
        ((get (b-fx/make-builtins nil) "fx") :dattorro :wet 0.8 :decay 0.7))
      (is (= [["dattorro-reverb" "value" 0.35]
              ["dattorro-reverb" "wet" 0.8]
              ["dattorro-reverb" "decay" 0.7]]
             @calls))))
  (testing "the short :dattorro spelling works for per-track FX metadata"
    (let [env    (merge (leval/make-env (fn [] nil) (fn [_] nil))
                        (b-fx/make-builtins nil))
          result (lisp/eval-string "(->> (seq :bd) (fx :dattorro :wet 0.8))" env)
          pat    (:result result)
          fx-meta (first (:track-fx pat))]
      (is (not (lisp/eval-error? result)) (:message result))
      (is (core/pattern? pat))
      (is (= "dattorro-reverb" (:name fx-meta)))
      (is (= 0.8 (get-in fx-meta [:params "wet"]))))))

(deftest reset-global-effects-resets-plugin-state-and-flags
  (testing "global plugin params do not leak between evaluations"
    (let [calls  (atom [])
          plugin #js {:resetParams (fn [] (swap! calls conj :reset))}]
      (reset! fx/chain [{:name "distort"
                         :plugin plugin
                         :active? true
                         :bypassed? true}])
      (fx/reset-global-effects!)
      (is (= [:reset] @calls))
      (is (= [{:name "distort"
               :plugin plugin
               :active? false
               :bypassed? false}]
             @fx/chain)))))

(deftest tl5-note-keywords-reach-midi-math
  (testing "note keywords from scale/chord/voicing convert to the right MIDI numbers (MIDI out / export)"
    (is (= 63 (midi/hz->midi (theory/note->hz :ds4))))
    (is (= [60 63 67 70] (mapv #(midi/hz->midi (theory/note->hz %)) [:c4 :ds4 :g4 :as4])))))

;;; ── master form (Phase WS2) ──────────────────────────────────────────

(defn- master-fixture
  "Run f with a two-effect stub chain; f receives [log eval!]. eval! resets state
   then evaluates the source, returning the eval result."
  [f]
  (let [log   (atom [])
        mk    (fn [nm] #js {:setParam   (fn [p v] (swap! log conj [nm p v]))
                            :resetParams (fn [])})
        p-rev (mk "reverb")
        p-del (mk "delay")
        env   (assoc (merge (leval/make-env (fn [] nil) (fn [_] nil))
                            (b-fx/make-builtins nil))
                     :*master-fn* b-fx/run-master!)
        eval! (fn [src]
                (reset! fx/chain [{:name "reverb" :plugin p-rev :active? false :bypassed? false}
                                  {:name "delay"  :plugin p-del :active? false :bypassed? false}])
                (fx/reset-global-effects!)
                (b-fx/reset-master-run!)
                (reset! log [])
                (let [r (lisp/eval-string src env)]
                  {:result r :chain @fx/chain :log @log}))]
    (f log eval! p-rev p-del)))

(deftest master-equals-top-level-fx
  (master-fixture
   (fn [_ eval! p-rev p-del]
     (testing "F1"
       (let [a (eval! "(master (fx :reverb 0.3))")
             b (eval! "(fx :reverb 0.3)")]
         (is (= (:chain a) (:chain b)))
         (is (= [{:name "reverb" :plugin p-rev :active? true :bypassed? false}
                 {:name "delay" :plugin p-del :active? false :bypassed? false}]
                (:chain a)))
         (is (= [["reverb" "value" 0.3]] (:log a)))
         (is (= (:log a) (:log b)))))
     (testing "F2"
       (let [a (eval! "(master (fx :delay :time 0.375 :feedback 0.4) (fx :reverb 0.3))")
             b (eval! "(fx :delay :time 0.375 :feedback 0.4)\n(fx :reverb 0.3)")
             expected [["delay" "time" 0.375] ["delay" "feedback" 0.4] ["reverb" "value" 0.3]]]
         (is (= (:chain a) (:chain b)))
         (is (every? :active? (:chain a)))
         (is (= expected (:log a)))
         (is (= expected (:log b))))))))

(deftest master-result-string
  (master-fixture
   (fn [_ eval! _ _]
     (let [a (eval! "(master (fx :reverb 0.3))")]
       (is (= {:result "=> master: 1 fx"} (:result a)))
       (is (true? (b-fx/master-result? "=> master: 1 fx")))))))

(deftest master-second-block-is-error
  (master-fixture
   (fn [_ eval! _ _]
     (testing "F4: same buffer"
       (let [a (eval! "(master (fx :reverb 0.3))\n(master (fx :delay 0.25))")]
         (is (lisp/eval-error? (:result a)))
         (is (= "Only one (master …) per buffer — move these (fx …) forms into the first (master …)"
                (:message (:result a))))
         (is (= {:from 26 :to 51} (:source (:result a))))
         (is (= [["reverb" "value" 0.3]] (:log a)))))
     (testing "F5: other project buffer"
       (reset! st/buffer-of (fn [src] (if (< (:from src) 26) "fx" "main")))
       (try
         (let [a (eval! "(master (fx :reverb 0.3))\n(master (fx :delay 0.25))")]
           (is (= "Only one (master …) per project — already defined in buffer :fx"
                  (:message (:result a)))))
         (finally (reset! st/buffer-of nil)))))))

(deftest master-per-track-child-is-error
  (master-fixture
   (fn [_ eval! _ _]
     (let [a (eval! "(master (fx :reverb 0.3 (seq :bd)))")]
       (is (lisp/eval-error? (:result a)))
       (is (= "master only takes global (fx …) forms — (fx :reverb 0.3 (seq :bd)) is a per-track effect; put it in the track's ->> chain"
              (:message (:result a))))
       (is (= {:from 8 :to 34} (:source (:result a))))))))

(deftest master-loose-fx-hint
  (master-fixture
   (fn [_ eval! _ _]
     (let [orig js/console.info
           msgs (atom [])]
       (set! js/console.info (fn [m] (swap! msgs conj m)))
       (try
         (reset! b-fx/hinted? false)
         (eval! "(fx :delay 0.25)\n(master (fx :reverb 0.3))")
         (is (= b-fx/LOOSE-FX-HINT (b-fx/loose-fx-hint!)))
         (eval! "(fx :delay 0.25)\n(master (fx :reverb 0.3))")
         (is (nil? (b-fx/loose-fx-hint!)))
         (reset! b-fx/hinted? false)
         (eval! "(master (fx :reverb 0.3))")
         (is (nil? (b-fx/loose-fx-hint!)))
         (is (= [b-fx/LOOSE-FX-HINT] @msgs))
         (finally (set! js/console.info orig)))))))

;;; ── Phase EXP1 — param log, plugin instantiation, getParams flag ──────

(deftest set-param-writes-param-log
  (let [saved-chain @fx/chain
        saved-log   @fx/param-log
        calls       (atom [])
        plugin      #js {:setParam    (fn [p v] (swap! calls conj [p v]))
                         :resetParams (fn [] nil)}]
    (try
      (reset! fx/chain [{:name "bitcrusher" :plugin plugin :active? false :bypassed? false}])
      (reset! fx/param-log {})
      (fx/set-param! "bitcrusher" "wet" 1)
      (fx/set-param! "bitcrusher" "bits" 3)
      (fx/set-param! "missing" "x" 1)
      (testing "set-param! logs every call in order (E6)"
        (is (= {"bitcrusher" [["wet" 1] ["bits" 3]]} @fx/param-log))
        (is (= [["wet" 1] ["bits" 3]] @calls))
        (is (:active? (first @fx/chain))))
      (testing "reset-global-effects! clears the log"
        (fx/reset-global-effects!)
        (is (= {} @fx/param-log)))
      (finally
        (reset! fx/chain saved-chain)
        (reset! fx/param-log saved-log)))))

(deftest instantiate-plugin-test
  (testing "clone() wins when present"
    (let [made (atom 0)
          p    #js {:name "c" :clone (fn [] (swap! made inc) #js {:name "fresh"})}]
      (is (= "fresh" (.-name (fx/instantiate-plugin p))))
      (is (= 1 @made))))
  (testing "otherwise a copy that shares the prototype"
    (let [p     #js {:name "plain" :_x 1}
          fresh (fx/instantiate-plugin p)]
      (is (not (identical? p fresh)))
      (is (= "plain" (.-name fresh)))
      (is (= 1 (.-_x fresh))))))

(deftest normalize-plugin-flags-default-get-params
  (let [bare  #js {:type "effect" :name "bare"
                   :createNodes (fn [_] nil) :setParam (fn [_ _] nil) :destroy (fn [] nil)}
        own   #js {:type "effect" :name "own" :getParams (fn [] #js {:wet 1})
                   :createNodes (fn [_] nil) :setParam (fn [_ _] nil) :destroy (fn [] nil)}]
    (plugins/normalize-plugin! bare)
    (plugins/normalize-plugin! own)
    (is (true? (aget bare "__repulseDefaultGetParams")))
    (is (nil? (aget own "__repulseDefaultGetParams")))))

(deftest notify-plugins-test
  (let [seen (atom [])
        p    #js {:onEvent (fn [n t] (swap! seen conj [n t]))}]
    (fx/notify-plugins! [p #js {} nil] :bd 1.5)
    (fx/notify-plugins! [p] {:note :c4} 2.0)
    (fx/notify-plugins! [p] 440 3.0)
    (is (= [["bd" 1.5] ["c4" 2.0]] @seen))))

;;; ── Phase FX1 — chain order, parameter descriptors, tempo ─────────────

(defn- fx1-node []
  #js {:connect (fn [_] nil) :disconnect (fn [] nil)})

(defn- fake-effect
  "A plugin stub named nm; calls are logged into log as [nm method args…]."
  ([nm log] (fake-effect nm log nil))
  ([nm log params]
   (let [p #js {:name        nm
                :createNodes (fn [_] #js {:inputNode (fx1-node) :outputNode (fx1-node)})
                :setParam    (fn [k v] (swap! log conj [nm :set k v]))
                :setTempo    (fn [cd origin] (swap! log conj [nm :tempo cd origin]))
                :resetParams (fn [] nil)
                :destroy     (fn [] nil)}]
     (when params (set! (.-params p) params))
     p)))

(defn- with-fake-audio
  "Run f with stub audio nodes, an empty chain and cycle-dur 2.0."
  [f]
  (let [saved-chain @fx/chain
        saved-gain  @audio/master-gain
        saved-tap   @audio/master-tap
        saved-cd    (:cycle-dur @audio/scheduler-state)]
    (try
      (reset! fx/chain [])
      (reset! audio/master-gain (fx1-node))
      (reset! audio/master-tap (fx1-node))
      (swap! audio/scheduler-state assoc :cycle-dur 2.0)
      (with-redefs [audio/get-ctx (fn [] #js {})]
        (f))
      (finally
        (reset! fx/chain saved-chain)
        (reset! audio/master-gain saved-gain)
        (reset! audio/master-tap saved-tap)
        (swap! audio/scheduler-state assoc :cycle-dur saved-cd)))))

(deftest fx1-chain-order-is-independent-of-load-order
  (with-fake-audio
   (fn []
     (let [log (atom [])]
       (doseq [nm ["limiter" "width" "auto-pan" "my-plugin" "cab" "flanger"
                   "chorus" "delay" "reverb" "compressor" "eq" "gate"]]
         (fx/add-effect! (fake-effect nm log)))
       (is (= ["gate" "eq" "compressor" "reverb" "delay" "chorus" "flanger" "cab"
               "my-plugin" "auto-pan" "width" "limiter"]
              (mapv :name @fx/chain)))))))

(deftest fx1-chain-rank-unknown-between-cab-and-auto-pan
  (is (< (fx/chain-rank "cab") (fx/chain-rank "user-thing") (fx/chain-rank "auto-pan")))
  (is (= (fx/chain-rank "a") (fx/chain-rank "b")))
  (testing "sort is stable for equal ranks"
    (is (= ["x" "y"] (mapv :name (fx/sort-chain [{:name "x"} {:name "y"}]))))))

(deftest fx1-parse-fx-args
  (is (= [["value" 0.4] ["dry" 1]] (fx/parse-fx-args [0.4 :dry 1])))
  (is (= [["wet" 0.3] ["time" [1 4]]] (fx/parse-fx-args [:wet 0.3 :time [1 4]])))
  (is (= [["value" 0.4]] (fx/parse-fx-args [0.4])))
  (is (= [] (fx/parse-fx-args []))))

(def ^:private reverb-desc
  #js {:value "wet" :wet "number" :dry "number"
       :type #js ["classic" "room" "hall" "plate" "spring" "gated" "reverse"]
       :decay "number" :size "number" :damp "number" :predelay "number"})

(deftest fx1-prepare-param-rules
  (let [rev #js {:params reverb-desc}
        eq  #js {:params #js {:low "number" :low-freq "number"}}
        dly #js {:params #js {:value "wet" :wet "number" :time "number" :taps "list"}}
        old #js {}]
    (testing "positional value renamed to the descriptor's target"
      (is (= {:name "wet" :value 0.4 :warning nil :send? true}
             (fx/prepare-param rev "reverb" "value" 0.4))))
    (testing "positional value without a target"
      (is (= {:name "value" :value 3 :send? false
              :warning "fx :eq takes no positional value — parameters: low, low-freq"}
             (fx/prepare-param eq "eq" "value" 3))))
    (testing "unknown parameter"
      (is (= "fx :reverb has no parameter :room — parameters: wet, dry, type, decay, size, damp, predelay"
             (:warning (fx/prepare-param rev "reverb" "room" 0.8))))
      (is (false? (:send? (fx/prepare-param rev "reverb" "room" 0.8)))))
    (testing "numbers: rationals convert, non-numbers warn"
      (is (= 0.1875 (:value (fx/prepare-param dly "delay" "time" [3 16]))))
      (is (= "fx :delay :time expects a number — got :fast"
             (:warning (fx/prepare-param dly "delay" "time" :fast)))))
    (testing "enum: keyword or string, unknown value warns"
      (is (= "hall" (:value (fx/prepare-param rev "reverb" "type" :hall))))
      (is (= "room" (:value (fx/prepare-param rev "reverb" "type" "room"))))
      (is (= "fx :reverb :type has no value :cathedral — values: classic, room, hall, plate, spring, gated, reverse"
             (:warning (fx/prepare-param rev "reverb" "type" :cathedral)))))
    (testing "list: vector elements through ->num, a single value is a one-element list"
      (is (= [0.125 0.375] (vec (:value (fx/prepare-param dly "delay" "taps" [[1 8] [3 8]])))))
      (is (= [1 8] (vec (:value (fx/prepare-param dly "delay" "taps" [1 8])))))
      (is (= [0.5] (vec (:value (fx/prepare-param dly "delay" "taps" 0.5))))))
    (testing "plugins without a descriptor are unchanged"
      (is (= {:name "value" :value [1 4] :warning nil :send? true}
             (fx/prepare-param old "delay" "value" [1 4])))
      (is (= {:name "anything" :value :x :warning nil :send? true}
             (fx/prepare-param old "x" "anything" :x))))
    (testing "needs a parameter"
      (is (= "fx :reverb needs a parameter — parameters: wet, dry, type, decay, size, damp, predelay"
             (fx/needs-param-warning rev "reverb")))
      (is (nil? (fx/needs-param-warning old "reverb"))))))

(deftest fx1-global-fx-positional-and-named-and-warnings
  (let [saved @fx/chain
        log   (atom [])
        warns (atom [])
        env   (merge (leval/make-env (fn [] nil) (fn [_] nil))
                     (b-fx/make-builtins {:warn! #(swap! warns conj %)}))]
    (try
      (reset! fx/chain [{:name "reverb" :plugin (fake-effect "reverb" log reverb-desc)
                         :active? false :bypassed? false}])
      (testing "(fx :reverb 0.4 :dry 1) sets both parameters at top level"
        (lisp/eval-string "(fx :reverb 0.4 :dry 1)" env)
        (is (= [["reverb" :set "wet" 0.4] ["reverb" :set "dry" 1]] @log)))
      (testing "unknown parameter: warning on the keyword, nothing sent"
        (reset! log []) (reset! warns [])
        (lisp/eval-string "(fx :reverb :room 0.8)" env)
        (is (= [] @log))
        (is (= [{:message "fx :reverb has no parameter :room — parameters: wet, dry, type, decay, size, damp, predelay"
                 :from 12 :to 17}]
               @warns)))
      (testing "keyword enum is sent as a string; bad value warns, rest still applies"
        (reset! log []) (reset! warns [])
        (lisp/eval-string "(fx :reverb :type :cathedral :wet 0.4)" env)
        (is (= [["reverb" :set "wet" 0.4]] @log))
        (is (= 1 (count @warns))))
      (testing "(fx :reverb) with a descriptor warns on the effect keyword"
        (reset! log []) (reset! warns [])
        (lisp/eval-string "(fx :reverb)" env)
        (is (= [] @log))
        (is (= {:from 4 :to 11} (select-keys (first @warns) [:from :to]))))
      (testing "per-track form gets converted params"
        (reset! warns [])
        (let [r (lisp/eval-string "(->> (seq :bd) (fx :reverb :type :hall 0.5))" env)]
          (is (not (lisp/eval-error? r)))))
      (testing "per-track form: positional renamed, rational converted"
        (let [r   (lisp/eval-string "(->> (seq :bd) (fx :reverb 1/2 :type :hall))" env)
              fxm (first (:track-fx (:result r)))]
          (is (= {"wet" 0.5 "type" "hall"} (:params fxm)))))
      (finally (reset! fx/chain saved)))))

(deftest fx1-set-tempo-on-add-and-on-cycle-dur-change
  (with-fake-audio
   (fn []
     (let [log (atom [])]
       (fx/add-effect! (fake-effect "delay" log))
       (is (= [["delay" :tempo 2.0 0]] @log))
       (reset! log [])
       (fx/tempo-watch-fn nil nil {:cycle-dur 2.0 :x 1} {:cycle-dur 2.0 :x 2})
       (is (= [] @log) "other keys do not notify")
       (fx/tempo-watch-fn nil nil {:cycle-dur 2.0} {:cycle-dur 2.6667})
       (is (= [["delay" :tempo 2.6667 0]] @log))
       (testing "plugins without setTempo are skipped"
         (is (nil? (fx/call-set-tempo! #js {} 2.0 0))))))))
