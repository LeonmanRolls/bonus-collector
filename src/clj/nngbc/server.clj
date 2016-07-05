(ns nngbc.server
    (:import (com.gargoylesoftware.htmlunit WebClient BrowserVersion))
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
      [clojure.spec.gen :as gen])
    (:gen-class))


(comment

  (def web-client (new WebClient BrowserVersion/FIREFOX_38))

  (def page (.getPage web-client "https://gameskip.com/farmville-2-links/non-friend-bonus.html"))

  (def bonuses (-> page (.getBody) (.getByXPath "//div[@class=\"title box\"]")))

  bonuses

  (first bonuses)

  (-> (nth bonuses 9) (.click))

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
