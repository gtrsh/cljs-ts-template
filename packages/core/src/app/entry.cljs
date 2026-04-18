(ns app.entry
  (:require [app.domain :as domain]
            [app.adapters.clock :as clock]
            [app.adapters.idb   :as idb]
            [app.adapters.ws    :as ws]))

(defn- board->api [board-atom]
  #js {:getState (fn [] (clj->js @board-atom))})

(defn ^:export create-app
  ([] (create-app #js {}))
  ([js-opts]
   (let [{:keys [db-name store-name]
          :or   {db-name "kanban" store-name "v1"}}
         (js->clj js-opts :keywordize-keys true)

         deps {:clock  (clock/make)
               :kv     (idb/make {:db-name db-name :store-name store-name})
               :socket (ws/make)}

         board-atom (atom (domain/demo-board))]

     ;; на шаге 4 deps попадут в сервисный слой; пока они просто собраны
     ;; для ранней проверки, что вся цепочка компилируется.
     (js/console.log "[core] deps ready:" (clj->js (keys deps)))

     #js {:board    (board->api board-atom)
          :shutdown (fn [] nil)})))

(defn ^:export create-app-with-deps
  "Принимает JS-объект с готовыми реализациями портов — для тестов/Storybook."
  [^js js-deps]
  (let [deps       (js->clj js-deps :keywordize-keys true)
        board-atom (atom domain/empty-board)]
    (js/console.log "[core] createAppWithDeps called with:" (clj->js (keys deps)))
    #js {:board    (board->api board-atom)
         :shutdown (fn [] nil)}))
