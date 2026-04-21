(ns repulse.ui.auth-button
  "Login / logout button rendered into the header.
   Reads from repulse.auth/auth-atom; calls login!/logout! on click."
  (:require [repulse.auth :as auth]))

(defn- el [id] (.getElementById js/document id))

(defn render-auth-btn!
  "Update the #auth-btn element to reflect current auth state."
  []
  (when-let [btn (el "auth-btn")]
    (if-let [name (auth/user-display-name)]
      (let [avatar (auth/avatar-url)]
        (set! (.-innerHTML btn)
              (if avatar
                (str "<img class=\"auth-avatar\" src=\"" avatar "\" alt=\"\" />"
                     "<span class=\"auth-name\">" name "</span>")
                (str "<span class=\"auth-name\">" name "</span>")))
        (set! (.-title btn) "Sign out")
        (set! (.-dataset.action btn) "logout"))
      (do
        (set! (.-textContent btn) "sign in")
        (set! (.-title btn) "Sign in with GitHub")
        (set! (.-dataset.action btn) "login")))))

(defn init!
  "Inject the #auth-btn into the header controls and wire click handler."
  []
  (when-let [controls (.querySelector js/document ".header-controls")]
    ;; Insert before the play button
    (let [btn (js/document.createElement "button")]
      (set! (.-id btn) "auth-btn")
      (set! (.-className btn) "auth-btn")
      (set! (.-textContent btn) "sign in")
      (.addEventListener btn "click"
        (fn []
          (if (= "logout" (.. btn -dataset -action))
            (auth/logout!)
            (auth/login!))))
      (let [play-btn (el "play-btn")]
        (if play-btn
          (.insertBefore controls btn play-btn)
          (.appendChild controls btn)))))
  (render-auth-btn!))
