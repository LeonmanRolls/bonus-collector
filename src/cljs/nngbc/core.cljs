(ns nngbc.core
  (:require
    [cljs.reader :refer [read-string]]
    [ajax.core :refer [GET POST]]
    [nngbc.common :as cmn]
    [om.core :as om :include-macros true]
    [om.dom :as dom :include-macros true]
    [cljs.spec :as s]
    [cljs.spec.test :as ts]
    [cljs.spec.impl.gen :as gen]))

(enable-console-print!)

;Type stuff------------------------------------------------------------------------------------------------------

(defn cursor-gen [val]
      (om/ref-cursor (om/root-cursor (atom val))))

(defn map-cursor-gen [map-atom]
      (om/ref-cursor (om/root-cursor map-atom)))

(defn indexed-cursor-gen [vector-atom]
      (om/ref-cursor (om/root-cursor vector-atom)))

(s/def ::owner (s/with-gen
                 #(instance? js/Object %)
                 (fn [] (s/gen #{(js-obj "hi" "there")}))))

(s/def ::map-cursor (s/with-gen
                      #(instance? om.core/MapCursor %)
                      (fn []
                          (s/gen #{(map-cursor-gen (atom {}))}))))

(s/def ::indexed-cursor (s/with-gen
                          #(instance? om.core/IndexedCursor %)
                          (fn []
                              (s/gen #{(indexed-cursor-gen (atom []))}))))

(defn spec-indexed-cursor-gen [val val-test]
      (s/with-gen
        (s/and
          #(instance? om.core/IndexedCursor %)
          (fn [x]
              (val-test (first @x))))
        (fn []
            (s/gen #{(cursor-gen [val])}))))

(s/def ::bonus-gamedata (s/keys :req-un [::cmn/gamename ::cmn/gameid ::cmn/bonuses]))

(s/def ::bonus-gamedatas (s/or
                           :empty-init ::indexed-cursor
                           :data-data (s/every ::bonus-gamedata)))

(s/def ::app-state (s/keys :req [::bonus-gamedatas]))

;--------------------------------------------------------------------------------------------------------------

(defonce app-state (atom {::bonus-gamedatas []}))

(defn error-handler [{:keys [status status-text]}]
      (.log js/console (str "something bad happened: " status " " status-text)))

(s/fdef header
        :args (s/cat :data map? :owner ::owner))

(defn header [data owner]
      (reify
        om/IRender
        (render [this]
                (dom/div #js {:className "search-header"}
                         (dom/a #js {:className "logo"})))))

(s/fdef sidebar
        :args (s/cat :data map? :owner ::owner))

(defn sidebar [data owner]
      (reify
        om/IRender
        (render [this]
                (dom/div #js {:className "simple-sidebar"}

                         (dom/a #js {:href "#" :className "sidebar-a"}
                                (dom/i #js {:className "fa fa-television fa-3x"}))

                         (dom/a #js {:href "#bonus-row" :className "sidebar-a"}
                                (dom/i #js {:className "fa fa-gift fa-3x"}))))))

(s/fdef bonus-partial
        :args (s/cat :data ::cmn/bonus :owner ::owner))

(defn bonus-partial [{:keys [title bonus_url_string img_url timestamp gameid] :as data} owner]
      (reify
        om/IRender
        (render [_]
                (dom/div #js {:className "col-md-2 col-sm-3 col-xs-6 bonus"}
                         (dom/a #js {:href bonus_url_string :className "song" :target "_blank"}
                                (dom/figure nil
                                              (dom/img #js {:src img_url :id bonus_string_url
                                                            :className "bonus-img"}))
                                (dom/div #js {:href "#" :className "song-title"} title)
                                #_(dom/div #js {:href "#" :className "song-title"}
                                           "Clicks: " clicks))))))

(s/fdef bonus-container
        :args (s/cat :data ::bonus-gamedata :owner ::owner))

(defn bonus-container [{:keys [gameid gamename bonuses] :as data} owner]
      (reify
        om/IRender
        (render [_]
                (dom/div
                  #js {:className "row"}
                  (dom/h1 nil gamename)
                  (om/build-all bonus-partial bonuses {:key :bonus_url_string})))))

(s/fdef loading :args (s/cat :data map? :owner ::owner))

(defn loading [data owner]
      (reify
        om/IRender
        (render [_]
                (dom/div #js{:className "loading"}
                         (dom/h1 nil "Loading...")
                         (dom/img #js {:src "https://media.giphy.com/media/26tPgy93ssTeTTSqA/giphy.gif"})))))

(s/fdef root-component
        :args (s/cat :data ::app-state :owner ::owner))

(defn root-component [{:keys [::bonus-gamedatas] :as app} owner]
      (reify

        om/IWillMount
        (will-mount [_]
                    (GET "/bonuses"
                           {:handler (fn [resp]
                                         (om/update! app ::bonus-gamedatas (read-string resp)))}))

        om/IRender
        (render [_]
                (dom/div nil
                         (om/build header {})
                         (om/build sidebar {})
                         (when (empty? bonus-gamedatas)
                               (om/build loading {}))
                         (apply dom/div #js {:className "container bonus-container"}
                                (om/build-all bonus-container (vec (reverse bonus-gamedatas)) {:key :gameid}))))))

(when
  (js/document.getElementById "app")
  (do
    (om/root
      root-component
      app-state
      {:target (js/document.getElementById "app")})))

(comment
  (browser-repl)

  (GET "/bonuses"
       {:handler (fn [resp] (println "resp: " resp))})

  )

