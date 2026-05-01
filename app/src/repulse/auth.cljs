(ns repulse.auth
  "GitHub OAuth via Supabase. Exports: auth-atom, supabase-client,
   init-auth!, login!, logout!, session, user-display-name."
  (:require ["@supabase/supabase-js" :refer [createClient]]
            [goog.object :as gobj]))

;;; State

(defonce auth-atom (atom nil))

(defonce ^:private client-atom (atom nil))
(defonce ^:private site-url-atom (atom nil))  ; set from /api/env on init

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

(defn user-id []
  (get-in @auth-atom [:user :id]))

;;; Auth actions

(defn login! []
  (when-let [sb @client-atom]
    ;; Use the deployment-specific siteUrl when available (covers Vercel preview
    ;; deployments where location.origin may not be in Supabase's redirect allow-list).
    ;; Falls back to current page URL when running locally or if siteUrl wasn't set.
    (let [redirect-to (or @site-url-atom (.-href js/location))]
      (-> (js-invoke (.-auth sb) "signInWithOAuth"
                     #js {:provider "github"
                          :options  #js {:redirectTo redirect-to}})
          (.catch (fn [e] (js/console.error "[REPuLse/auth] login failed:" e)))))))

(defn logout! []
  (when-let [sb @client-atom]
    (-> (js-invoke (.-auth sb) "signOut")
        (.then (fn [_] (reset! auth-atom nil)))
        (.catch (fn [e] (js/console.error "[REPuLse/auth] logout failed:" e))))))

;;; Initialisation

(defn- apply-session! [session]
  (if session
    (reset! auth-atom {:session session
                       :user (js->clj (gobj/get session "user") :keywordize-keys true)})
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
      (.then (fn [^js env]
               (let [url      (gobj/get env "url")
                     key      (gobj/get env "key")
                     site-url (gobj/get env "siteUrl")]
                 (when site-url (reset! site-url-atom site-url))
                 (when (and url key)
                   (let [sb (createClient url key)]
                     (reset! client-atom sb)
                     ;; Restore existing session
                     (-> (js-invoke (.-auth sb) "getSession")
                         (.then (fn [result]
                                  (apply-session! (some-> result
                                                          (gobj/get "data")
                                                          (gobj/get "session")))
                                  (when on-change-fn (on-change-fn @auth-atom)))))
                     ;; Subscribe to future changes
                     (js-invoke (.-auth sb) "onAuthStateChange"
                                (fn [_event session]
                                  (apply-session! session)
                                  (when on-change-fn (on-change-fn @auth-atom)))))))))
      (.catch (fn [e]
                ;; Supabase not configured — app works anonymously
                (js/console.info "[REPuLse/auth] running without backend:" (.-message e))))))
