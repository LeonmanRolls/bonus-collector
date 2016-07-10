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
      [clojure.spec.test :as ts :refer [check]]
      [clojure.spec.gen :as gen]
      [cemerick.url :refer (url url-encode)]
      [clojure.java.jdbc :as jdbc])
    (:gen-class))

(def mysql-db
  (or
    (System/getenv "DATABASE_URL")
    {:subprotocol "postgresql"
     :subname "//localhost:5432/nngbc"
     :user "postgres"
     :password "1fishy4me"}))

;Value gens ==============================================================================

(defn html-division-gen []
        (->
          (new WebClient BrowserVersion/FIREFOX_38)
          (.getPage "http://www.google.com")
          (.getBody)
          (.getByXPath "//div[@class=\"ctr-p\"]")
          first))

;Types ===================================================================================

(s/def ::bonus_url_string
  (s/with-gen
    #(not (nil? (:query (url %))))
    (fn [] (s/gen #{"https://apps.facebook.com/farmville-two/viral.php?viralId=be012f04287d1360de69368f70369268_33671037398"}))))

(s/def ::raw_url_string
  (s/with-gen
    #(not (nil? (:query (url %))))
    (fn [] (s/gen #{"https://www.facebook.com/login.php?skip_api_login=1&api_key=321574327904696&signed_next=1&next=https%3A%2F%2Fwww.facebook.com%2Fv2.0%2Fdialog%2Foauth%3Fredirect_uri%3Dhttps%253A%252F%252Fapps.facebook.com%252Ffarmville-two%252Fviral.php%253FviralId%253D5c88d281e966fba5a1c39e1f694ae326_31119553664%2526amp%25253Bh%253D-AQFez6YC%2526amp%25253Benc%253DAZO3rIVVOFtpYIkLifXyRJqDHIhGqw0ob1w_nZE0SW2oB9LR1AduzBrTdfprK9UhApnJItVCcmP6CfgOEEqMG5eNnkb03LWq3pWCABjgVwSAlFifQug6N0kDbdYVw_7e3%26state%3Dae22dc5cc750b87517687e2eaf391bde%26scope%3Duser_friends%252Cemail%252Cuser_birthday%252Cpublish_actions%26client_id%3D321574327904696%26ret%3Dlogin%26logger_id%3Dfcb12011-90fb-4ea7-9491-4af26122fce9&cancel_url=https%3A%2F%2Fapps.facebook.com%2Ffarmville-two%2Fviral.php%3FviralId%3D5c88d281e966fba5a1c39e1f694ae326_31119553664%26amp%253Bh%3D-AQFez6YC%26amp%253Benc%3DAZO3rIVVOFtpYIkLifXyRJqDHIhGqw0ob1w_nZE0SW2oB9LR1AduzBrTdfprK9UhApnJItVCcmP6CfgOEEqMG5eNnkb03LWq3pWCABjgVwSAlFifQug6N0kDbdYVw_7e3%26error%3Daccess_denied%26error_code%3D200%26error_description%3DPermissions%2Berror%26error_reason%3Duser_denied%26state%3Dae22dc5cc750b87517687e2eaf391bde%23_%3D_&display=page&locale=zh_TW&logger_id=fcb12011-90fb-4ea7-9491-4af26122fce9"}))))

(s/def ::window
  (s/with-gen
    #(instance? com.gargoylesoftware.htmlunit.TopLevelWindow %)
    (fn [](s/gen #{(let [web-client (new WebClient)]
                        (.getPage web-client "https://gameskip.com/farmville-2-links/non-friend-bonus.html")
                        (last (.getTopLevelWindows web-client)))}))))

(s/def ::html-division-coll
  (s/with-gen
    (s/coll-of #(instance? com.gargoylesoftware.htmlunit.html.HtmlDivision %))
    (fn [] (s/gen #{[(html-division-gen)]}))))

(s/def ::title string?)

(s/def ::timestamp
  (s/with-gen
    #(instance? java.lang.Long %)
    (fn [] (s/gen #{1468028021}))))

(s/def ::img_url
  (s/with-gen
    #(not (nil? (re-find #"(?:([^:/?#]+):)?(?://([^/?#]*))?([^?#]*\.(?:jpg|gif|png))(?:\?([^#]*))?(?:#(.*))?" %)))
    (fn [] (s/gen #{"https://zdnfarmtwo3-a.akamaihd.net/assets/icons/icon_buildable_racing_yak_flag_cogs-b36350ea1d3d34cd8adacac1afcd7c80.png"
                    "https://zdnfarmtwo3-a.akamaihd.net/assets/icons/icon_buildable_racing_yak_flag_cogs-b36350ea1d3d34cd8adacac1afcd7c80.jpg"}))))

(s/def ::gameid
  (s/with-gen
    #(instance? java.lang.Long %)
    (fn [] (s/gen #{321574327904696}))))

(s/def ::gamename string?)

(s/def ::bonus (s/keys :req [::bonus_url_string ::title ::img_url ::timestamp ::gameid]))

(s/def ::bonuses (s/coll-of ::bonus))

(s/def ::gameskip-url
  (s/with-gen
    #(not (nil? (re-find #"gameskip.com" %)))
    (fn [] (s/gen #{"https://gameskip.com/farmville-2-links/non-friend-bonus.html"}))))

(s/def ::gamedata (s/keys :req-un [::gamename ::gameid ::gameskip-url]))

(s/def ::gamedatas (s/coll-of ::gamedata))

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
        :ret  ::bonus_url_string)

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

(s/fdef get-gameskip-bonuses
        :args (s/cat :gameskip-url ::gameskip-url :gameid ::gameid)
        :ret ::bonuses)

(defn get-gameskip-bonuses [gameskip-url gameid]
      (let [bonuses (atom [])
            web-client (new WebClient BrowserVersion/FIREFOX_38)
            html-divisions  (->
                              web-client
                              (.getPage gameskip-url)
                              (.getBody)
                              (.getByXPath "//div[@class=\"title box\"]"))
            _ (doall
                (map
                  (fn [html-division]
                      (let [_ (.click html-division)
                            raw-url (window->raw-url (last (.getTopLevelWindows web-client)))
                            bonus-url (raw-url->bonus-url raw-url)
                            img-div (first (.getByXPath html-division "./table/tbody/tr/td/div/img"))
                            title (.getAltAttribute img-div)
                            img-url (get (:query (url (.getAttribute img-div "data-original"))) "url")]

                           (swap! bonuses conj {::bonus_url_string bonus-url
                                                ::title title
                                                ::img_url img-url
                                                ::timestamp (time-stamp)
                                                ::gameid gameid})
                           (.close (last (.getTopLevelWindows web-client)))))
                  (take 2 html-divisions)))]
           @bonuses))

(defn get-all-gamedata []
      (jdbc/query mysql-db ["SELECT * from games"]))

(s/fdef insert-bonuses!
        :args (s/cat :bonuses ::bonuses))

(defn insert-bonuses! [bonuses]
      (doall
        (map
          (fn [bonus]
              (try
                (jdbc/insert! mysql-db :bonuses bonus)
                (catch Exception e
                  (println "Exception, presumably bonus primary key "))))
          bonuses)))

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
      (ts/instrument)
      (let [port (Integer. (or port (env :port) 10555))]
           (run-jetty http-handler {:port port :join? false})))

