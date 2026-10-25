(ns repulse.samples-test
  (:require [cljs.test :refer [deftest is async]]
            [clojure.string :as str]
            [repulse.samples :as samples]))

(defn- fake-response [status body]
  #js {:ok         (= 200 status)
       :status     status
       :statusText (if (= 200 status) "OK" "Not Found")
       :json       (fn [] (js/Promise.resolve (clj->js body)))})

(defn- with-fake-fetch
  "Replace js/fetch with handler (url → [status body]) while (f done') runs;
   restores the original fetch and registry before calling done."
  [done handler f]
  (let [orig-fetch    (.-fetch js/globalThis)
        orig-registry @samples/registry
        orig-sources  @samples/loaded-sources
        requested     (atom [])]
    (set! (.-fetch js/globalThis)
          (fn [url]
            (swap! requested conj url)
            (let [[status body] (handler url)]
              (js/Promise.resolve (fake-response status body)))))
    (f requested
       (fn []
         (set! (.-fetch js/globalThis) orig-fetch)
         (reset! samples/registry orig-registry)
         (reset! samples/loaded-sources orig-sources)
         (done)))))

(def ^:private tree
  {:tree [{:type "blob" :path "drums/kick.wav"}]})

(deftest github-falls-back-to-master
  (async done
    (with-fake-fetch done
      (fn [url] (if (str/includes? url "/trees/master") [200 tree] [404 {}]))
      (fn [requested finish]
        (-> (samples/load-external! "github:someone/kit")
            (.then (fn []
                     (is (= 2 (count @requested)) "tries main, then master")
                     (is (= ["https://raw.githubusercontent.com/someone/kit/master/drums/kick.wav"]
                            (get @samples/registry "drums")))
                     (finish))))))))

(deftest github-explicit-branch-does-not-fall-back
  (async done
    (with-fake-fetch done
      (fn [_] [404 {}])
      (fn [requested finish]
        (-> (samples/load-external! "github:someone/kit/dev")
            (.then (fn []
                     (is (= 1 (count @requested)))
                     (is (nil? (get @samples/registry "drums")))
                     (finish))))))))
