(ns repulse.test-runner
  (:require [cljs.test :as test]
            [repulse.core-test]
            [repulse.theory-test]
            [repulse.params-test]
            [repulse.lisp.eval-test]))

(defn main []
  (test/run-tests 'repulse.core-test 'repulse.theory-test 'repulse.params-test
                  'repulse.lisp.eval-test))
