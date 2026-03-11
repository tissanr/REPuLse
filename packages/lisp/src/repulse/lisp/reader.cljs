(ns repulse.lisp.reader)

;; SourcedVal: wraps a primitive literal with its source character range.
;; Primitives (keywords, numbers, strings, booleans, nil) cannot carry ClojureScript
;; metadata, so we box them in this record to thread source positions through eval.
(defrecord SourcedVal [v source])

(defn whitespace? [ch]
  (contains? #{\space \newline \tab \return \,} ch))

(defn digit? [ch]
  (boolean (re-matches #"[0-9]" (str ch))))

(defn sym-char? [ch]
  (boolean (re-matches #"[a-zA-Z0-9\-_+*/=<>!?.]" (str ch))))

(declare read-form)

(defn skip-ws-comments [{:keys [src pos]}]
  (loop []
    (let [ch (when (< @pos (count src)) (nth src @pos))]
      (cond
        (whitespace? ch)
        (do (swap! pos inc) (recur))

        (= \; ch)
        (do
          (loop []
            (let [c (when (< @pos (count src)) (nth src @pos))]
              (when (and c (not= \newline c))
                (swap! pos inc)
                (recur))))
          (recur))))))

(defn peek-char [{:keys [src pos]}]
  (when (< @pos (count src)) (nth src @pos)))

(defn advance [{:keys [src pos]}]
  (let [ch (nth src @pos)]
    (swap! pos inc)
    ch))

(defn read-string* [r]
  (advance r) ; consume "
  (loop [acc []]
    (let [ch (peek-char r)]
      (cond
        (nil? ch)  (throw (ex-info "Unterminated string" {:type :read-error}))
        (= \" ch)  (do (advance r) (apply str acc))
        (= \\ ch)  (do (advance r)
                       (let [e (advance r)]
                         (recur (conj acc (case e \n \newline \t \tab \\ \\ \" \" e)))))
        :else      (recur (conj acc (advance r)))))))

(defn read-number [r]
  (loop [acc []]
    (let [ch (peek-char r)]
      (if (and ch (or (digit? ch) (= \. ch)))
        (recur (conj acc (advance r)))
        (let [s (apply str acc)]
          (if (re-find #"\." s) (js/parseFloat s) (js/parseInt s 10)))))))

(defn read-keyword [r]
  (advance r) ; consume :
  (loop [acc []]
    (let [ch (peek-char r)]
      (if (and ch (sym-char? ch))
        (recur (conj acc (advance r)))
        (keyword (apply str acc))))))

(defn read-symbol [r]
  (loop [acc []]
    (let [ch (peek-char r)]
      (if (and ch (sym-char? ch))
        (recur (conj acc (advance r)))
        (let [s (apply str acc)]
          (cond
            (= s "true")  true
            (= s "false") false
            (= s "nil")   nil
            :else         (symbol s)))))))

(defn read-list [r]
  (advance r) ; consume (
  (loop [forms []]
    (skip-ws-comments r)
    (let [ch (peek-char r)]
      (cond
        (nil? ch) (throw (ex-info "Unterminated list" {:type :read-error}))
        (= \) ch) (do (advance r) (apply list forms))
        :else     (recur (conj forms (read-form r)))))))

(defn read-vector [r]
  (advance r) ; consume [
  (loop [forms []]
    (skip-ws-comments r)
    (let [ch (peek-char r)]
      (cond
        (nil? ch) (throw (ex-info "Unterminated vector" {:type :read-error}))
        (= \] ch) (do (advance r) forms)
        :else     (recur (conj forms (read-form r)))))))

;; read-form* is the raw reader — no source annotation.
;; It does NOT call skip-ws-comments (the outer read-form does).
(defn read-form* [r]
  (let [ch (peek-char r)]
    (cond
      (nil? ch)      ::eof
      (= \" ch)      (read-string* r)
      (= \: ch)      (read-keyword r)
      (= \( ch)      (read-list r)
      (= \[ ch)      (read-vector r)
      (digit? ch)    (read-number r)
      (= \- ch)      (do
                       (advance r)
                       (if (digit? (peek-char r))
                         (let [n (read-number r)]
                           (- n))
                         (do
                           (swap! (:pos r) dec)
                           (read-symbol r))))
      (sym-char? ch) (read-symbol r)
      :else          (throw (ex-info (str "Unexpected char: " ch) {:type :read-error})))))

;; read-form: skip whitespace, capture source range, then wrap result.
;; - Collections (list, vector) and symbols support with-meta.
;; - Primitives (keyword, number, string, boolean, nil) are wrapped in SourcedVal.
(defn read-form [r]
  (skip-ws-comments r)
  (let [from @(:pos r)
        result (read-form* r)
        to @(:pos r)]
    (if (= result ::eof)
      ::eof
      (cond
        (or (seq? result) (vector? result) (symbol? result))
        (with-meta result {:source {:from from :to to}})
        :else
        (->SourcedVal result {:from from :to to})))))

(defn read-all [src]
  (let [r {:src src :pos (atom 0)}]
    (loop [forms []]
      (skip-ws-comments r)
      (if (nil? (peek-char r))
        forms
        (let [f (read-form r)]
          (if (= f ::eof) forms (recur (conj forms f))))))))

(defn read-one [src]
  (first (read-all src)))
