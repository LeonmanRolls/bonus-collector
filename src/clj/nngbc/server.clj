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

(def mysql-db
  "postgres://oyznmdyhopqgua:hUjqAP5QBu8Ul6kbniHOMtv_89@ec2-54-243-48-204.compute-1.amazonaws.com:5432/dd3lchl2jq0q3c?ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory"
  #_(or
    (System/getenv "DATABASE")
    {:subprotocol "postgresql"
     :subname "//localhost:5432/nngbc"
     :user "postgres"
     :password "1fishy4me"}))

;Value gens ==============================================================================

(defn web-client-gen []
  (let [web-client (new WebClient BrowserVersion/CHROME)]
                   (.setTimeout (.getOptions web-client) 0)
                   (.setMaxInMemory (.getOptions web-client) 4096000)
                   web-client))

(def html-division
  (->
    (web-client-gen)
    (.getPage "http://www.google.com")
    (.getBody)
    (.getByXPath "//div[@class=\"ctr-p\"]")
    first))

(def bonus-division
  (->
    (web-client-gen)
    (.getPage "https://gameskip.com/farmville-2-links/non-friend-bonus.html")
    (.getBody)
    (.getByXPath "//div[@class=\"title box\"]")
    first))

;Types ===================================================================================

(s/def ::raw_url_string
  (s/with-gen
    #(not (nil? (:query (url %))))
    (fn [] (s/gen #{"https://www.facebook.com/login.php?skip_api_login=1&api_key=321574327904696&signed_next=1&next=https%3A%2F%2Fwww.facebook.com%2Fv2.0%2Fdialog%2Foauth%3Fredirect_uri%3Dhttps%253A%252F%252Fapps.facebook.com%252Ffarmville-two%252Fviral.php%253FviralId%253D5c88d281e966fba5a1c39e1f694ae326_31119553664%2526amp%25253Bh%253D-AQFez6YC%2526amp%25253Benc%253DAZO3rIVVOFtpYIkLifXyRJqDHIhGqw0ob1w_nZE0SW2oB9LR1AduzBrTdfprK9UhApnJItVCcmP6CfgOEEqMG5eNnkb03LWq3pWCABjgVwSAlFifQug6N0kDbdYVw_7e3%26state%3Dae22dc5cc750b87517687e2eaf391bde%26scope%3Duser_friends%252Cemail%252Cuser_birthday%252Cpublish_actions%26client_id%3D321574327904696%26ret%3Dlogin%26logger_id%3Dfcb12011-90fb-4ea7-9491-4af26122fce9&cancel_url=https%3A%2F%2Fapps.facebook.com%2Ffarmville-two%2Fviral.php%3FviralId%3D5c88d281e966fba5a1c39e1f694ae326_31119553664%26amp%253Bh%3D-AQFez6YC%26amp%253Benc%3DAZO3rIVVOFtpYIkLifXyRJqDHIhGqw0ob1w_nZE0SW2oB9LR1AduzBrTdfprK9UhApnJItVCcmP6CfgOEEqMG5eNnkb03LWq3pWCABjgVwSAlFifQug6N0kDbdYVw_7e3%26error%3Daccess_denied%26error_code%3D200%26error_description%3DPermissions%2Berror%26error_reason%3Duser_denied%26state%3Dae22dc5cc750b87517687e2eaf391bde%23_%3D_&display=page&locale=zh_TW&logger_id=fcb12011-90fb-4ea7-9491-4af26122fce9"}))))

(s/def ::window
  (s/with-gen
    #(instance? com.gargoylesoftware.htmlunit.TopLevelWindow %)
    (fn [](s/gen #{(let [web-client (web-client-gen)]
                        (.getPage web-client "https://gameskip.com/farmville-2-links/non-friend-bonus.html")
                        (last (.getTopLevelWindows web-client)))}))))

(s/def ::bonus-html-division
  (s/with-gen
    #(instance? com.gargoylesoftware.htmlunit.html.HtmlDivision %)
    (fn [] (s/gen #{bonus-division}))))

(s/def ::html-division-coll
  (s/with-gen
    (s/coll-of #(instance? com.gargoylesoftware.htmlunit.html.HtmlDivision %))
    (fn [] (s/gen #{[html-division]}))))

(s/def ::bonus-html-division-coll
  (s/with-gen
    (s/coll-of #(instance? com.gargoylesoftware.htmlunit.html.HtmlDivision %))
    (fn [] (s/gen #{[bonus-division]}))))

;=========================================================================================

(defn get-all-gamedata []
      (map
        (fn [gamedata]
            (update gamedata :gameid long))
        (jdbc/query mysql-db ["SELECT * from games"])))

(comment
 (get-all-gamedata)

  )

(s/fdef gameid->gamename
        :args (s/cat :gameid ::cmn/gameid)
        :ret ::cmn/gamename)

(def gameid->gamename
  (memoize
    (fn [gameid]
        (->
          (jdbc/query mysql-db ["SELECT gamename FROM games WHERE gameid=?" gameid])
          first
          (:gamename)))))

(defn get-all-bonuses []
      (into []
            (let [grouped-bonuses (group-by
                                    :gameid
                                    (into []
                                          (map
                                            (fn [bonus]
                                                (->
                                                  (update bonus :gameid long)
                                                  (update :timestamp long)))
                                            (jdbc/query mysql-db ["SELECT * FROM bonuses ORDER BY timestamp DESC LIMIT 50"]))))]
                 (map
                   (fn [bonus]
                       {:gameid (first bonus)
                        :gamename (gameid->gamename (first bonus))
                        :bonuses (last bonus)})
                   grouped-bonuses))))

(defroutes routes
           (GET "/" _
                {:status 200
                 :headers {"Content-Type" "text/html; charset=utf-8"}
                 :body (io/input-stream (io/resource "public/index.html"))})

           (GET "/bonuses" _
                {:status 200
                 :headers {"Content-Type" "text/html; charset=utf-8"}
                 :body (str (get-all-bonuses))})
(resources "/"))

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


