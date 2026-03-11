(ns repulse.samples)

;; Sample registry: bank-name (string) -> [full-url ...]
(def registry (atom {}))

;; Decoded buffer cache: url -> AudioBuffer
(def buffer-cache (atom {}))

;; In-flight fetch promises: url -> Promise<AudioBuffer>
(def in-flight (atom {}))

;; Default Strudel CDN manifests
(def DEFAULT-MANIFESTS
  ["https://strudel.b-cdn.net/dirt-samples.json"
   "https://strudel.b-cdn.net/tidal-drum-machines.json"])

(defn- parse-manifest
  "Parse a manifest JS object into {bank-name -> [full-url ...]}."
  [js-data]
  (let [raw  (js->clj js-data)
        base (get raw "_base" "")]
    (reduce-kv
     (fn [acc k v]
       (if (and (not= k "_base") (vector? v) (seq v))
         (assoc acc k (mapv #(str base %) v))
         acc))
     {}
     raw)))

(defn load-manifest!
  "Fetch a JSON manifest and merge its banks into the registry."
  [url]
  (-> (js/fetch url)
      (.then #(.json %))
      (.then (fn [data]
               (let [banks (parse-manifest data)]
                 (swap! registry merge banks)
                 (js/console.log (str "[REPuLse] loaded " (count banks)
                                      " sample banks from " url)))))
      (.catch (fn [e]
                (js/console.warn (str "[REPuLse] manifest load failed: " url) e)))))

(defn init!
  "Load all default sample manifests. Call once on app startup."
  []
  (doseq [url DEFAULT-MANIFESTS]
    (load-manifest! url)))

(defn has-bank?
  "Returns true if the named bank is registered."
  [bank]
  (boolean (seq (get @registry (name bank)))))

(defn get-url
  "Get the URL for the nth sample in a bank. Wraps around if n >= count."
  [bank n]
  (when-let [urls (get @registry (name bank))]
    (when (seq urls)
      (nth urls (mod (max 0 (or n 0)) (count urls))))))

(defn get-buffer!
  "Returns a Promise<AudioBuffer> for the given URL, using cache."
  [url ac]
  (if-let [buf (get @buffer-cache url)]
    (js/Promise.resolve buf)
    (if-let [p (get @in-flight url)]
      p
      (let [p (-> (js/fetch url)
                  (.then #(.arrayBuffer %))
                  (.then #(.decodeAudioData ac %))
                  (.then (fn [buf]
                           (swap! buffer-cache assoc url buf)
                           (swap! in-flight dissoc url)
                           buf))
                  (.catch (fn [e]
                            (swap! in-flight dissoc url)
                            (js/Promise.reject e))))]
        (swap! in-flight assoc url p)
        p))))

(defn play!
  "Schedule playback of the nth sample from bank at audio time t.
   Returns a Promise. Fire-and-forget is fine."
  [ac t bank n]
  (when-let [url (get-url bank n)]
    (-> (get-buffer! url ac)
        (.then (fn [buf]
                 (let [src (.createBufferSource ac)
                       ;; If loading took longer than scheduled, play now
                       t'  (max t (.-currentTime ac))]
                   (set! (.-buffer src) buf)
                   (.connect src (.-destination ac))
                   (.start src t'))))
        (.catch (fn [e]
                  (js/console.debug "[REPuLse] sample play failed:" (name bank) e))))))

(defn bank-names
  "Returns a sorted list of all registered bank names."
  []
  (sort (keys @registry)))
