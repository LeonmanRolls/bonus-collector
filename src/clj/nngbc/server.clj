(ns nngbc.server
  (:require [clojure.java.io :as io]
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
            [compojure.route :refer [resources]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]]
            [reaver :refer [parse extract-from text attr]]
            [clojure.spec :as s]
            [clojure.spec.test :as ts]
            [clojure.spec.gen :as gen])
  (:gen-class))

(comment

  (def criminal-case (slurp "http://www.baronstrainers.com/Criminal.Case.Teammates/"))

  (def farmville-two (slurp ""))

  (extract-from (parse criminal-case) ".container .center a"
                [:url]
                "a" (attr :href))


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
