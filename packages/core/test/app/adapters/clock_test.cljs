(ns app.adapters.clock-test
  (:require [cljs.test :refer [deftest is]]
            [app.ports :as p]
            [app.adapters.clock :as clock]))

(deftest system-clock-returns-positive-number
  (let [c (clock/make)
        t (p/-now c)]
    (is (number? t))
    (is (pos? t))
    (is (<= t (.now js/Date)))))
