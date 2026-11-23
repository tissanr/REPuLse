(ns repulse.content.builtin-meta-test
  "Lisp layer of the env <-> builtin_meta.edn link (Phase QA4): every name bound by
   repulse.lisp.eval/make-env, and every special form, has an entry in
   builtin_meta.edn. The full app env (repulse.env.builtins) cannot load in the
   :test build; e2e/builtins-meta.spec.ts checks it in both directions."
  (:require [cljs.reader :as reader]
            [cljs.test :refer [deftest is testing]]
            [shadow.resource :as rc]
            [repulse.lisp.eval :as leval]))

(def ^:private meta-names
  (->> (reader/read-string (rc/inline "repulse/content/builtin_meta.edn"))
       keys
       (map name)
       set))

;; Names that are bound in the Lisp env but deliberately have no builtin_meta.edn
;; entry. A name may go here only if it is internal and not meant for users.
;; Starts empty.
(def ^:private lisp-excluded #{})

(deftest every-lisp-env-name-has-meta
  (let [env     (leval/make-env (fn []) (fn [_]))
        bound   (set (filter string? (keys env)))
        missing (sort (remove (into meta-names lisp-excluded) bound))]
    (is (seq bound) "make-env returned bindings")
    (is (= [] missing)
        (str "bound in make-env but missing from builtin_meta.edn: " (pr-str missing)))))

(deftest every-special-form-has-meta
  (let [missing (sort (remove (into meta-names lisp-excluded) leval/special-forms))]
    (is (= [] missing)
        (str "special forms missing from builtin_meta.edn: " (pr-str missing)))))

(deftest meta-has-the-required-keys
  (let [entries (reader/read-string (rc/inline "repulse/content/builtin_meta.edn"))
        required [:type :detail :signature :doc :category :returns :side-effects
                  :examples :see-also]
        bad     (for [[k e] entries
                      r required
                      :when (not (contains? e r))]
                  [(name k) r])]
    (testing "every entry carries all required keys"
      (is (= [] (vec bad))))))
