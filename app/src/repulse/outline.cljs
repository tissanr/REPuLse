(ns repulse.outline
  "Pure outline extraction over a Lezer syntax tree (SONG3).
   No DOM, no CodeMirror view: only a Lezer Tree (or SyntaxNode) and the
   document text.  The Lezer tree is partial on invalid code, so the outline
   keeps working while the buffer has syntax errors.

   Phase WS1: top-level (fx :name …) forms are entries of kind :fx, and every
   top-level entry carries :refs (Symbol/BuiltinName texts inside it), which
   used-by searches.

   Phase WS2: a top-level (master (fx …) …) form is an entry of kind :master
   (no name) whose :children are its (fx …) entries (kind :fx).

   Exports: extract, entry-at, definition-for, project-definition, song-bars,
            playing-section, used-by."
  (:require [repulse.song :as song]))

(def ^:private HEADS
  {"def" :def "defn" :defn "defsynth" :defsynth "track" :track "song" :song
   "fx" :fx "master" :master})

;; Kinds whose name token must be a Keyword (the others take a Symbol).
(def ^:private KEYWORD-NAMED #{:track :fx})

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

(defn- collect-refs
  "Set of Symbol/BuiltinName texts anywhere under `n`, skipping the nodes
   starting at the positions in `skip` (the head and the name token)."
  [text ^js n skip]
  (letfn [(walk [acc ^js c]
            (cond
              (contains? skip (.-from c)) acc
              (contains? #{"Symbol" "BuiltinName"} (node-name c)) (conj acc (text-of text c))
              :else (reduce walk acc (children c))))]
    (reduce walk #{} (children n))))

(defn- top-level-entry [text ^js list-node]
  (let [[head nm & rest-nodes] (body-nodes list-node)
        kind (when head (get HEADS (text-of text head)))]
    (when (and kind (contains? #{"BuiltinName" "Symbol"} (node-name head)))
      (case kind
        :master
        {:kind :master :name nil
         :from (.-from list-node) :to (.-to list-node)
         :name-from nil :name-to nil
         :children (vec (keep (fn [n]
                                (when (= "List" (node-name n))
                                  (let [e (top-level-entry text n)]
                                    (when (= :fx (:kind e)) e))))
                              (when nm (cons nm rest-nodes))))}
        :song
        {:kind :song :name nil
         :from (.-from list-node) :to (.-to list-node)
         :name-from nil :name-to nil
         :children (vec (keep (fn [n]
                                (when (= "List" (node-name n))
                                  (section-entry text n)))
                              (cons nm rest-nodes)))}
        (let [ok? (and nm (if (contains? KEYWORD-NAMED kind)
                            (= "Keyword" (node-name nm))
                            (contains? #{"Symbol" "BuiltinName"} (node-name nm))))]
          (when ok?
            {:kind kind :name (text-of text nm)
             :from (.-from list-node) :to (.-to list-node)
             :name-from (.-from nm) :name-to (.-to nm)
             :refs (collect-refs text list-node #{(.-from head) (.-from nm)})
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

(defn project-definition
  "Cross-buffer lookup (Phase SONG4). others = [[buffer-name entries] …] in tab
   order, without the active buffer. → {:buffer b :entry e} for the first buffer
   whose definition-for matches (its last match), or nil."
  [others kind name]
  (some (fn [[b entries]]
          (when-let [e (definition-for entries kind name)]
            {:buffer b :entry e}))
        others))

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

(defn used-by
  "Names of the entries (any kind but :song) whose :refs contain `name`, in
   source order, without the entry called `name` itself — Phase WS1."
  [entries name]
  (vec (keep (fn [e]
               (when (and (not= :song (:kind e))
                          (not= name (:name e))
                          (contains? (:refs e) name))
                 (:name e)))
             entries)))
