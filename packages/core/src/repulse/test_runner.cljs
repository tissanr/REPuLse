(ns repulse.test-runner
  (:require [cljs.test :as test]
            [repulse.core-test]
            [repulse.theory-test]
            [repulse.params-test]
            [repulse.envelope-test]
            [repulse.lisp.eval-test]
            [repulse.lisp.mini-test]
            [repulse.lisp.integration-test]
            [repulse.fx-test]
            [repulse.session-test]
            [repulse.ui.timeline-test]))

(defn main []
  (test/run-tests 'repulse.core-test 'repulse.theory-test 'repulse.params-test
                  'repulse.envelope-test
                  'repulse.lisp.eval-test 'repulse.lisp.mini-test
                  'repulse.lisp.integration-test
                  'repulse.fx-test 'repulse.session-test
                  'repulse.ui.timeline-test))
