(ns app.service
  "Сервисный слой. Принимает команды, применяет чистые функции из app.board,
   персистит в KV, отправляет в сокет, нотифицирует подписчиков."
  (:require [app.board  :as board]
            [app.domain :as domain]
            [app.ports  :as p]
            [promesa.core :as pr]))

;; --- ключи в KV-хранилище ---

(def ^:private board-key "kanban:board")

;; --- мультиметод: команда → чистое применение к board ---

(defmulti ^:private apply-command
  "Принимает board и command, возвращает новый board (или {:error ...})."
  (fn [_board command] (:type command)))

(defmethod apply-command :add-card
  [board {:keys [id title column-id created-at]}]
  (board/add-card board {:id id :title title
                         :column-id column-id :created-at created-at}))

(defmethod apply-command :remove-card
  [board {:keys [card-id]}]
  (board/remove-card board card-id))

(defmethod apply-command :move-card
  [board {:keys [card-id target-column-id target-index]}]
  (board/move-card board {:card-id card-id
                          :target-column-id target-column-id
                          :target-index target-index}))

(defmethod apply-command :rename-card
  [board {:keys [card-id title]}]
  (board/rename-card board card-id title))

(defmethod apply-command :reorder-columns
  [board {:keys [column-id target-index]}]
  (board/reorder-columns board {:column-id column-id
                                :target-index target-index}))

(defmethod apply-command :set-wip-limit
  [board {:keys [column-id limit]}]
  (board/set-wip-limit board column-id limit))

(defmethod apply-command :default
  [_ command]
  {:error :unknown-command :type (:type command)})

;; --- инициализация: попытаться загрузить из KV, иначе взять demo-board ---

(defn- init-board [{:keys [kv log]}]
  (pr/catch
    (pr/let [saved (p/-get kv board-key)]
      (if saved
        (do (p/-log log :info "hydrated from IDB" {:keys (count (:cards saved))})
            saved)
        (do (p/-log log :info "no saved board, using demo" {})
            (domain/demo-board))))
    (fn [err]
      (p/-log log :warn "hydration failed, using demo" {:error (ex-message err)})
      (domain/demo-board))))

;; --- главный API сервиса ---

(defn create-service
  "deps: {:kv ... :socket ... :clock ... :log ... :id-gen ... :socket-url ...}
   Возвращает map с ключами:
   :get-state    () → board
   :dispatch     (command) → nil | {:error ...}
   :subscribe    (listener) → unsubscribe-fn
   :shutdown     () → nil"
  [{:keys [kv socket clock log id-gen socket-url] :as deps}]
  (let [state-atom (atom nil)
        socket-conn-atom (atom nil)
        listeners-atom (atom #{})]

    ;; --- подписка на изменения atom'а транслирует в listeners ---
    (add-watch state-atom ::broadcast
               (fn [_ _ _ new-state]
                 (doseq [l @listeners-atom]
                   (try
                     (l new-state)
                     (catch :default e
                       (p/-log log :error "listener threw" {:err (ex-message e)}))))))

    ;; --- async-инициализация: hydrate + WS-коннект ---
    (-> (init-board deps)
        (pr/then
          (fn [board]
            (reset! state-atom board)
            (p/-log log :info "service ready" {})))
        (pr/catch
          (fn [err]
            (p/-log log :error "init failed" {:error (ex-message err)})
            (reset! state-atom (domain/demo-board)))))

    (when socket-url
      (-> (p/-connect socket socket-url
                      {:on-message (fn [msg]
                                     (p/-log log :debug "ws message" msg))
                       :on-close   (fn [info]
                                     (p/-log log :warn "ws closed" info))
                       :on-error   (fn [_]
                                     (p/-log log :error "ws error" {}))})
          (pr/then (fn [conn] (reset! socket-conn-atom conn)))
          (pr/catch (fn [err]
                      (p/-log log :warn "ws connect failed" {:error (ex-message err)})))))

    {:get-state
     (fn [] @state-atom)

     :dispatch
     (fn dispatch [command]
       (let [command (cond-> command
                       (= :add-card (:type command))
                       (-> (update :id #(or % (p/-new-id id-gen)))
                           (update :created-at #(or % (p/-now clock)))))
             current @state-atom
             result  (apply-command current command)]
         (cond
           (nil? current)
           (do (p/-log log :warn "dispatch before ready" {:type (:type command)})
               {:error :not-ready})

           (board/error? result)
           (do (p/-log log :warn "command rejected" {:command command :error result})
               result)

           :else
           (do
             (reset! state-atom result)
             (-> (p/-put kv board-key result)
                 (pr/catch (fn [e]
                             (p/-log log :error "persist failed"
                                     {:error (ex-message e)}))))
             (when-let [conn @socket-conn-atom]
               (p/-send socket conn {:type :command :command command}))
             nil))))

     :subscribe
     (fn [listener]
       (swap! listeners-atom conj listener)
       ;; возвращаем unsubscribe:
       (fn [] (swap! listeners-atom disj listener)))

     :shutdown
     (fn []
       (when-let [conn @socket-conn-atom]
         (p/-close socket conn)
         (reset! socket-conn-atom nil))
       (remove-watch state-atom ::broadcast)
       (reset! listeners-atom #{})
       nil)}))
