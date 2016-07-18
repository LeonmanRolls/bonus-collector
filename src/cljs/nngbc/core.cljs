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

#_(s/def ::bonuses-cursor (s/with-gen
                            (s/and
                              #(instance? om.core/MapCursor %)
                              (s/valid? ::cmn/bonus)
                              )
                            (fn []
                                (s/gen #{(map-cursor-gen (atom {}))}))))

(s/def ::app-state (s/keys :req [::cmn/bonuses]))

;--------------------------------------------------------------------------------------------------------------

(defonce app-state (atom {::cmn/bonuses []}))

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

                         (dom/a #js {:href "#" :clasName "sidebar-a"}
                                (dom/i #js {:className "fa fa-television fa-3x"}))

                         (dom/a #js {:href "#bonus-row" :clasName "sidebar-a"}
                                (dom/i #js {:className "fa fa-gift fa-3x"}))))))

(s/fdef root-component
        :args (s/cat :data ::app-state :owner ::owner))

(defn root-component [app owner]
      (reify

        om/IWillMount
        (will-mount [_]
                    (GET "/bonuses"
                         {:handler (fn [resp]
                                       (om/update! app :bonuses (read-string resp)))}))

        om/IRender
        (render [_]
                (dom/div nil
                         (println (:bonuses app))
                         (om/build header {})
                         (om/build sidebar {})))))

(when
  (js/document.getElementById "app")
  (do
    (s/instrument-all)
    (om/root
      root-component
      app-state
      {:target (js/document.getElementById "app")})))

(comment



  )
