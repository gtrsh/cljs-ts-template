(ns app.entry
  (:require [app.service :as service]
            [app.adapters.clock :as clock-a]
            [app.adapters.idb   :as idb-a]
            [app.adapters.ws    :as ws-a]
            [app.adapters.log   :as log-a]
            [app.adapters.id    :as id-a]
            [clojure.set]))

(defn- board->js
  "Конвертирует CLJS-структуру board в JS, с переименованием
   kebab-case ключей в camelCase для TS-стороны."
  [board]
  (when board
    (let [rename-card   #(-> %
                             (clojure.set/rename-keys
                               {:created-at :createdAt :column-id :columnId}))
          rename-column #(-> %
                             (clojure.set/rename-keys
                               {:card-ids :cardIds :wip-limit :wipLimit}))]
      (clj->js
        {:columns     (into {} (map (fn [[k v]] [k (rename-column v)]) (:columns board)))
         :columnOrder (:column-order board)
         :cards       (into {} (map (fn [[k v]] [k (rename-card v)]) (:cards board)))}))))

(defn- js->command
  "Обратная конвертация: JS-команда от TS в CLJS-команду.
   camelCase → kebab-case, :type → keyword."
  [^js js-cmd]
  (let [m (js->clj js-cmd :keywordize-keys true)]
    (-> m
        (update :type keyword)
        (clojure.set/rename-keys
          {:cardId         :card-id
           :targetColumnId :target-column-id
           :targetIndex    :target-index
           :columnId       :column-id
           :createdAt      :created-at}))))

(defn- make-board-api [svc]
  (let [;; кеш: последняя пара (cljs-board, js-board)
        cache    (atom {:cljs nil :js nil})
        ;; snapshot возвращает js-board, пересчитывая только если cljs изменился
        snapshot (fn []
                   (let [current                                  ((:get-state svc))
                         {cached-cljs :cljs cached-js :js}        @cache]
                     (if (identical? current cached-cljs)
                       cached-js
                       (let [new-js (board->js current)]
                         (reset! cache {:cljs current :js new-js})
                         new-js))))]
    #js {:getState
         snapshot

         :dispatch
         (fn [js-cmd]
           (let [result ((:dispatch svc) (js->command js-cmd))]
             (if (and (map? result) (:error result))
               (clj->js result)
               nil)))

         :subscribe
         (fn [js-listener]
           ;; listener дёргает snapshot (общий кеш с getState!) — так React
           ;; при вызове listener, а затем getSnapshot получит один reference.
           ((:subscribe svc)
            (fn [_board] (js-listener (snapshot)))))}))

(defn ^:export create-app
  ([] (create-app #js {}))
  ([js-opts]
   (let [{:keys [socket-url db-name store-name]
          :or   {db-name "kanban" store-name "v1"}}
         (js->clj js-opts :keywordize-keys true)

         deps {:clock      (clock-a/make)
               :kv         (idb-a/make {:db-name db-name :store-name store-name})
               :socket     (ws-a/make)
               :log        (log-a/make)
               :id-gen     (id-a/make)
               :socket-url socket-url}

         svc (service/create-service deps)]

     #js {:board    (make-board-api svc)
          :shutdown (:shutdown svc)})))

(defn ^:export create-app-with-deps
  "Принимает JS-объект с готовыми реализациями портов. На шаге 5
   этот путь будет покрыт интеграционными тестами с fake-адаптерами."
  [^js js-deps]
  (let [deps (js->clj js-deps :keywordize-keys true)
        svc  (service/create-service deps)]
    #js {:board    (make-board-api svc)
         :shutdown (:shutdown svc)}))
