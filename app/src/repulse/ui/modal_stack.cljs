(ns repulse.ui.modal-stack
  "Phase QA3 C — one stack of open modal layers (dialogs, the snippet submit
   modal). Global shortcuts and panel Escape handlers do nothing while a modal
   is open; only the topmost layer handles its keys.
   Exports: stack, push!, remove!, any-open?, top.")

(defonce stack (atom []))

(defn push!
  "Open layer id (moved to the top when already open)."
  [id]
  (swap! stack (fn [s] (conj (filterv #(not= id %) s) id))))

(defn remove!
  "Close layer id."
  [id]
  (swap! stack (fn [s] (filterv #(not= id %) s))))

(defn any-open? [] (boolean (seq @stack)))

(defn top [] (peek @stack))
