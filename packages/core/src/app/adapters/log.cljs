(ns app.adapters.log
  (:require [app.ports :as p]))

(def ^:private level->fn
  {:debug #(.debug js/console %1 %2)
   :info  #(.info  js/console %1 %2)
   :warn  #(.warn  js/console %1 %2)
   :error #(.error js/console %1 %2)})

(defrecord ConsoleLogger []
  p/Logger
  (-log [_ level msg data]
    (let [f (get level->fn level (level->fn :info))]
      (f (str "[core] " msg) (clj->js data)))))

(defn make [] (->ConsoleLogger))
