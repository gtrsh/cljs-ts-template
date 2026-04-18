(ns app.adapters.id
  (:require [app.ports :as p]
            ["nanoid" :refer [nanoid]]))

(defrecord NanoIdGen []
  p/IdGen
  (-new-id [_] (nanoid 10)))

(defn make [] (->NanoIdGen))
