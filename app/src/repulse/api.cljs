(ns repulse.api
  "Thin fetch wrapper for the REPuLse REST API.
   All calls return a JS Promise that resolves to {:data ...} or {:error ...}."
  (:require [repulse.auth :as auth]))

(defn- auth-headers []
  (if-let [sess (auth/session)]
    #js {"Authorization" (str "Bearer " (.-access_token sess))
         "Content-Type"  "application/json"}
    #js {"Content-Type" "application/json"}))

(defn- parse-response [resp]
  (-> (.json resp)
      (.then (fn [data]
               (if (.-ok resp)
                 {:data (js->clj data :keywordize-keys true)}
                 {:error (or (:error (js->clj data :keywordize-keys true))
                             (str "HTTP " (.-status resp)))})))))

(defn fetch-snippets
  "GET /api/snippets — returns Promise<{:data [...] | :error str}>."
  ([] (fetch-snippets {}))
  ([{:keys [tag q limit]}]
   (let [params (cond-> {}
                  tag   (assoc :tag tag)
                  q     (assoc :q q)
                  limit (assoc :limit limit))
         qs     (when (seq params)
                  (str "?" (clojure.string/join "&"
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

(defn toggle-star!
  "POST /api/snippets/:id/star — returns Promise<{:data {:starred bool} | :error str}>."
  [snippet-id]
  (-> (js/fetch (str "/api/snippets/" snippet-id "/star")
                #js {:method  "POST"
                     :headers (auth-headers)})
      (.then parse-response)
      (.catch (fn [e] {:error (.-message e)}))))
