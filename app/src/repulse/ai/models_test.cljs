(ns repulse.ai.models-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.ai.models :as m]))

(deftest normalise-openrouter-test
  (let [[a b] (m/normalise "openrouter"
                {:data [{:id "x/a:free" :name "A" :created 5 :supported_parameters ["tools" "stop"]}
                        {:id "x/b" :supported_parameters ["stop"]}]})]
    (is (= {:id "x/a:free" :label "A" :tools? true :created 5} a))
    (is (= {:id "x/b" :label "x/b" :tools? false :created nil} b))))

(deftest normalise-mistral-test
  (let [[a b] (m/normalise "mistral"
                {:data [{:id "mistral-medium-latest" :created 9 :capabilities {:function_calling true}}
                        {:id "mistral-embed" :capabilities {:function_calling false}}
                        ]})]
    (is (true? (:tools? a)))
    (is (false? (:tools? b)))))

(deftest normalise-google-test
  (is (= [{:id "gemini-x" :label "Gemini X" :tools? nil :created nil}]
         (m/normalise "google"
           {:models [{:name "models/gemini-x" :displayName "Gemini X"
                      :supportedGenerationMethods ["generateContent"]}
                     {:name "models/embed" :supportedGenerationMethods ["embedContent"]}]}))))

(deftest normalise-groq-test
  (is (= ["a"] (map :id (m/normalise "groq" {:data [{:id "a" :active true}
                                                    {:id "b" :active false}]})))))

(deftest normalise-openai-test
  (is (= ["gpt-5"] (map :id (m/normalise "openai"
                              {:data [{:id "gpt-5"} {:id "text-embedding-3"} {:id "whisper-1"}
                                      {:id "dall-e-3"} {:id "gpt-realtime"}]})))))

(deftest normalise-anthropic-test
  (let [[a] (m/normalise "anthropic"
              {:data [{:id "claude-sonnet-5-5" :display_name "Sonnet 5.5"
                       :created_at "2026-01-01T00:00:00Z"}]})]
    (is (= "Sonnet 5.5" (:label a)))
    (is (= 1767225600 (:created a)))))

(deftest pick-default-test
  (testing "pattern order beats recency across patterns"
    (is (= "claude-sonnet-1"
           (m/pick-default "anthropic"
             [{:id "claude-opus-9" :created 99} {:id "claude-sonnet-1" :created 1}] "fb"))))
  (testing "newest within a pattern"
    (is (= "claude-sonnet-2"
           (m/pick-default "anthropic"
             [{:id "claude-sonnet-1" :created 1} {:id "claude-sonnet-2" :created 2}] "fb"))))
  (testing "tools-capable preferred over newer non-tools"
    (is (= "mistral-medium-latest"
           (m/pick-default "mistral"
             [{:id "mistral-medium-2" :created 9 :tools? false}
              {:id "mistral-medium-latest" :created 1 :tools? true}] "fb"))))
  (testing "exact match wins over prefix matches"
    (is (= "openrouter/auto"
           (m/pick-default "openrouter"
             [{:id "openrouter/auto-beta" :created 99 :tools? true}
              {:id "openrouter/auto" :created 1 :tools? true}] "fb"))))
  (testing "empty or unmatched catalogue falls back"
    (is (= "fb" (m/pick-default "anthropic" [] "fb")))
    (is (= "fb" (m/pick-default "anthropic" [{:id "other"}] "fb")))))

(deftest ttl-test
  (is (m/stale? nil))
  (is (not (m/stale? {:fetched-at 1000} 2000)))
  (is (m/stale? {:fetched-at 0} (+ m/ttl-ms 1))))

(deftest age-label-test
  (is (= "just now" (m/age-label 0 1000)))
  (is (= "3 h ago" (m/age-label 0 (* 3 3600 1000))))
  (is (= "2 d ago" (m/age-label 0 (* 2 86400 1000)))))
