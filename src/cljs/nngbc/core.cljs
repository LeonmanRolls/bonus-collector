(ns nngbc.core
  (:require
    [nngbc.common :as cmn]
    [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.spec :as s]
            [cljs.spec.test :as ts]
            [cljs.spec.impl.gen :as gen]))

(enable-console-print!)

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

(defonce app-state (atom {:text "Hello Chestnut!"
                          :bonuses []}))

(s/fdef header
        :args (s/cat :data map? :owner ::owner))

(defn header [data owner]
      (reify
        om/IRender
        (render [this]
                (dom/div #js {:className "search-header"}
                         (println "data: " (type data))
                                     (dom/a #js {:className "logo"})))))

(defn root-component [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
            (om/build header {})
               (dom/h1 nil (:text app))))))

(when
  (js/document.getElementById "app")
  (do
    (s/instrument-all)
    (om/root
      root-component
      app-state
      {:target (js/document.getElementById "app")})))

#_(om/root
 root-component
 app-state
 {:target (js/document.getElementById "app")})

