(ns repulse.auth
  "GitHub OAuth via Supabase. Exports: auth-atom, supabase-client,
   init-auth!, login!, logout!, session, user-display-name."
  (:require ["@supabase/supabase-js" :refer [createClient]]))

;;; State

(defonce auth-atom (atom nil))

(defonce ^:private client-atom (atom nil))

;;; Helpers

(defn supabase-client []
  @client-atom)

(defn session []
  (:session @auth-atom))

(defn user-display-name []
  (or (get-in @auth-atom [:user :user_metadata :full_name])
      (get-in @auth-atom [:user :user_metadata :user_name])
      (get-in @auth-atom [:user :email])))

(defn avatar-url []
  (get-in @auth-atom [:user :user_metadata :avatar_url]))

;;; Auth actions

(defn login! []
  (when-let [sb @client-atom]
    (-> (js-invoke (.-auth sb) "signInWithOAuth"
                   #js {:provider "github"
                        :options  #js {:redirectTo (.-href js/location)}})
        (.catch (fn [e] (js/console.error "[REPuLse/auth] login failed:" e))))))

(defn logout! []
  (when-let [sb @client-atom]
    (-> (js-invoke (.-auth sb) "signOut")
        (.then (fn [_] (reset! auth-atom nil)))
        (.catch (fn [e] (js/console.error "[REPuLse/auth] logout failed:" e))))))

;;; Initialisation

(defn- apply-session! [session]
  (if session
    (reset! auth-atom {:session session
                       :user (js->clj (.-user session) :keywordize-keys true)})
    (reset! auth-atom nil)))

(defn init-auth!
  "Fetch Supabase credentials from /api/env, create the client, restore any
   existing session, and subscribe to future auth state changes.
   Calls on-change-fn (fn [auth-map-or-nil]) whenever auth state changes."
  [& {:keys [on-change-fn]}]
  (-> (js/fetch "/api/env")
      (.then (fn [r]
               (if (.-ok r)
                 (.json r)
                 (throw (js/Error. (str "env fetch failed: " (.-status r)))))))
      (.then (fn [env]
               (let [url (.-url env)
                     key (.-key env)]
                 (when (and url key)
                   (let [sb (createClient url key)]
                     (reset! client-atom sb)
                     ;; Restore existing session
                     (-> (js-invoke (.-auth sb) "getSession")
                         (.then (fn [result]
                                  (apply-session! (.. result -data -session))
                                  (when on-change-fn (on-change-fn @auth-atom)))))
                     ;; Subscribe to future changes
                     (js-invoke (.-auth sb) "onAuthStateChange"
                                (fn [_event session]
                                  (apply-session! session)
                                  (when on-change-fn (on-change-fn @auth-atom)))))))))
      (.catch (fn [e]
                ;; Supabase not configured — app works anonymously
                (js/console.info "[REPuLse/auth] running without backend:" (.-message e))))))
