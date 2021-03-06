(ns nngbc.server
    (:import (com.gargoylesoftware.htmlunit WebClient BrowserVersion WebWindowListener))
    (:require
      [clojure.java.io :as jio]
      [nngbc.common :as cmn]
      [clojure.java.io :as io]
      [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
      [compojure.route :refer [resources]]
      [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
      [ring.middleware.gzip :refer [wrap-gzip]]
      [ring.middleware.logger :refer [wrap-with-logger]]
      [ring.util.response :refer [redirect]]
      [environ.core :refer [env]]
      [ring.adapter.jetty :refer [run-jetty]]
      ;   [reaver :refer [parse extract-from text attr]]
      [clojure.spec :as s]
      [clojure.spec.test :as ts :refer [check]]
      [clojure.spec.gen :as gen]
      [cemerick.url :refer (url url-encode)]
      [clojure.java.jdbc :as jdbc]
      [overtone.at-at :as att])
    (:gen-class))

;alter number of html divisions based on env
(def mysql-db (System/getenv "DATABASE"))

(s/fdef get-all-gamedata
        :args empty?
        :ret ::cmn/gamedatas)

(defn get-all-gamedata []
      (map
        (fn [gamedata]
            (update gamedata :gameid long))
        (jdbc/query mysql-db ["SELECT * from games"])))

(s/fdef get-all-bonuses
       :args empty?
        :ret (s/and (s/every ::cmn/bonus-gamedata) vector?))

(defn get-all-bonuses []
      (into []
            (map
              (fn [{:keys [gameid gamename] :as game}]
                  {:gameid gameid
                   :gamename gamename
                   :bonuses (into []
                                  (map
                                    (fn [bonus]
                                        (->
                                          (update bonus :gameid long)
                                          (update :timestamp long)))
                                    (jdbc/query
                                      mysql-db
                                      ["SELECT * FROM bonuses WHERE gameid=? ORDER BY timestamp DESC LIMIT 20" gameid])))})
              (get-all-gamedata))))

(defroutes routes
           (GET "/bonuses" _
                {:status 200
                 :headers {"Content-Type" "text/html; charset=utf-8"}
                 :body (str (get-all-bonuses))})

           (ANY "/gbc/facebook/:appid/" [appid] (redirect "https://peaceful-harbor-5860.herokuapp.com/"))

           (resources "/")

           (ANY "/*" _
                {:status 200
                 :headers {"Content-Type" "text/html; charset=utf-8"}
                 :body (io/input-stream (io/resource "public/index.html"))}))

(def http-handler
  (-> routes
      (wrap-defaults api-defaults)
      wrap-with-logger
      wrap-gzip))

(defn -main [& [port]]
      (println "Main ran")
      #_(att/every 60000 #(harvest-bonuses) my-pool :desc "bonus harvest")
      (let [port (Integer. (or port (env :port) 10555))]
           (run-jetty http-handler {:port port :join? false})))

