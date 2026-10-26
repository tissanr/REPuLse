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
            [repulse.samples-test]
            [repulse.sound-check-test]
            [repulse.ui.timeline-test]
            [repulse.ai.safety-test]
            [repulse.ai.edit-test]
            [repulse.ai.client-test]
            [repulse.ai.models-test]
            [repulse.ai.knowledge-test]
            [repulse.audio-clock-test]))

;; Without this the process exits 0 even when tests fail, so `npm test` (and CI)
;; would pass regardless of the result.
(defmethod test/report [::test/default :end-run-tests] [m]
  (when-not (test/successful? m)
    (set! (.-exitCode js/process) 1)))

(defn main []
  (test/run-tests 'repulse.core-test 'repulse.theory-test 'repulse.params-test
                  'repulse.envelope-test
                  'repulse.lisp.eval-test 'repulse.lisp.mini-test
                  'repulse.lisp.integration-test
                  'repulse.fx-test 'repulse.session-test
                  'repulse.samples-test
                  'repulse.sound-check-test
                  'repulse.ui.timeline-test
                  'repulse.ai.safety-test
                  'repulse.ai.edit-test
                  'repulse.ai.client-test
                  'repulse.ai.models-test
                  'repulse.ai.knowledge-test
                  'repulse.audio-clock-test))
