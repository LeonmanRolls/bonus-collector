(ns nngbc.common
    (:require
      [cemerick.url :refer (url url-encode)]
      #?(:clj  [clojure.spec :as s]
         :cljs [cljs.spec :as s])
      #?(:clj [clojure.spec.test :as ts])
      #?(:clj [clojure.spec.gen :as gen])
      #?(:cljs [cljs.spec.impl.gen :as gen])))

(s/def ::bonus_url_string
  (s/with-gen
    #(not (nil? (:query (url %))))
    (fn [] (s/gen #{"https://apps.facebook.com/farmville-two/viral.php?viralId=be012f04287d1360de69368f70369268_33671037398"}))))

(s/def ::title string?)

(s/def ::img_url
  (s/with-gen
    #(not (nil? (re-find #"(?:([^:/?#]+):)?(?://([^/?#]*))?([^?#]*\.(?:jpg|gif|png))(?:\?([^#]*))?(?:#(.*))?" %)))
    (fn [] (s/gen #{"https://zdnfarmtwo3-a.akamaihd.net/assets/icons/icon_buildable_racing_yak_flag_cogs-b36350ea1d3d34cd8adacac1afcd7c80.png"
                    "https://zdnfarmtwo3-a.akamaihd.net/assets/icons/icon_buildable_racing_yak_flag_cogs-b36350ea1d3d34cd8adacac1afcd7c80.jpg"}))))

(s/def ::timestamp
  (s/with-gen
    #?(:clj #(instance? java.lang.Long %)
       :cljs number?)
    (fn [] (s/gen #{1468028021}))))

(s/def ::gameid
  (s/with-gen
    #?(:clj #(instance? java.lang.Long %)
       :cljs number?)
    (fn [] (s/gen #{321574327904696}))))

(s/def ::bonus (s/keys :req [::bonus_url_string ::title ::img_url ::timestamp ::gameid]))

(s/def ::bonuses (s/coll-of ::bonus))


