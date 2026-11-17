(ns repulse.zip
  "Minimal STORE-only (uncompressed) zip writer (Phase SK1, K8).
   Local headers + data, central directory, end record; no extra fields,
   comments or ZIP64; fixed DOS time/date (1980-01-01 00:00) so archives are
   byte-reproducible. Pure — no DOM.")

(def max-zip-bytes 0xFFFFFFFF)

(def ^:private crc-table
  (let [t (js/Uint32Array. 256)]
    (dotimes [n 256]
      (loop [c n k 0]
        (if (< k 8)
          (recur (if (odd? c)
                   (bit-xor 0xEDB88320 (unsigned-bit-shift-right c 1))
                   (unsigned-bit-shift-right c 1))
                 (inc k))
          (aset t n (unsigned-bit-shift-right c 0)))))
    t))

(defn crc32
  "CRC-32 (poly 0xEDB88320) of a Uint8Array → unsigned integer."
  [^js bytes]
  (let [n (.-length bytes)]
    (loop [i 0 c 0xFFFFFFFF]
      (if (< i n)
        (recur (inc i)
               (bit-xor (aget crc-table (bit-and (bit-xor c (aget bytes i)) 0xFF))
                        (unsigned-bit-shift-right c 8)))
        (unsigned-bit-shift-right (bit-xor c 0xFFFFFFFF) 0)))))

(defn- name-bytes [s] (.encode (js/TextEncoder.) s))

(defn zip-size
  "entries [{:name str :size n}] → total archive bytes."
  [entries]
  (+ 22
     (reduce + 0
             (for [{:keys [name size]} entries
                   :let [nl (.-length (name-bytes name))]]
               (+ 30 nl size 46 nl)))))

(defn- local-header [^js nm size crc]
  (let [buf (js/Uint8Array. (+ 30 (.-length nm)))
        dv  (js/DataView. (.-buffer buf))]
    (.setUint32 dv 0 0x04034B50 true)
    (.setUint16 dv 4 20 true)
    (.setUint16 dv 6 0x0800 true)
    (.setUint16 dv 8 0 true)
    (.setUint16 dv 10 0 true)
    (.setUint16 dv 12 0x0021 true)
    (.setUint32 dv 14 crc true)
    (.setUint32 dv 18 size true)
    (.setUint32 dv 22 size true)
    (.setUint16 dv 26 (.-length nm) true)
    (.setUint16 dv 28 0 true)
    (.set buf nm 30)
    buf))

(defn- central-entry [^js nm size crc offset]
  (let [buf (js/Uint8Array. (+ 46 (.-length nm)))
        dv  (js/DataView. (.-buffer buf))]
    (.setUint32 dv 0 0x02014B50 true)
    (.setUint16 dv 4 20 true)
    (.setUint16 dv 6 20 true)
    (.setUint16 dv 8 0x0800 true)
    (.setUint16 dv 10 0 true)
    (.setUint16 dv 12 0 true)
    (.setUint16 dv 14 0x0021 true)
    (.setUint32 dv 16 crc true)
    (.setUint32 dv 20 size true)
    (.setUint32 dv 24 size true)
    (.setUint16 dv 28 (.-length nm) true)
    (.setUint32 dv 42 offset true)
    (.set buf nm 46)
    buf))

(defn- end-record [n cd-size cd-offset]
  (let [buf (js/Uint8Array. 22)
        dv  (js/DataView. (.-buffer buf))]
    (.setUint32 dv 0 0x06054B50 true)
    (.setUint16 dv 8 n true)
    (.setUint16 dv 10 n true)
    (.setUint32 dv 12 cd-size true)
    (.setUint32 dv 16 cd-offset true)
    buf))

(defn zip-parts
  "entries [{:name str :data Uint8Array}] → JS array of Uint8Array parts: local
   header + data per entry, then the central directory and the end record.
   The data arrays are passed through, not copied."
  [entries]
  (let [parts   (array)
        central (array)]
    (loop [es (seq entries) offset 0]
      (if es
        (let [{:keys [name data]} (first es)
              nm   (name-bytes name)
              size (.-length ^js data)
              crc  (crc32 data)
              lh   (local-header nm size crc)]
          (.push parts lh)
          (.push parts data)
          (.push central (central-entry nm size crc offset))
          (recur (next es) (+ offset (.-length lh) size)))
        (let [cd-size (reduce + 0 (map #(.-length ^js %) central))]
          (doseq [c central] (.push parts c))
          (.push parts (end-record (count entries) cd-size offset)))))
    parts))

(defn zip-bytes
  "zip-parts concatenated into one Uint8Array (tests)."
  [entries]
  (let [parts (zip-parts entries)
        total (reduce + 0 (map #(.-length ^js %) parts))
        out   (js/Uint8Array. total)]
    (loop [i 0 off 0]
      (when (< i (.-length parts))
        (let [p (aget parts i)]
          (.set out p off)
          (recur (inc i) (+ off (.-length ^js p))))))
    out))
