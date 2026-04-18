(ns hooks
  (:require [clojure.java.io :as io]))

(defn write-dist-package-json
  "Пишет dist/package.json с {\"type\":\"module\"}, чтобы Node трактовал
   dist/index.js как ESM, не распространяя это правило на родительский
   пакет (где лежит out/node-tests.js и runtime тестов — они CommonJS)."
  [state & _]
  (io/make-parents "dist/_")
  (spit "dist/package.json" "{\"type\":\"module\"}")
  state)
