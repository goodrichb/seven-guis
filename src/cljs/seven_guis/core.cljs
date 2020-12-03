(ns seven-guis.core
  (:require
   [reagent.dom :as rdom]
   [seven-guis.views :as views]
   [seven-guis.config :as config]))

(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/main-panel] root-el)))

(defn init []
  (dev-setup)
  (mount-root))
