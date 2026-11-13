(ns repulse.history-idb
  "IndexedDB adapter for the SK2 history store (hand-written, no library).
   Browser only — never required by a test namespace.
   Exports: open!"
  (:require [repulse.history :as history]))

(def DB-NAME "repulse-history")
(def DB-VERSION 1)

(defn- adapter [^js db]
  (reify history/HistoryStorage
    (-load [_ on-done]
      (try
        (let [tx     (.transaction db #js ["blobs" "meta"] "readonly")
              meta-r (.get (.objectStore tx "meta") "store")
              keys-r (.getAllKeys (.objectStore tx "blobs"))
              vals-r (.getAll (.objectStore tx "blobs"))]
          (set! (.-oncomplete tx)
                (fn [_]
                  (let [ks (array-seq (.-result keys-r))
                        vs (array-seq (.-result vals-r))]
                    (on-done {:meta  (let [m (.-result meta-r)] (when (string? m) m))
                              :blobs (zipmap ks vs)}))))
          (set! (.-onerror tx) (fn [_] (on-done nil)))
          (set! (.-onabort tx) (fn [_] (on-done nil))))
        (catch :default _ (on-done nil))))
    (-write! [_ ops]
      (when (seq ops)
        (try
          (let [tx (.transaction db #js ["blobs" "meta"] "readwrite")
                blobs (.objectStore tx "blobs")
                meta  (.objectStore tx "meta")
                warn  (fn [_] (js/console.warn "[REPuLse] Could not save history:"
                                               (some-> (.-error tx) .-name)))]
            (doseq [[op x y] ops]
              (case op
                :put-blob    (.put blobs y x)
                :put-meta    (.put meta x "store")
                :delete-blob (.delete blobs x)))
            (set! (.-onerror tx) warn)
            (set! (.-onabort tx) warn))
          (catch :default e
            (js/console.warn "[REPuLse] Could not save history:" (.-name e))))))))

(defn open!
  "Open the database; calls (on-done adapter) or (on-done nil) on any failure."
  [on-done]
  (let [called (atom false)
        done   (fn [x] (when-not @called (reset! called true) (on-done x)))]
    (try
      (if-not (exists? js/indexedDB)
        (done nil)
        (let [req (.open js/indexedDB DB-NAME DB-VERSION)]
          (set! (.-onupgradeneeded req)
                (fn [_]
                  (let [db (.-result req)]
                    (.createObjectStore db "blobs")
                    (.createObjectStore db "meta"))))
          (set! (.-onsuccess req) (fn [_] (done (adapter (.-result req)))))
          (set! (.-onerror req) (fn [_] (done nil)))
          (set! (.-onblocked req) (fn [_] (done nil)))))
      (catch :default _ (done nil)))))
