(ns repulse.ui.a11y
  "Phase QA3 C/D — small keyboard-accessibility DOM helpers.
   Exports: focusable-in, trap-tab!, activate-on-key!, focus-key, restore-focus!."
  (:require [clojure.string :as str]))

(def ^:private focusable-selector
  "button, input, select, textarea, [tabindex=\"0\"]")

(defn- visible? [^js el]
  (and (not (.-disabled el))
       (not (.-hidden el))
       (pos? (.-length (.getClientRects el)))))

(defn focusable-in
  "Visible, enabled focusable elements inside container, in DOM order."
  [^js container]
  (filterv visible? (array-seq (.querySelectorAll container focusable-selector))))

(defn trap-tab!
  "Keep Tab / Shift+Tab focus inside container (wraps at both ends). Call from
   a keydown handler; does nothing for other keys."
  [^js e ^js container]
  (when (= "Tab" (.-key e))
    (let [els    (focusable-in container)
          head   (first els)
          tail   (peek els)
          shift? (.-shiftKey e)
          cur    (.-activeElement js/document)]
      (cond
        (empty? els)
        (.preventDefault e)

        (not (.contains container cur))
        (do (.preventDefault e) (.focus (if shift? tail head)))

        (and shift? (identical? cur head))
        (do (.preventDefault e) (.focus tail))

        (and (not shift?) (identical? cur tail))
        (do (.preventDefault e) (.focus head))))))

(defn activate-on-key!
  "Enter or Space on a [role=button] element inside container that is not a
   real <button> clicks it (keyboard activation of div/span controls)."
  [^js container]
  (.addEventListener container "keydown"
    (fn [^js e]
      (when (contains? #{"Enter" " "} (.-key e))
        (let [t (.-target e)]
          (when (and (instance? js/Element t)
                     (= "button" (.getAttribute t "role"))
                     (not= "BUTTON" (.-tagName t)))
            (.preventDefault e)
            (.click t)))))))

(defn- css-escape [s]
  (if (and (exists? js/CSS) (.-escape js/CSS))
    (.escape js/CSS s)
    (str/replace s #"[\"\\\\]" #(str "\\" %))))

(defn focus-key
  "A selector that finds the focused element again after container's
   innerHTML was replaced: its first class plus data-id / data-act /
   data-buffer / data-name. nil when focus is not inside container."
  [^js container]
  (let [^js el (.-activeElement js/document)]
    (when (and el container (not (identical? el container)) (.contains container el))
      (let [cls   (first (remove str/blank? (str/split (or (.getAttribute el "class") "") #"\s+")))
            attrs (keep (fn [a]
                          (when-let [v (.getAttribute el a)]
                            (str "[" a "=\"" (str/replace v #"[\"\\\\]" #(str "\\" %)) "\"]")))
                        ["data-id" "data-act" "data-buffer" "data-name"])
            sel   (str (.toLowerCase (.-tagName el))
                       (when cls (str "." (css-escape cls)))
                       (apply str attrs))]
        sel))))

(defn restore-focus!
  "Focus the element matching key (from focus-key) inside container, if any."
  [^js container key]
  (when (and container key)
    (when-let [^js el (try (.querySelector container key) (catch :default _ nil))]
      (.focus el))))
