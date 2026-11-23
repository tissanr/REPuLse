(ns repulse.env.builtins.samples
  "Sample-management builtins: samples!, sample-banks, bank."
  (:require [repulse.audio :as audio]
            [repulse.samples :as samples]
            [repulse.lisp.eval :as leval]))

(defn make-builtins
  "No ctx dependencies."
  [_ctx]
  {"samples!"
   (fn [url]
     (let [url' (leval/unwrap url)]
       (samples/load-external! url')
       (str "loading " url' "…")))

   "sample-banks"
   (fn ([] (samples/format-banks))
       ([flt] (samples/format-banks (leval/unwrap flt))))

   "bank"
   (fn [prefix]
     (let [p (leval/unwrap prefix)]
       (samples/set-bank-prefix! p)
       ;; KIT1: a kit prefix starts fetching all of its recordings in the background.
       (when p (samples/preload-kit! (audio/get-ctx) p))
       (str "bank: " (if p (name p) "cleared"))))})
