(ns repulse.api
  "Thin fetch wrapper for the REPuLse REST API.
   All calls return a JS Promise that resolves to {:data ...} or {:error ...}."
  (:require [clojure.string :as str]
            [goog.object :as gobj]
            [repulse.auth :as auth]))

(defn- auth-headers []
  (let [token (some-> (auth/session) (gobj/get "access_token"))]
    (if token
      #js {"Authorization" (str "Bearer " token)
           "Content-Type"  "application/json"}
      #js {"Content-Type" "application/json"})))

(defn- parse-response [resp]
  (-> (.json resp)
      (.then (fn [data]
               (if (.-ok resp)
                 {:data (js->clj data :keywordize-keys true)}
                 {:error (or (:error (js->clj data :keywordize-keys true))
                             (str "HTTP " (.-status resp)))})))))

(defn fetch-snippets
  "GET /api/snippets — returns Promise<{:data [...] | :error str}>.
   Options: :tag :q :sort :author :limit"
  ([] (fetch-snippets {}))
  ([{:keys [tag q sort author limit]}]
   (let [params (cond-> {}
                  tag    (assoc :tag tag)
                  q      (assoc :q q)
                  sort   (assoc :sort sort)
                  author (assoc :author author)
                  limit  (assoc :limit limit))
         qs     (when (seq params)
                  (str "?" (str/join "&"
                             (map (fn [[k v]] (str (name k) "=" (js/encodeURIComponent v)))
                                  params))))]
     (-> (js/fetch (str "/api/snippets" qs) #js {:headers (auth-headers)})
         (.then parse-response)
         (.catch (fn [e] {:error (.-message e)}))))))

(defn create-snippet!
  "POST /api/snippets — returns Promise<{:data snippet | :error str}>."
  [snippet]
  (-> (js/fetch "/api/snippets"
                #js {:method  "POST"
                     :headers (auth-headers)
                     :body    (js/JSON.stringify (clj->js snippet))})
      (.then parse-response)
      (.catch (fn [e] {:error (.-message e)}))))

(defn set-rating!
  "POST /api/snippets/:id/star with {rating: 0-5} — 0 removes the rating.
   Returns Promise<{:data {:rating int} | :error str}>."
  [snippet-id rating]
  (-> (js/fetch (str "/api/snippets/" snippet-id "/star")
                #js {:method  "POST"
                     :headers (auth-headers)
                     :body    (js/JSON.stringify #js {:rating rating})})
      (.then parse-response)
      (.catch (fn [e] {:error (.-message e)}))))

(defn track-usage!
  "POST /api/snippets/:id/use — silently increments usage counter."
  [snippet-id]
  (-> (js/fetch (str "/api/snippets/" snippet-id "/use")
                #js {:method  "POST"
                     :headers (auth-headers)})
      (.then parse-response)
      (.catch (fn [e] {:error (.-message e)}))))

(defn report-snippet!
  "POST /api/snippets/:id/report — returns Promise<{:data {:ok true} | :error str}>."
  [snippet-id reason]
  (-> (js/fetch (str "/api/snippets/" snippet-id "/report")
                #js {:method  "POST"
                     :headers (auth-headers)
                     :body    (js/JSON.stringify #js {:reason reason})})
      (.then parse-response)
      (.catch (fn [e] {:error (.-message e)}))))

(defn fetch-my-ratings!
  "GET /api/my-stars — returns Promise<{:data [{:snippet_id str :rating int}] | :error str}>.
   Requires authentication."
  []
  (-> (js/fetch "/api/my-stars" #js {:headers (auth-headers)})
      (.then parse-response)
      (.catch (fn [e] {:error (.-message e)}))))
