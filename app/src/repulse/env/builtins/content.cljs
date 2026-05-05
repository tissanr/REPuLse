(ns repulse.env.builtins.content
  "Content builtins: snippet, demo, tutorial, load-gist."
  (:require [repulse.snippets :as snippets]
            [repulse.content.demos :as demos]
            [repulse.content.tutorial :as tutorial]
            [repulse.ui.editor :as editor]
            [repulse.lisp.eval :as leval]))

(defn make-builtins
  "ctx — {:set-output! f :evaluate-ref atom}"
  [{:keys [set-output! evaluate-ref]}]
  {"snippet"
   (snippets/snippet-builtin editor/editor-view evaluate-ref)

   "demo"
   (demos/demo-builtin editor/editor-view evaluate-ref)

   "tutorial"
   (tutorial/tutorial-builtin editor/editor-view)

   "load-gist"
   (fn [url]
     (let [url'    (leval/unwrap url)
           raw-url (if (re-find #"gist\.githubusercontent\.com" url')
                     url'
                     ;; Convert gist.github.com/user/id → API URL
                     (let [[_ gist-id] (re-find #"/([a-f0-9]+)/?$" url')]
                       (str "https://api.github.com/gists/" gist-id)))]
       (if (re-find #"api\.github\.com" raw-url)
         ;; API path — fetch JSON, extract first file's content
         (-> (js/fetch raw-url)
             (.then #(.json %))
             (.then (fn [data]
                      (let [files      (js->clj (.-files data))
                            first-file (second (first files))
                            content    (get first-file "content")]
                        (when-let [view @editor/editor-view]
                          (.dispatch view
                                     #js {:changes #js {:from   0
                                                        :to     (.. view -state -doc -length)
                                                        :insert content}})
                          (when-let [f @evaluate-ref] (f content))))))
             (.catch (fn [e]
                       (set-output! (str "Gist load failed: " e) :error))))
         ;; Raw URL — fetch text directly
         (-> (js/fetch raw-url)
             (.then #(.text %))
             (.then (fn [text]
                      (when-let [view @editor/editor-view]
                        (.dispatch view
                                   #js {:changes #js {:from   0
                                                      :to     (.. view -state -doc -length)
                                                      :insert text}})
                        (when-let [f @evaluate-ref] (f text)))))
             (.catch (fn [e]
                       (set-output! (str "Gist load failed: " e) :error)))))
       (str "loading gist…")))})
