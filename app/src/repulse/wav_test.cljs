(ns repulse.wav-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [repulse.wav :as wav]))

(defn- ascii [dv off n]
  (apply str (map #(char (.getUint8 dv (+ off %))) (range n))))

(deftest encode-wav-16-header
  (let [l   (js/Float32Array. #js [0.5 -1.0 2.0])
        r   (js/Float32Array. #js [-0.5 0.25 -3.0])
        buf (wav/encode-wav-16 l r 44100)
        dv  (js/DataView. buf)]
    (testing "44-byte RIFF/WAVE header for 16-bit stereo PCM"
      (is (= (+ 44 (* 3 4)) (.-byteLength buf)))
      (is (= "RIFF" (ascii dv 0 4)))
      (is (= (+ 36 12) (.getUint32 dv 4 true)))
      (is (= "WAVE" (ascii dv 8 4)))
      (is (= "fmt " (ascii dv 12 4)))
      (is (= 16 (.getUint32 dv 16 true)))
      (is (= 1 (.getUint16 dv 20 true)))
      (is (= 2 (.getUint16 dv 22 true)))
      (is (= 44100 (.getUint32 dv 24 true)))
      (is (= (* 44100 4) (.getUint32 dv 28 true)))
      (is (= 4 (.getUint16 dv 32 true)))
      (is (= 16 (.getUint16 dv 34 true)))
      (is (= "data" (ascii dv 36 4)))
      (is (= 12 (.getUint32 dv 40 true))))
    (testing "interleaved samples, scaled by 32767, truncated and clamped"
      (is (= 16383 (.getInt16 dv 44 true)))
      (is (= -16383 (.getInt16 dv 46 true)))
      (is (= -32767 (.getInt16 dv 48 true)))
      (is (= 8191 (.getInt16 dv 50 true)))
      (is (= 32767 (.getInt16 dv 52 true)))
      (is (= -32767 (.getInt16 dv 54 true))))
    (testing "sample rate is written as given (song export uses the context rate)"
      (is (= 48000 (.getUint32 (js/DataView. (wav/encode-wav-16 l r 48000)) 24 true))))))
