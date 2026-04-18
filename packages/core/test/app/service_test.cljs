(ns app.service-test
  (:require [cljs.test :refer [deftest testing is async]]
            [promesa.core :as pr]
            [app.fakes :as f]
            [app.service :as service]))

;; --- хелперы ---

(defn- settle
  "Ждёт 10мс реального времени, чтобы event loop гарантированно
   прогнал все fire-and-forget промиса до конца. Надёжнее, чем
   пытаться угадать количество микротасков."
  []
  (pr/delay 10 nil))

(defn- with-service
  "Создаёт сервис с фейками, ждёт :ready, передаёт тело теста.
   Возвращает Promise для use с async."
  [opts body-fn]
  (let [{:keys [deps inspect]} (f/make-fakes opts)
        svc (service/create-service deps)]
    (-> (:ready svc)
        (pr/then (fn [_] (body-fn svc inspect))))))

(defn- with-service-and-socket
  "Вариант для тестов с WebSocket: передаёт :socket-url, ждёт :ready."
  [opts body-fn]
  (let [{:keys [deps inspect]} (f/make-fakes opts)
        svc (service/create-service (assoc deps :socket-url "ws://test"))]
    (-> (:ready svc)
        (pr/then (fn [_] (body-fn svc inspect))))))

;; --- базовый lifecycle ---

(deftest service-hydrates-demo-board-when-kv-empty
  (async done
    (-> (with-service {}
          (fn [svc _]
            (let [state ((:get-state svc))]
              (is (some? state))
              (is (= 3 (count (:columns state))) "демо-доска имеет 3 колонки"))))
        (pr/finally done))))

(deftest service-hydrates-from-kv-when-saved
  (async done
    (let [saved {:columns      {"x" {:id "x" :title "Saved" :wip-limit nil :card-ids []}}
                 :column-order ["x"]
                 :cards        {}}]
      (-> (with-service {:initial-kv {"kanban:board" saved}}
            (fn [svc _]
              (is (= ["x"] (:column-order ((:get-state svc)))))))
          (pr/finally done)))))

(deftest service-falls-back-to-demo-when-kv-fails
  (async done
    (-> (with-service {:kv-opts {:fail-get? true}}
          (fn [svc inspect]
            (is (some? ((:get-state svc))))
            (is (= 3 (count (:columns ((:get-state svc)))))
                "при ошибке KV — demo-board")
            (is (seq ((:warn-logs inspect)))
                "факт ошибки залогирован на уровне warn")))
        (pr/finally done))))

;; --- dispatch ---

(deftest dispatch-add-card-updates-state
  (async done
    (-> (with-service {}
          (fn [svc _]
            (let [err ((:dispatch svc) {:type :add-card
                                        :title "Новая"
                                        :column-id "todo"})]
              (is (nil? err))
              (let [state ((:get-state svc))
                    todo-cards (get-in state [:columns "todo" :card-ids])]
                (is (= 2 (count todo-cards))
                    "в todo была 1 demo-карточка, стало 2")))))
        (pr/finally done))))

