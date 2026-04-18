(ns app.entry
  (:require [app.domain :as domain]))

(defn- board->api
  "Превращает CLJS-состояние в JS-объект методов для TS-стороны.
   На шагах 4-5 здесь появятся setState/subscribe/moveCard/..., пока только getState."
  [board-atom]
  #js {:getState (fn [] (clj->js @board-atom))})

(defn ^:export create-app
  "Боевая сборка: реальные адаптеры, стартовое состояние из demo-board.
   Позже будет принимать опции (url сокета, имя IDB-store и т.д.)."
  ([] (create-app #js {}))
  ([_opts]
   (let [board-atom (atom (domain/demo-board))]
     #js {:board (board->api board-atom)
          :shutdown (fn [] nil)})))

(defn ^:export create-app-with-deps
  "Сборка с инжектом зависимостей — для тестов, Storybook, демо-страниц.
   Подробности на шаге 5."
  [^js _js-deps]
  (let [board-atom (atom domain/empty-board)]
    #js {:board (board->api board-atom)
         :shutdown (fn [] nil)}))
