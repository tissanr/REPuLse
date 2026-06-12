(ns repulse.ui.html
  "Shared HTML-escaping helper for UI namespaces that build innerHTML strings.
   Escapes single quotes too, so values are safe in single-quoted attribute
   and JS-string contexts as well as element content."
  (:require [clojure.string :as str]))

(defn escape-html [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'" "&#39;")))
