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

(def my-pool (att/mk-pool))

(def mysql-db
  (or
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

(s/def ::web-client
  (s/with-gen
    #(instance? WebClient %)
    (fn [] (s/gen #{(new WebClient)}))))

;=========================================================================================

(def window-listener
  (reify
    WebWindowListener

    (webWindowClosed [this event]
                     (println "web window closed: " event))

    (webWindowContentChanged [this event]
                             (println "web window content changed: " event))

    (webWindowOpened [this event]

                     (println "web window opened: " event))))

(defn time-stamp [] (quot (System/currentTimeMillis) 1000))

(s/fdef raw-url->bonus-url
        :args (s/cat :raw-url ::raw_url_string)
        :ret  ::cmn/bonus_url_string)

(defn raw-url->bonus-url [url-string]
      (->
        url-string
        url
        :query
        (get "next")
        url
        :query
        (get "redirect_uri")))

(s/fdef window->raw-url
        :args (s/cat :window ::window)
        :ret string?)

(defn window->raw-url [window]
      (.toString (.getBaseURL (.getEnclosedPage window))))

(s/fdef get-html-divisions
        :args (s/cat :gameskip-url ::cmn/gameskip-url)
        :ret ::bonus-html-division-coll)

(defn get-html-divisions [gameskip-url]
      (into []
            (->
              (let [web-client (new WebClient BrowserVersion/CHROME)]
                   (.setTimeout (.getOptions web-client) 0)
                   (.setMaxInMemory (.getOptions web-client) 4096000)
                   web-client)
              (.getPage gameskip-url)
              (.getBody)
              (.getByXPath "//div[@class=\"title box\"]"))))

(s/fdef bonus-img-handler
        :args (s/cat :img-url ::cmn/img_url)
        :ret ::cmn/img_url)

(defn bonus-img-handler [img-url]
      (if
        (.contains "manual" img-url)
        "http://static1.squarespace.com/static/52b5dd41e4b0edd5cee29a6c/t/52b9fd52e4b0d9c93d4ecdeb/1387920722995/domo3+50x50.png"
        img-url))

(s/fdef get-latest-gameskip-bonus
        :args (s/cat :gameskip-url ::cmn/gameskip-url :game-id ::cmn/gameid)
        :ret ::cmn/bonus)

(defn get-latest-gameskip-bonus [gameskip-url gameid]
      (try
        (let [bonus-html-division (first (get-html-divisions gameskip-url))
              _ (println "bonus-html-division: " bonus-html-division)
              page (.click bonus-html-division)
              _ (println "page: " page)
              bonus-url (raw-url->bonus-url (.toString (-> page (.getBaseURL))))
              _ (println "bonus-url: " bonus-url)
              img-div (first (.getByXPath bonus-html-division "./table/tbody/tr/td/div/img"))
              title (.getAltAttribute img-div)
              img-url (bonus-img-handler (get (:query (url (.getAttribute img-div "data-original"))) "url"))]
             {::cmn/bonus_url_string bonus-url
              ::cmn/title title
              ::cmn/img_url img-url
              ::cmn/timestamp (time-stamp)
              ::cmn/gameid gameid})

        (catch Exception e
          (println "gameskip-url: " gameskip-url)
          (println "gameid: " gameid)
          (println "Exception in get-latest-gameskip-bonus: " e))))

(s/fdef insert-bonus!
        :args (s/cat :bonuses ::cmn/bonus))

(defn insert-bonus! [bonus]
      (println "Inserting bonus")
      (try
        (jdbc/insert! mysql-db :bonuses bonus)
        (catch Exception e (println "insert exception"))))

(defn get-all-gamedata []
      (map
        (fn [gamedata]
            (update gamedata :gameid long))
        (jdbc/query mysql-db ["SELECT * from games"])))

(s/fdef gameid->gamename
        :args ::cmn/gameid
        :ret ::cmn/gamedata)

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
                       (println "bonus: " bonus)
                       {:gameid (first bonus)
                        :gamename (gameid->gamename (ffirst bonus))
                        :bonuses (last bonus)})
                   grouped-bonuses))))

(comment
 mysql-db
(jdbc/query mysql-db ["SELECT * FROM bonuses ORDER BY timestamp DESC LIMIT 50"])
 (str (get-all-bonuses))
  )

(defn harvest-bonuses []
      (println "Harvest bonuses running")
      (dorun
        (map
          (fn [{:keys [gameskip_url gameid] :as gamedata}]
              (let [bonus (get-latest-gameskip-bonus gameskip_url gameid)]
                   (when bonus (insert-bonus! bonus))))
          (get-all-gamedata))))

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
      (ts/instrument)
      #_(att/every 60000 #(harvest-bonuses) my-pool :desc "bonus harvest")
      (let [port (Integer. (or port (env :port) 10555))]
           (run-jetty http-handler {:port port :join? false})))


