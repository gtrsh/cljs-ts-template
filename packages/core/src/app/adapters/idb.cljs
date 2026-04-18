(ns app.adapters.idb
  "Адаптер KVStore поверх idb-keyval. Ключи храним в формате
   'kanban:card:UUID' или 'kanban:board' — префиксная структура
   поможет на шаге 4 для инкрементальных обновлений."
  (:require [app.ports :as p]
            [promesa.core :as pr]
            [clojure.string :as str]
            ["idb-keyval" :as idb]))

(defn- ->cljs [v]
  (when (some? v)
    (js->clj v :keywordize-keys true)))

(defn- ->js [v]
  (clj->js v))

(defrecord IdbKvStore [store]
  p/KVStore
  (-get [_ k]
    (pr/let [v (idb/get k store)]
      (->cljs v)))

  (-put [_ k v]
    (pr/let [_ (idb/set k (->js v) store)]
      v))

  (-delete [_ k]
    (pr/let [_ (idb/del k store)]
      nil))

  (-keys-with-prefix [_ prefix]
    (pr/let [all (idb/keys store)]
      (->> all
           js->clj
           (filter #(and (string? %)
                         (str/starts-with? % prefix)))
           vec))))

(defn make
  "opts: {:db-name \"kanban\" :store-name \"v1\"}"
  [{:keys [db-name store-name]
    :or   {db-name "kanban" store-name "v1"}}]
  (->IdbKvStore (idb/createStore db-name store-name)))
