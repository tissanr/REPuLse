(ns repulse.samples
  (:require [repulse.lisp.reader :as reader]
            [repulse.specs :as specs]
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

;; External sources loaded via (samples! ...) — listed in the outline session block.
;; [{:type :github :id "owner/repo" :banks N}
;;  {:type :freesound :query "kick 808" :count N}]
(defonce loaded-sources (atom []))

(defn set-bank-prefix!
  "Set the global drum machine prefix. Pass nil to clear."
  [prefix]
  (reset! active-bank-prefix (when prefix (name prefix)))
  (js/console.log (str "[REPuLse] bank prefix: " (or @active-bank-prefix "none"))))

;; Default sample manifests.
;; - dough-samples: small curated set (casio, crow, jazz, metal, etc.)
;; - tidal-drum-machines: drum machine banks (AkaiLinn, TR-808, TR-909, …)
;; - tidalcycles/Dirt-Samples: full SuperDirt library (tabla, bd, sd, cp, …)
;;   strudel.json format: {"_base": "https://…/", "bank": ["path/file.wav", …]}
;;   Lazy loading — audio files are only fetched when a sample actually plays.
(def DEFAULT-MANIFESTS
  ["https://raw.githubusercontent.com/felixroos/dough-samples/main/Dirt-Samples.json"
   "https://raw.githubusercontent.com/felixroos/dough-samples/main/tidal-drum-machines.json"
   "https://raw.githubusercontent.com/tidalcycles/Dirt-Samples/master/strudel.json"])

(defn- parse-manifest
  "Parse a manifest JS object into {bank-name -> [full-url ...]}."
  [js-data]
  (let [raw  (js->clj js-data)
        base (get raw "_base" "")]
    (let [banks (if (map? raw)
                  (reduce-kv
                   (fn [acc k v]
                     (if (and (not= k "_base") (string? k) (vector? v) (seq v))
                       (let [urls (filterv string? (mapv #(str base %) v))]
                         (if (seq urls) (assoc acc k urls) acc))
                       acc))
                   {}
                   raw)
                  {})]
      (if (specs/sample-registry? banks)
        banks
        (do
          (js/console.warn "[REPuLse] Ignoring malformed sample manifest")
          {})))))

(defn- fetch-ok!
  "Returns a Promise that rejects with a descriptive error if response.ok is false."
  [^js resp]
  (if (.-ok resp)
    (js/Promise.resolve resp)
    (js/Promise.reject (js/Error. (str "HTTP " (.-status resp) " " (.-statusText resp))))))

(defn load-manifest!
  "Fetch a JSON manifest and merge its banks into the registry."
  [url]
  (-> (js/fetch url)
      (.then fetch-ok!)
      (.then #(.json %))
      (.then (fn [data]
               (let [banks (parse-manifest data)]
                 (swap! registry merge banks)
                 (js/console.log (str "[REPuLse] loaded " (count banks)
                                      " sample banks from " url)))))
      (.catch (fn [e]
                (js/console.warn (str "[REPuLse] manifest load failed: " url) e)))))

;; Number of manifest / repo loads still in flight, and callbacks waiting for
;; the registry to settle (see when-loaded!).
(defonce pending-loads (atom 0))
(defonce ^:private on-loaded (atom []))

(defn- track-load!
  "Count promise p as an in-flight load until it settles. Returns p."
  [p]
  (swap! pending-loads inc)
  (.finally p (fn []
                (when (zero? (swap! pending-loads dec))
                  (let [fs @on-loaded]
                    (reset! on-loaded [])
                    (doseq [f fs] (f))))))
  p)

(defn when-loaded!
  "Call f now if no sample loads are in flight, otherwise once they all settle.
   Lets callers inspect the registry without seeing a half-loaded state."
  [f]
  (if (zero? @pending-loads)
    (f)
    (swap! on-loaded conj f)))

(defn init!
  "Load all default sample manifests. Call once on app startup."
  []
  (doseq [url DEFAULT-MANIFESTS]
    (track-load! (load-manifest! url))))

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
                  (.then fetch-ok!)
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

;;; ── Phase TL6: playback planning (pure) ──────────────────────────────

(def ^:private fade-secs
  "Linear fade applied when legato/sustain or a cut group stops a voice."
  0.005)

(defn- ->num
  "Number from a number or an [n d] rational; nil otherwise."
  [x]
  (cond (number? x) x
        (and (vector? x) (= 2 (count x)) (every? number? x) (not (zero? (second x))))
        (/ (first x) (second x))
        :else nil))

(defn- clamp01 [x] (min 1.0 (max 0.0 x)))

(defn playback-plan
  "Pure. How to play a buffer of buf-dur seconds for the event params in extra:
     :rate :begin :end :loop :loop-begin :loop-end :unit :accelerate
     :legato :sustain — event params (TL6, decisions S5–S10)
     :dur :cycle-dur  — event and cycle length in seconds (S12), may be nil.
   → nil (nothing to play) or
     {:reverse? bool :rate r>0 :offset secs :content secs-or-nil
      :loop? bool :loop-start secs :loop-end secs
      :stop secs-after-start-or-nil :accel-to rate-or-nil :length secs}
   :content bounds a one-shot in buffer time (unaffected by rate); :stop is real
   time; :length estimates how long the voice sounds (##Inf for an endless loop)."
  [extra buf-dur]
  (let [n        #(->num (get extra %))
        r        (double (or (n :rate) 1))
        cd       (n :cycle-dur)
        c-unit?  (and (#{:c "c"} (:unit extra)) cd (pos? cd))
        eff      (if c-unit? (/ (* r buf-dur) cd) r)
        b        (clamp01 (double (or (n :begin) 0)))
        e        (clamp01 (double (or (n :end) 1)))
        loop?    (boolean (:loop extra))]
    (when (and (not (zero? eff)) (< b e) (pos? buf-dur))
      (let [reverse? (neg? eff)
            rate     (Math/abs eff)
            ;; forward fraction → position in the (possibly reversed) buffer
            pos      (fn [f] (* buf-dur (if reverse? (- 1 f) f)))
            offset   (if reverse? (pos e) (pos b))
            content  (* (- e b) buf-dur)
            lb       (clamp01 (double (or (n :loop-begin) b)))
            le       (clamp01 (double (or (n :loop-end) e)))
            [ls lend] (if reverse? [(pos le) (pos lb)] [(pos lb) (pos le)])
            dur      (n :dur)
            legato   (n :legato)
            sustain  (n :sustain)
            stops    (cond-> []
                       (and sustain (pos? sustain))           (conj sustain)
                       (and legato (pos? legato) dur (pos? dur)) (conj (* legato dur))
                       (and loop? dur (pos? dur) (nil? legato) (nil? sustain)) (conj dur))
            stop     (when (seq stops) (apply min stops))
            natural  (if loop? ##Inf (/ content rate))
            accel    (n :accelerate)
            accel-to (when (and accel (not (zero? accel)))
                       (max 0.01 (if reverse? (- (+ eff accel)) (+ eff accel))))]
        {:reverse?   reverse?
         :rate       rate
         :offset     offset
         :content    (when-not loop? content)
         :loop?      loop?
         :loop-start ls
         :loop-end   (if (> lend ls) lend (+ ls (/ 1 44100)))
         :stop       stop
         :accel-to   accel-to
         :length     (if stop (min stop natural) natural)}))))

(defn cut-plan
  "Pure. A new voice starting at t joins a cut group whose voices are
   [{:id :start :end} …] (end = when the voice stops sounding, absolute secs).
   → {:choke [id …] — earlier voices still sounding at t (stop them at t)
      :self-stop t2|nil — earliest later-starting voice, which cuts the new one}
   Voices starting exactly at t neither cut nor are cut (decision S11)."
  [voices t]
  {:choke     (vec (keep (fn [{:keys [id start end]}]
                           (when (and (< start t) (> end t)) id))
                         voices))
   :self-stop (some->> voices (map :start) (filter #(> % t)) seq (apply min))})

;;; ── Phase TL6: reversed buffers & cut groups (side effects) ──────────

(defonce ^:private reversed-cache (js/WeakMap.))

(defn- reversed-buffer
  "A reversed copy of buf (cached per buffer, decision S7)."
  [ac buf]
  (or (.get reversed-cache buf)
      (let [nch (.-numberOfChannels buf)
            out (.createBuffer ac nch (.-length buf) (.-sampleRate buf))]
        (dotimes [ch nch]
          (.set (.getChannelData out ch)
                (.reverse (js/Float32Array.from (.getChannelData buf ch)))))
        (.set reversed-cache buf out)
        out)))

;; AudioContext → atom {group [{:id :start :end :src :gain :amp} …]}
(defonce ^:private cut-groups (js/WeakMap.))
(defonce ^:private voice-ids (atom 0))
(def ^:private max-voices-per-group 64)

(defn- fade-out!
  "Stop a voice at time tc with a short linear fade."
  [{:keys [src gain amp]} tc]
  (let [g (.-gain gain)]
    (.cancelScheduledValues g tc)
    (.setValueAtTime g amp tc)
    (.linearRampToValueAtTime g 0 (+ tc fade-secs))
    (try (.stop src (+ tc fade-secs)) (catch :default _ nil))))

(defn- join-cut-group!
  "Register voice in group on ac; fade out the voices it cuts and, if a later
   voice of the group is already scheduled, fade itself out at that time."
  [ac group voice]
  (let [groups (or (.get cut-groups ac)
                   (let [a (atom {})] (.set cut-groups ac a) a))
        now    (.-currentTime ac)
        live   (filterv #(> (:end %) now) (get @groups group []))
        t      (:start voice)
        {:keys [choke self-stop]} (cut-plan live t)
        choked (set choke)
        live'  (mapv (fn [v] (if (choked (:id v))
                               (do (fade-out! v t) (assoc v :end (+ t fade-secs)))
                               v))
                     live)
        voice' (if self-stop
                 (do (fade-out! voice self-stop)
                     (update voice :end min (+ self-stop fade-secs)))
                 voice)]
    (swap! groups assoc group
           (vec (take-last max-voices-per-group (conj live' voice'))))))

(defn play!
  "Schedule playback of the nth sample from bank at audio time t.
   amp        — 0.0–1.0 gain (default 1.0)
   pan        — -1.0–1.0 stereo position (default 0.0)
   extra      — map of event params (see playback-plan): :rate :begin :end :loop
                :loop-begin :loop-end :unit :accelerate :legato :sustain :cut,
                plus timing :dur / :cycle-dur in seconds when known.
   dest       — AudioNode to connect to (default: ac.destination)
   Returns the Promise of the buffer fetch (offline export waits for it).

   NOTE: envelope parameters (attack, decay, release) are NOT applied to samples.
   Shorten a sample with :end, :legato or :sustain."
  ([ac t bank n] (play! ac t bank n 1.0 0.0 {} nil))
  ([ac t bank n amp] (play! ac t bank n amp 0.0 {} nil))
  ([ac t bank n amp pan] (play! ac t bank n amp pan {} nil))
  ([ac t bank n amp pan extra] (play! ac t bank n amp pan extra nil))
  ([ac t bank n amp pan extra dest]
   (when-let [url (get-url bank n)]
     (-> (get-buffer! url ac)
         (.then (fn [buf]
                  (when-let [plan (playback-plan extra (.-duration buf))]
                    (let [{:keys [reverse? rate offset content loop? loop-start loop-end
                                  stop accel-to length]} plan
                          src    (.createBufferSource ac)
                          gain   (.createGain ac)
                          panner (.createStereoPanner ac)
                          t'     (max t (.-currentTime ac))
                          amp    (float amp)
                          out    (or dest (.-destination ac))
                          pr     (.-playbackRate src)]
                      (set! (.-buffer src) (if reverse? (reversed-buffer ac buf) buf))
                      (.setValueAtTime pr rate t')
                      (when accel-to
                        (.linearRampToValueAtTime pr accel-to
                                                  (+ t' (if (js/isFinite length) length
                                                            (/ (or content 1) rate)))))
                      (when loop?
                        (set! (.-loop src) true)
                        (set! (.-loopStart src) loop-start)
                        (set! (.-loopEnd src) loop-end))
                      (.setValueAtTime (.-gain gain) amp t')
                      (.setValueAtTime (.-pan panner) (float pan) t')
                      (.connect src gain)
                      (.connect gain panner)
                      (.connect panner out)
                      (if content
                        (.start src t' offset content)
                        (.start src t' offset))
                      (when stop
                        (let [g      (.-gain gain)
                              t-stop (+ t' stop)]
                          (.setValueAtTime g amp (max t' (- t-stop fade-secs)))
                          (.linearRampToValueAtTime g 0 t-stop)
                          (.stop src t-stop)))
                      (when-some [group (:cut extra)]
                        (join-cut-group! ac group
                                         {:id    (swap! voice-ids inc)
                                          :start t'
                                          :end   (+ t' length)
                                          :src   src :gain gain :amp amp}))))))
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
          (let [parsed (reduce-kv
                        (fn [acc k v]
                          (let [urls (when (vector? v)
                                       (filterv string? (mapv #(str base (unwrap-sv %)) v)))]
                            (if (and (seq urls) (or (keyword? k) (string? k)))
                              (assoc acc (name k) urls)
                              acc)))
                        {}
                        banks)]
            (when (specs/sample-registry? parsed)
              parsed)))))
    (catch :default _ nil)))

(defn load-lisp-manifest!
  "Fetch a REPuLse Lisp (.edn) manifest, parse it, and register the banks."
  [url]
  (-> (js/fetch url)
      (.then fetch-ok!)
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
   Groups by immediate parent folder; files in the repo root go under repo-name.
   Returns a Promise that rejects when the branch's tree cannot be fetched, so
   callers can fall back to another branch."
  [owner repo branch]
  (let [api-url  (str "https://api.github.com/repos/" owner "/" repo
                      "/git/trees/" branch "?recursive=1")
        raw-base (str "https://raw.githubusercontent.com/"
                      owner "/" repo "/" branch "/")]
    (-> (js/fetch api-url)
        (.then fetch-ok!)
        (.then #(.json %))
        (.then (fn [^js data]
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
                   (let [source {:type :github :id (str owner "/" repo) :banks (count banks)}]
                     (when (specs/loaded-source? source)
                       (swap! loaded-sources conj source)))
                   (js/console.log (str "[REPuLse] loaded " (count banks)
                                        " banks from github:" owner "/" repo))))))))

(defn load-external!
  "Load samples from url.
   Dispatch:
     'github:owner/repo'         — GitHub tree API, tries main then master
     'github:owner/repo/branch'  — GitHub tree API, specific branch
     'https://…/samples.edn'     — REPuLse Lisp manifest
     anything else               — Strudel-compatible JSON manifest"
  [url]
  (track-load!
   (cond
     (str/starts-with? url "github:")
     (let [parts  (str/split (subs url 7) #"/")
           owner  (first parts)
           repo   (second parts)
           branch (nth parts 2 nil)]
       (-> (if branch
             (load-github! owner repo branch)
             (-> (load-github! owner repo "main")
                 (.catch (fn [_] (load-github! owner repo "master")))))
           (.catch (fn [e]
                     (js/console.warn "[REPuLse] GitHub load failed:"
                                      (str owner "/" repo) e)))))

     (str/ends-with? (str/lower-case url) ".edn")
     (load-lisp-manifest! url)

     :else
     (load-manifest! url))))

(defn register-url!
  "Register a single audio URL as a one-sample bank under bank-name (string or keyword)."
  [bank-name url]
  (let [bank (name bank-name)
        entry {bank [url]}]
    (if (specs/sample-registry? entry)
      (swap! registry assoc bank [url])
      (js/console.warn "[REPuLse] Ignoring invalid sample URL registration:" bank-name url))))

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
