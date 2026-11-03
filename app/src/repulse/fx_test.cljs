(ns repulse.fx-test
  (:require [cljs.test :refer [deftest is testing]]
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