(deftest dispatch-enriches-add-card-with-id-and-time
  (async done
    (-> (with-service {:clock-initial 42}
          (fn [svc _]
            ((:dispatch svc) {:type :add-card :title "X" :column-id "todo"})
            (let [card (->> ((:get-state svc))
                            :cards
                            vals
                            (filter #(= "X" (:title %)))
                            first)]
              (is (= "id-0" (:id card)) "IdGen дал счётчик")
              (is (= 42 (:created-at card)) "Clock дал 42"))))
        (pr/finally done))))

(deftest dispatch-respects-explicit-id-and-time
  (async done
    (-> (with-service {}
          (fn [svc _]
            ((:dispatch svc) {:type :add-card
                              :id "explicit"
                              :title "X"
                              :column-id "todo"
                              :created-at 999})
            (let [card (get-in ((:get-state svc)) [:cards "explicit"])]
              (is (= 999 (:created-at card)) "явный created-at сохранился")
              (is (= "X" (:title card))))))
        (pr/finally done))))

(deftest dispatch-returns-error-without-mutating-state
  (async done
    (-> (with-service {}
          (fn [svc _]
            (let [before ((:get-state svc))
                  ;; doing: wip-limit 3, 1 demo card. Добавляем ещё 2 — станет 3/3.
                  _      ((:dispatch svc) {:type :add-card :title "a" :column-id "doing"})
                  _      ((:dispatch svc) {:type :add-card :title "b" :column-id "doing"})
                  ;; третья сверх лимита — должна отлететь
                  err    ((:dispatch svc) {:type :add-card :title "c" :column-id "doing"})
                  after  ((:get-state svc))]
              (is (= :wip-exceeded (:error err)))
              (is (= 3 (count (get-in after [:columns "doing" :card-ids])))
                  "ровно 3 карточки в doing (2 добавленных + 1 demo, лимит достигнут)")
              (is (not (identical? after before))
                  "стейт изменился (первые два add-card прошли), но третий не применялся"))))
        (pr/finally done))))

(deftest dispatch-move-card-across-columns
  (async done
    (-> (with-service {}
          (fn [svc _]
            (let [state-before ((:get-state svc))
                  todo-card-id (first (get-in state-before [:columns "todo" :card-ids]))]
              ((:dispatch svc) {:type :move-card
                                :card-id todo-card-id
                                :target-column-id "done"
                                :target-index nil})
              (let [state-after ((:get-state svc))]
                (is (= "done" (get-in state-after [:cards todo-card-id :column-id])))
                (is (contains? (set (get-in state-after [:columns "done" :card-ids]))
                               todo-card-id))))))
        (pr/finally done))))

;; --- persist ---

(deftest dispatch-persists-to-kv
  (async done
    (-> (with-service {}
          (fn [svc inspect]
            ((:dispatch svc) {:type :add-card :title "Persisted" :column-id "todo"})
            (-> (settle)
                (pr/then
                  (fn [_]
                    (let [saved (get ((:kv-snapshot inspect)) "kanban:board")]
                      (is (some? saved))
                      (is (some #(= "Persisted" (:title %))
                                (vals (:cards saved)))
                          "в KV лежит обновлённое состояние с новой карточкой")))))))
        (pr/finally done))))

(deftest dispatch-does-not-persist-on-error
  (async done
    (-> (with-service {}
          (fn [svc inspect]
            ((:dispatch svc) {:type :add-card :title "ok" :column-id "todo"})
            (-> (settle)
                (pr/then
                  (fn [_]
                    (let [snapshot-before ((:kv-snapshot inspect))
                          _ ((:dispatch svc) {:type :move-card
                                              :card-id "ghost"
                                              :target-column-id "todo"
                                              :target-index 0})]
                      (-> (settle)
                          (pr/then
                            (fn [_]
                              (let [snapshot-after ((:kv-snapshot inspect))]
                                (is (= snapshot-before snapshot-after)
                                    "после ошибочного dispatch в KV ничего не записалось"))))))))))) 
        (pr/finally done))))

(deftest persist-failure-is-logged-but-state-survives
  (async done
    (-> (with-service {:kv-opts {:fail-put? true}}
          (fn [svc inspect]
            ((:dispatch svc) {:type :add-card :title "X" :column-id "todo"})
            (is (some? (->> ((:get-state svc))
                            :cards
                            vals
                            (filter #(= "X" (:title %)))
                            first))
                "стейт обновился, хотя KV упал")
            (-> (settle)
                (pr/then
                  (fn [_]
                    (is (seq ((:error-logs inspect)))
                        "ошибка персиста залогирована на уровне error"))))))
        (pr/finally done))))

;; --- subscribe ---

(deftest subscribe-receives-updates-on-dispatch
  (async done
    (-> (with-service {}
          (fn [svc _]
            (let [received (atom [])
                  _        ((:subscribe svc) #(swap! received conj %))]
              ((:dispatch svc) {:type :add-card :title "1" :column-id "todo"})
              ((:dispatch svc) {:type :add-card :title "2" :column-id "todo"})
              (is (= 2 (count @received))
                  "listener вызван два раза"))))
        (pr/finally done))))

(deftest subscribe-unsubscribe-stops-updates
  (async done
    (-> (with-service {}
          (fn [svc _]
            (let [received (atom [])
                  unsub    ((:subscribe svc) #(swap! received conj %))]
              ((:dispatch svc) {:type :add-card :title "1" :column-id "todo"})
              (unsub)
              ((:dispatch svc) {:type :add-card :title "2" :column-id "todo"})
              (is (= 1 (count @received))
                  "после unsubscribe listener не вызывается"))))
        (pr/finally done))))

(deftest broken-listener-does-not-break-others
  (async done
    (-> (with-service {}
          (fn [svc inspect]
            (let [received (atom [])]
              ((:subscribe svc)
               (fn [_] (throw (ex-info "boom" {}))))
              ((:subscribe svc)
               (fn [b] (swap! received conj b)))
              ((:dispatch svc) {:type :add-card :title "x" :column-id "todo"})
              (is (= 1 (count @received))
                  "второй listener получил обновление, несмотря на падение первого")
              (is (seq ((:error-logs inspect)))
                  "падение листенера залогировано"))))
        (pr/finally done))))

;; --- websocket ---

(deftest socket-send-on-dispatch-when-url-provided
  (async done
    (-> (with-service-and-socket {}
          (fn [svc inspect]
            ((:dispatch svc) {:type :add-card :title "x" :column-id "todo"})
            (is (= 1 (count ((:sent inspect)))))
            (is (= :command (:type (first ((:sent inspect))))))
            (is (= :add-card (-> ((:sent inspect)) first :command :type)))))
        (pr/finally done))))

(deftest socket-not-connected-when-url-missing
  (async done
    (-> (with-service {}
          (fn [svc inspect]
            ((:dispatch svc) {:type :add-card :title "x" :column-id "todo"})
            (is (empty? ((:conns inspect))) "connect не вызывался")
            (is (empty? ((:sent inspect))) "send не вызывался")))
        (pr/finally done))))

(deftest socket-connect-failure-does-not-block-service
  (async done
    (-> (with-service-and-socket {:socket-opts {:fail-connect? true}}
          (fn [svc inspect]
            (is (some? ((:get-state svc))) "сервис всё равно инициализировался")
            ((:dispatch svc) {:type :add-card :title "x" :column-id "todo"})
            (is (empty? ((:sent inspect))) "сообщений не отправлено")
            (is (seq ((:warn-logs inspect))) "предупреждение залогировано")))
        (pr/finally done))))

;; --- shutdown ---

(deftest shutdown-closes-socket
  (async done
    (-> (with-service-and-socket {}
          (fn [svc inspect]
            ((:shutdown svc))
            (is ((:closed? inspect)) "сокет закрыт после shutdown")))
        (pr/finally done))))

(deftest shutdown-stops-broadcast
  (async done
    (-> (with-service {}
          (fn [svc _]
            (let [received (atom [])]
              ((:subscribe svc) #(swap! received conj %))
              ((:dispatch svc) {:type :add-card :title "before" :column-id "todo"})
              ((:shutdown svc))
              ((:dispatch svc) {:type :add-card :title "after" :column-id "todo"})
              (is (= 1 (count @received))
                  "после shutdown listener больше не получает событий"))))
        (pr/finally done))))
