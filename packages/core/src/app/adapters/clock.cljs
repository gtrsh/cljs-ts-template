(ns app.adapters.clock
  (:require [app.ports :as p]))

(defrecord SystemClock []
  p/Clock
  (-now [_] (.now js/Date)))

(defn make [] (->SystemClock))
