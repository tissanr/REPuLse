(ns repulse.zip-test
  "Phase SK1 (K8) — STORE-only zip writer: CRC-32 and exact byte vectors."
  (:require [clojure.string :as str]
            [cljs.test :refer [deftest is testing]]
            [repulse.zip :as zip]))

(defn- utf8 [s] (.encode (js/TextEncoder.) s))

(defn- hex [bytes]
  (str/join " " (map #(.toUpperCase (.padStart (.toString % 16) 2 "0"))
                     (array-seq bytes))))

(deftest crc32-test
  (is (= 0xCBF43926 (zip/crc32 (utf8 "123456789"))))
  (is (= 0 (zip/crc32 (utf8 ""))))
  (is (= 0xD8932AAC (zip/crc32 (utf8 "hi")))))

(def ^:private a-txt-hex
  "50 4B 03 04 14 00 00 08 00 00 00 00 21 00 AC 2A 93 D8 02 00 00 00 02 00 00 00 05 00 00 00 61 2E 74 78 74 68 69 50 4B 01 02 14 00 14 00 00 08 00 00 00 00 21 00 AC 2A 93 D8 02 00 00 00 02 00 00 00 05 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 61 2E 74 78 74 50 4B 05 06 00 00 00 00 01 00 01 00 33 00 00 00 25 00 00 00 00 00")

(deftest zip-bytes-test
  (testing "one entry — the exact 110 bytes verified with Python zipfile / unzip -t"
    (let [bs (zip/zip-bytes [{:name "a.txt" :data (utf8 "hi")}])]
      (is (= 110 (.-length bs)))
      (is (= a-txt-hex (hex bs)))))
  (testing "two 10-byte entries → 258 bytes; size formula agrees"
    (let [es [{:name "repulse-kick.wav" :data (js/Uint8Array. 10)}
              {:name "repulse-bass.wav" :data (js/Uint8Array. 10)}]]
      (is (= 258 (.-length (zip/zip-bytes es))))
      (is (= 258 (zip/zip-size [{:name "repulse-kick.wav" :size 10}
                                {:name "repulse-bass.wav" :size 10}])))))
  (testing "parts: header + data per entry, then central directory + end record"
    (let [data  (utf8 "hi")
          parts (zip/zip-parts [{:name "a.txt" :data data}])]
      (is (= 4 (.-length parts)) "local header, data, central entry, end record")
      (is (identical? data (aget parts 1)) "data is passed through, not copied")))
  (testing "UTF-8 names, empty archive"
    (is (= 116 (zip/zip-size [{:name "ä-ü.wav" :size 0}])))
    (is (= 22 (.-length (zip/zip-bytes []))))))

(deftest zip-size-guard-test
  (testing "K8: one 5-byte name adds 108 bytes of structure; the guard trips above 0xFFFFFFFF"
    (let [at-limit (- zip/max-zip-bytes 108)]
      (is (= zip/max-zip-bytes (zip/zip-size [{:name "a.wav" :size at-limit}])))
      (is (not (> (zip/zip-size [{:name "a.wav" :size at-limit}]) zip/max-zip-bytes)))
      (is (> (zip/zip-size [{:name "a.wav" :size (inc at-limit)}]) zip/max-zip-bytes)))))
