(ns repulse.samples
  (:require [repulse.lisp.reader :as reader]
            [clojure.string :as str]))

;; Sample registry: bank-name (string) -> [full-url ...]
(def registry (atom {}))

;; Decoded buffer cache: url -> AudioBuffer
(def buffer-cache (atom {}))

;; In-flight fetch promises: url -> Promise<AudioBuffer>
(def in-flight (atom {}))

;; Active drum machine prefix — nil means no prefix active.
;; When set, keyword :kw is looked up as "<prefix>_<kw>" first.
(defonce active-bank-prefix (atom nil))

(defn set-bank-prefix!
  "Set the global drum machine prefix. Pass nil to clear."
  [prefix]
  (reset! active-bank-prefix (when prefix (name prefix)))
  (js/console.log (str "[REPuLse] bank prefix: " (or @active-bank-prefix "none"))))

;; Default sample manifests (hosted on dough-samples, Strudel's canonical source)
(def DEFAULT-MANIFESTS
  ["https://raw.githubusercontent.com/felixroos/dough-samples/main/Dirt-Samples.json"
   "https://raw.githubusercontent.com/felixroos/dough-samples/main/tidal-drum-machines.json"])

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

(defn resolve-keyword
  "Resolve a keyword against the active prefix.
   If a prefix is active and \"<prefix>_<kw>\" is a registered bank, returns that
   prefixed keyword. Otherwise returns kw unchanged."
  [kw]
  (if-let [pfx @active-bank-prefix]
    (let [candidate (keyword (str pfx "_" (name kw)))]
      (if (has-bank? candidate) candidate kw))
    kw))

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
   amp        — 0.0–1.0 gain (default 1.0)
   pan        — -1.0–1.0 stereo position (default 0.0)
   extra      — map with optional keys:
                  :rate  — playback rate multiplier (default 1.0)
                  :begin — start offset as fraction 0.0–1.0 (default 0.0)
                  :end   — end offset as fraction 0.0–1.0 (default 1.0)
                  :loop  — boolean, loop the sample (default false)
   dest       — AudioNode to connect to (default: ac.destination)"
  ([ac t bank n] (play! ac t bank n 1.0 0.0 {} nil))
  ([ac t bank n amp] (play! ac t bank n amp 0.0 {} nil))
  ([ac t bank n amp pan] (play! ac t bank n amp pan {} nil))
  ([ac t bank n amp pan extra] (play! ac t bank n amp pan extra nil))
  ([ac t bank n amp pan extra dest]
   (when-let [url (get-url bank n)]
     (-> (get-buffer! url ac)
         (.then (fn [buf]
                  (let [src     (.createBufferSource ac)
                        gain    (.createGain ac)
                        panner  (.createStereoPanner ac)
                        t'      (max t (.-currentTime ac))
                        rate-v  (or (:rate extra) 1.0)
                        begin-v (or (:begin extra) 0.0)
                        end-v   (or (:end extra) 1.0)
                        loop?   (boolean (:loop extra))
                        buf-dur (.-duration buf)
                        offset  (* begin-v buf-dur)
                        dur     (* (- end-v begin-v) buf-dur)
                        out     (or dest (.-destination ac))]
                    (set! (.-buffer src) buf)
                    (set! (.. src -playbackRate -value) (float rate-v))
                    (when loop?
                      (set! (.-loop src) true)
                      (set! (.-loopStart src) offset)
                      (set! (.-loopEnd src) (* end-v buf-dur)))
                    (.setValueAtTime (.-gain gain) (float amp) t')
                    (.setValueAtTime (.-pan panner) (float pan) t')
                    (.connect src gain)
                    (.connect gain panner)
                    (.connect panner out)
                    (if loop?
                      (.start src t' offset)
                      (.start src t' offset dur)))))
         (.catch (fn [e]
                   (js/console.debug "[REPuLse] sample play failed:" (name bank) e)))))))

;;; External sample loading — Lisp manifest, JSON manifest, GitHub discovery

(defn- unwrap-sv [x]
  (if (instance? reader/SourcedVal x) (:v x) x))

(defn- parse-lisp-manifest
  "Parse a REPuLse Lisp (.edn) manifest string into {bank-name [url ...]} map.
   Keys in the reader output are SourcedVal-wrapped keywords; values are vectors
   of SourcedVal-wrapped strings. Unwraps both layers."
  [text]
  (try
    (let [form (reader/read-one text)]
      (when (map? form)
        (let [raw-map (reduce-kv (fn [acc k v] (assoc acc (unwrap-sv k) v)) {} form)
              base    (unwrap-sv (get raw-map :_base ""))
              banks   (dissoc raw-map :_base)]
          (reduce-kv
            (fn [acc k v]
              (assoc acc (name k) (mapv #(str base (unwrap-sv %)) v)))
            {}
            banks))))
    (catch :default _ nil)))

(defn load-lisp-manifest!
  "Fetch a REPuLse Lisp (.edn) manifest, parse it, and register the banks."
  [url]
  (-> (js/fetch url)
      (.then #(.text %))
      (.then (fn [text]
               (if-let [banks (parse-lisp-manifest text)]
                 (do (swap! registry merge banks)
                     (js/console.log (str "[REPuLse] loaded " (count banks)
                                          " banks from " url)))
                 (js/console.warn "[REPuLse] Lisp manifest parse failed:" url))))
      (.catch (fn [e]
                (js/console.warn "[REPuLse] Lisp manifest load failed:" url e)))))

(def ^:private AUDIO-EXTS #{"wav" "mp3" "ogg" "flac" "aiff"})

(defn- audio-ext? [path]
  (contains? AUDIO-EXTS (str/lower-case (last (str/split path #"\.")))))

(defn load-github!
  "Discover audio files in a public GitHub repo and register them as sample banks.
   Groups by immediate parent folder; files in the repo root go under repo-name."
  [owner repo branch]
  (let [api-url  (str "https://api.github.com/repos/" owner "/" repo
                      "/git/trees/" branch "?recursive=1")
        raw-base (str "https://raw.githubusercontent.com/"
                      owner "/" repo "/" branch "/")]
    (-> (js/fetch api-url)
        (.then #(.json %))
        (.then (fn [data]
                 (let [tree    (js->clj (.-tree data) :keywordize-keys true)
                       blobs   (filter #(and (= (:type %) "blob")
                                             (audio-ext? (:path %)))
                                       tree)
                       grouped (group-by
                                 (fn [{:keys [path]}]
                                   (let [parts (str/split path #"/")]
                                     (if (> (count parts) 1)
                                       (nth parts (- (count parts) 2))
                                       repo)))
                                 blobs)
                       banks   (reduce-kv
                                 (fn [acc folder files]
                                   (assoc acc folder
                                          (mapv #(str raw-base (:path %)) files)))
                                 {}
                                 grouped)]
                   (swap! registry merge banks)
                   (js/console.log (str "[REPuLse] loaded " (count banks)
                                        " banks from github:" owner "/" repo)))))
        (.catch (fn [e]
                  (js/console.warn "[REPuLse] GitHub load failed:"
                                   (str owner "/" repo) e))))))

(defn load-external!
  "Load samples from url.
   Dispatch:
     'github:owner/repo'         — GitHub tree API, tries main then master
     'github:owner/repo/branch'  — GitHub tree API, specific branch
     'https://…/samples.edn'     — REPuLse Lisp manifest
     anything else               — Strudel-compatible JSON manifest"
  [url]
  (cond
    (str/starts-with? url "github:")
    (let [parts  (str/split (subs url 7) #"/")
          owner  (first parts)
          repo   (second parts)
          branch (nth parts 2 nil)]
      (if branch
        (load-github! owner repo branch)
        (-> (load-github! owner repo "main")
            (.catch (fn [_] (load-github! owner repo "master"))))))

    (str/ends-with? (str/lower-case url) ".edn")
    (load-lisp-manifest! url)

    :else
    (load-manifest! url)))

(defn bank-names
  "Returns a sorted list of all registered bank names."
  []
  (sort (keys @registry)))

(defn- mfr-of [name]
  ;; Leading TitleCase word = manufacturer, e.g. "Roland" from "RolandTR808_bd"
  (second (re-find #"^([A-Z][a-z]+)" name)))

(defn format-banks
  "Returns a human-readable grouped string of all registered sample banks."
  []
  (let [all (sort (keys @registry))]
    (if (empty? all)
      "No sample banks loaded yet."
      (let [simple   (filterv #(not (str/includes? % "_")) all)
            compound (filterv #(str/includes? % "_") all)
            by-mfr   (group-by #(or (mfr-of %) "misc") compound)
            mfrs     (sort (keys by-mfr))
            sections (atom [])]
        ;; General (Dirt Samples — no underscore in name)
        (when (seq simple)
          (swap! sections conj
                 (str "general (" (count simple) "):\n  "
                      (str/join "  " simple))))
        ;; Per-manufacturer
        (doseq [mfr mfrs]
          (let [banks    (sort (get by-mfr mfr))
                by-model (group-by #(first (str/split % #"_" 2)) banks)]
            (swap! sections conj
                   (str mfr " (" (count banks) "):\n"
                        (str/join "\n"
                          (map (fn [model]
                                 (let [insts (sort (map #(second (str/split % #"_" 2))
                                                        (get by-model model)))]
                                   (str "  " model ": " (str/join "  " insts))))
                               (sort (keys by-model))))))))
        (str (count all) " sample banks\n\n"
             (str/join "\n\n" @sections))))))
