(ns repulse.env.builtins.samples
  "Sample-management builtins: samples!, sample-banks, bank."
  (:require [repulse.samples :as samples]
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
   (fn [] (samples/format-banks))

   "bank"
   (fn [prefix]
     (samples/set-bank-prefix! (leval/unwrap prefix))
     (str "bank: " (if prefix (name (leval/unwrap prefix)) "cleared")))})
