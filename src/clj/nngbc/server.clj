(ns nngbc.server
    (:import (com.gargoylesoftware.htmlunit WebClient BrowserVersion WebWindowListener))
    (:require [clojure.java.io :as io]
      [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
      [compojure.route :refer [resources]]
      [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
      [ring.middleware.gzip :refer [wrap-gzip]]
      [ring.middleware.logger :refer [wrap-with-logger]]
      [environ.core :refer [env]]
      [ring.adapter.jetty :refer [run-jetty]]
      ;   [reaver :refer [parse extract-from text attr]]
      [clojure.spec :as s]
      [clojure.spec.test :as ts]
      [clojure.spec.gen :as gen]
      [cemerick.url :refer (url url-encode)])
    (:gen-class))

;Value gens ==============================================================================

(defn html-division-gen []
        (->
          (new WebClient BrowserVersion/FIREFOX_38)
          (.getPage "http://www.google.com")
          (.getBody)
          (.getByXPath "//div[@class=\"ctr-p\"]")
          first))

;=========================================================================================

;Types ===================================================================================

(s/def ::bonus-url-string
  (s/with-gen
    #(not (nil? (:query (url %))))
    (fn [] #{"https://apps.facebook.com/farmville-two/viral.php?viralId=be012f04287d1360de69368f70369268_33671037398"})))

(s/def ::window
  (s/with-gen
    #(instance? com.gargoylesoftware.htmlunit.TopLevelWindow %)
    (fn [] #{(-> (new WebClient) (.getTopLevelWindows))})))

(s/def ::html-division-coll
  (s/with-gen
    (s/coll-of #(instance? com.gargoylesoftware.htmlunit.html.HtmlDivision %))
    (fn [] #{[   ]})
    )

  )

;=========================================================================================

;(.addWebWindowListener web-client listen)
(def window-listener
  (reify
    WebWindowListener

    (webWindowClosed [this event]
                     (println "web window closed: " event))

    (webWindowContentChanged [this event]
                             (println "web window content changed: " event))

    (webWindowOpened [this event]

                     (println "web window opened: " event))))

(s/fdef parse-raw-url
        :args ::bonus-url-string
        :ret ::bonus-url-string)

(defn parse-raw-url [url-string]
      (->
        url-string
        url
        :query
        (get "next")
        url
        :query
        (get "redirect_uri")))

(s/fdef window->raw-url
        :args ::window
        :ret ::bonus-url-string)

(defn window->raw-url [window]
      (.toString (.getBaseURL (.getEnclosedPage window))))

(s/fdef get-gameskip-bonuses
        :ret (s/coll-of #(instance? com.gargoylesoftware.htmlunit.html.HtmlDivision %)))

(defn get-gameskip-bonuses []
      (->
        (new WebClient BrowserVersion/FIREFOX_38)
        (.getPage "https://gameskip.com/farmville-2-links/non-friend-bonus.html")
        (.getBody)
        (.getByXPath "//div[@class=\"title box\"]")
        (as-> x (into [] x))))

(comment




  (as-> x (into [] x))

  (def html-division (first (get-gameskip-bonuses)))

  (type html-division)

  (read-string (pr-str html-division))

  (map
    (get-gameskip-bonuses))

  (.getTopLevelWindows web-client)

  (-> (nth bonuses 2) (.click))

  (type (last (.getTopLevelWindows web-client)))

  (instance? com.gargoylesoftware.htmlunit.TopLevelWindow (last (.getTopLevelWindows web-client)))

  (def test-url (.toString (.getBaseURL (.getEnclosedPage (last (.getTopLevelWindows web-client))))))
  test-url

  (s/valid? ::window (last (.getTopLevelWindows web-client)))

  (get (:query (url (get (:query (url test-url)) "next"))) "redirect_uri")

  (.close (last (.getTopLevelWindows web-client)))

  (def raw-links (map #(.click %) (take 4 bonuses)))

  (def raw-links-urls (map #(.getUrl %) raw-links))

  (def string-urls (map #(.toString %) raw-links-urls) )

  (nth string-urls 0)
  (nth string-urls 1)

  raw-links

  (.toString (first raw-links-urls))

  (take 5 bonuses)
  bonuses

  (first bonuses)

  (count bonuses)

  (count (-> web-client (.getWebWindows)))

  (-> web-client (.getTopLevelWindows))
  (count (-> web-client (.getTopLevelWindows)))

  (def criminal-case (slurp "http://www.baronstrainers.com/Criminal.Case.Teammates/"))

  (def farmville-two (slurp ""))

  (extract-from (parse criminal-case) ".container center a"
                [:url :img]
                "a" (attr :href)
                "img" (attr :src))

  )

(defroutes routes
  (GET "/" _
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (io/input-stream (io/resource "public/index.html"))})
  (resources "/"))

(def http-handler
  (-> routes
      (wrap-defaults api-defaults)
      wrap-with-logger
      wrap-gzip))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 10555))]
    (run-jetty http-handler {:port port :join? false})))


