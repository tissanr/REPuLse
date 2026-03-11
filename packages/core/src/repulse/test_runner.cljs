(ns repulse.test-runner
  (:require [cljs.test :as test]
            [repulse.core-test]))

(defn main []
  (test/run-tests 'repulse.core-test))
