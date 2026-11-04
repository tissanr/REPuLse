(ns repulse.download
  "Browser file download shared by WAV, ZIP and MIDI exports (Phase SK1).")

(defn download-parts!
  "Offer parts (a JS array of ArrayBuffers / typed arrays) as one file download.
   The parts are handed to the Blob as they are — never concatenated first."
  [parts mime filename]
  (let [blob (js/Blob. parts #js {:type mime})
        url  (.createObjectURL js/URL blob)
        a    (.createElement js/document "a")]
    (set! (.-href a) url)
    (set! (.-download a) filename)
    (.appendChild (.-body js/document) a)
    (.click a)
    (.removeChild (.-body js/document) a)
    (js/setTimeout #(.revokeObjectURL js/URL url) 1000)))
