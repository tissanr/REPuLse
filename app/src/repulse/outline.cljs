(ns repulse.outline
  "Pure outline extraction over a Lezer syntax tree (SONG3).
   No DOM, no CodeMirror view: only a Lezer Tree (or SyntaxNode) and the
   document text.  The Lezer tree is partial on invalid code, so the outline
   keeps working while the buffer has syntax errors.

   Exports: extract, entry-at, definition-for, song-bars, playing-section."
  (:require [repulse.song :as song]))

(def ^:private HEADS
  {"def" :def "defn" :defn "defsynth" :defsynth "track" :track "song" :song})

(defn- node-name [^js n] (.-name n))
(defn- text-of [text ^js n] (subs text (.-from n) (.-to n)))

(defn- children
  "Direct children of a SyntaxNode as a vector."
  [^js n]
  (loop [c (.-firstChild n) acc []]
    (if c (recur (.-nextSibling ^js c) (conj acc c)) acc)))

(defn- skip-node? [^js n]
  (contains? #{"OpenParen" "CloseParen" "LineComment"} (node-name n)))

(defn- body-nodes
  "Children of a List without parens and comments."
  [^js list-node]
  (vec (remove skip-node? (children list-node))))

(defn- has-error? [^js n]
  (or (.. n -type -isError)
      (boolean (some has-error? (children n)))))

(defn- parse-number [s]
  (let [v (js/parseFloat s)]
    (when-not (js/isNaN v) v)))

(defn- section-entry [text ^js list-node]
  (let [[head nm bars tracks] (body-nodes list-node)]
    (when (and head (= "section" (text-of text head))
               nm (= "Keyword" (node-name nm)))
      (let [tracks-str (when tracks (text-of text tracks))]
        (cond-> {:kind      :section
                 :name      (text-of text nm)
                 :from      (.-from list-node)
                 :to        (.-to list-node)
                 :name-from (.-from nm)
                 :name-to   (.-to nm)
                 :children  []}
          (and bars (= "Number" (node-name bars)))
          (assoc :bars (parse-number (text-of text bars)))

          (and tracks (= "Keyword" (node-name tracks)) (= ":all" tracks-str))
          (assoc :tracks [":all"])

          (and tracks (= "Vector" (node-name tracks)))
          (assoc :tracks (vec (keep #(when (= "Keyword" (node-name %)) (text-of text %))
                                    (children tracks)))))))))

(defn- top-level-entry [text ^js list-node]
  (let [[head nm & rest-nodes] (body-nodes list-node)
        kind (when head (get HEADS (text-of text head)))]
    (when (and kind (contains? #{"BuiltinName" "Symbol"} (node-name head)))
      (if (= kind :song)
        {:kind :song :name nil
         :from (.-from list-node) :to (.-to list-node)
         :name-from nil :name-to nil
         :children (vec (keep (fn [n]
                                (when (= "List" (node-name n))
                                  (section-entry text n)))
                              (cons nm rest-nodes)))}
        (let [ok? (and nm (if (= kind :track)
                            (= "Keyword" (node-name nm))
                            (contains? #{"Symbol" "BuiltinName"} (node-name nm))))]
          (when ok?
            {:kind kind :name (text-of text nm)
             :from (.-from list-node) :to (.-to list-node)
             :name-from (.-from nm) :name-to (.-to nm)
             :children []}))))))

(defn extract
  "tree: a Lezer Tree or SyntaxNode.  Returns {:entries [...] :partial? bool}.
   Only direct children of Program are inspected."
  [^js tree text]
  (let [root (if (.-topNode tree) (.-topNode tree) tree)]
    {:entries  (vec (keep (fn [n]
                            (when (= "List" (node-name n))
                              (top-level-entry text n)))
                          (children root)))
     :partial? (has-error? root)}))

(defn entry-at
  "Innermost entry (a section before its song) with from <= pos <= to, or nil."
  [entries pos]
  (some (fn [e]
          (when (<= (:from e) pos (:to e))
            (or (entry-at (:children e) pos) e)))
        entries))

(defn definition-for
  "kind is :symbol (def/defn/defsynth) or :keyword (track).  Last match wins."
  [entries kind name]
  (let [kinds (if (= kind :keyword) #{:track} #{:def :defn :defsynth})]
    (last (filter #(and (contains? kinds (:kind %)) (= name (:name %))) entries))))

(defn song-bars
  "Sum of section :bars, or nil if any is missing."
  [song-entry]
  (let [secs (:children song-entry)]
    (when (every? :bars secs)
      (reduce + 0 (map :bars secs)))))

(defn playing-section
  "Which outline section is sounding?  song: the evaluated song value
   (SONG1: :sections [{:name :start :end}], :total, :end); bar: scheduler bar
   relative to the song origin (nil when unknown); outline-sections: the
   outline's section entries.  Returns {:index i :section-bar n :section-bars n}
   or nil.  The outline section at the same index must carry the same name
   (the text may have changed since eval)."
  [song bar outline-sections]
  (when (and song bar)
    (when-let [q (song/wrap-bar song bar)]
      (when-let [sec (song/section-at-bar song q)]
        (let [i (.indexOf (to-array (:sections song)) sec)
              o (nth outline-sections i nil)]
          (when (and o (= (:name o) (str ":" (name (:name sec)))))
            {:index i
             :section-bar  (- q (:start sec))
             :section-bars (:bars sec)}))))))
