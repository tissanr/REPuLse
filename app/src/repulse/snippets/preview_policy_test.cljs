(ns repulse.snippets.preview-policy-test
  (:require [cljs.test :refer [deftest is]]
            [repulse.ai.knowledge :as knowledge]
            [repulse.snippets.preview-policy :as policy]))

(deftest every-builtin-follows-decision-9
  (is (> (count knowledge/builtins) 100))
  (doseq [[name meta] knowledge/builtins]
    (is (= (policy/allowed? name)
           (or (empty? (get meta "side-effects"))
               (contains? policy/preview-extra name)))
        name)))

(deftest side-effecting-builtins-are-denied
  (doseq [n ["restore!" "delete-snapshot!" "rename-snapshot!" "snapshot!"
             "freesound-key!" "ai" "export" "midi-export" "midi-map"
             "midi-clock-out!" "reset!" "upd" "tap!"]]
    (is (contains? knowledge/builtins n) (str n " is documented"))
    (is (not (policy/allowed? n)) n)))

(deftest unknown-names-are-denied
  (is (not (policy/allowed? "no-such-builtin")))
  (is (not (policy/allowed? {} "seq")) "missing metadata = denied"))

(deftest pure-builtins-are-allowed
  (doseq [n ["seq" "stack" "fast" "every" "euclidean" "amp" "scale"]]
    (is (policy/allowed? n) n)))

(deftest preview-extra-names-exist
  (doseq [n policy/preview-extra]
    (is (or (contains? knowledge/builtins n) (contains? #{"map?" "seq?" "vector?"} n)) n)))

(deftest denial-message-test
  (is (= "restore! is not available in snippet previews"
         (policy/denial-message "restore!"))))
