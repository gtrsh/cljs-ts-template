(ns app.fakes
  "Детерминированные in-memory реализации портов для тестов.
   Каждая фабрика возвращает пару [fake inspectors] — сам фейк и
   функции-интроспекторы, чтобы тесты могли смотреть внутрь."
  (:require [app.ports :as p]
            [promesa.core :as pr]
            [clojure.string :as str]))

;; --- KV ---

(defn fake-kv
  "Опции: {:fail-get? bool :fail-put? bool :initial {k v ...}}"
  ([] (fake-kv {}))
  ([{:keys [fail-get? fail-put? initial]
     :or {fail-get? false fail-put? false initial {}}}]
   (let [store (atom initial)
         ops   (atom [])
         fake  (reify p/KVStore
                 (-get [_ k]
                   (swap! ops conj [:get k])
                   (if fail-get?
                     (pr/rejected (ex-info "fake-kv: get failed" {:k k}))
                     (pr/resolved (get @store k))))
                 (-put [_ k v]
                   (swap! ops conj [:put k])
                   (if fail-put?
                     (pr/rejected (ex-info "fake-kv: put failed" {:k k}))
                     (do (swap! store assoc k v)
                         (pr/resolved v))))
                 (-delete [_ k]
                   (swap! ops conj [:delete k])
                   (swap! store dissoc k)
                   (pr/resolved nil))
                 (-keys-with-prefix [_ prefix]
                   (pr/resolved (vec (filter #(str/starts-with? % prefix)
                                             (keys @store))))))]
     {:kv       fake
      :snapshot #(deref store)
      :ops      #(deref ops)})))

;; --- Socket ---

(defn fake-socket
  "Опции: {:fail-connect? bool}
   Эмулирует сокет. После connect резолвится синхронно (в микротаске).
   Входящие сообщения можно инжектить через :inject-message."
  ([] (fake-socket {}))
  ([{:keys [fail-connect?] :or {fail-connect? false}}]
   (let [conns      (atom [])
         sent       (atom [])
         closed?    (atom false)
         handlers-atom (atom nil)
         fake       (reify p/Socket
                      (-connect [_ url handlers]
                        (if fail-connect?
                          (pr/rejected (ex-info "fake-socket: connect failed" {:url url}))
                          (let [conn {:url url :id (count @conns)}]
                            (reset! handlers-atom handlers)
                            (swap! conns conj conn)
                            (pr/resolved conn))))
                      (-send [_ _conn msg]
                        (when-not @closed?
                          (swap! sent conj msg)))
                      (-close [_ _conn]
                        (reset! closed? true)))]
     {:socket    fake
      :sent      #(deref sent)
      :conns     #(deref conns)
      :closed?   #(deref closed?)
      :inject-message (fn [msg]
                        (when-let [h @handlers-atom]
                          (when-let [on-msg (:on-message h)]
                            (on-msg msg))))})))

;; --- Clock ---

(defn fake-clock
  ([] (fake-clock 1000000))
  ([initial]
   (let [t (atom initial)]
     {:clock (reify p/Clock
               (-now [_] @t))
      :advance! (fn [ms] (swap! t + ms))
      :set!     (fn [ms] (reset! t ms))})))

;; --- Logger ---

(defn fake-logger
  "Копит все логи в векторе. Для тестов удобно проверять,
   что сервис залогировал warning при ошибке персиста и т.д."
  []
  (let [entries (atom [])]
    {:log     (reify p/Logger
                (-log [_ level msg data]
                  (swap! entries conj {:level level :msg msg :data data})))
     :entries #(deref entries)
     :at-level (fn [level]
                 (filter #(= level (:level %)) @entries))}))

;; --- IdGen ---

(defn fake-id-gen
  "Счётчик id в виде 'id-0', 'id-1', ..."
  []
  (let [counter (atom 0)]
    {:id-gen (reify p/IdGen
              (-new-id [_]
                (let [n @counter]
                  (swap! counter inc)
                  (str "id-" n))))
     :next-n #(deref counter)}))

;; --- удобная сборка всех фейков сразу ---

(defn make-fakes
  "Удобный конструктор: возвращает {:deps ... :inspect {...}}.
   deps — готовая мапа для create-service / createAppWithDeps.
   inspect — функции для проверки состояния фейков."
  ([] (make-fakes {}))
  ([{:keys [kv-opts socket-opts clock-initial initial-kv]
     :or {kv-opts {} socket-opts {} clock-initial 1000000 initial-kv {}}}]
   (let [kv     (fake-kv (merge {:initial initial-kv} kv-opts))
         socket (fake-socket socket-opts)
         clock  (fake-clock clock-initial)
         logger (fake-logger)
         ids    (fake-id-gen)]
     {:deps    {:kv     (:kv kv)
                :socket (:socket socket)
                :clock  (:clock clock)
                :log    (:log logger)
                :id-gen (:id-gen ids)}
      :inspect {:kv-snapshot (:snapshot kv)
                :kv-ops      (:ops kv)
                :sent        (:sent socket)
                :conns       (:conns socket)
                :closed?     (:closed? socket)
                :inject-message (:inject-message socket)
                :logs        (:entries logger)
                :warn-logs   #((:at-level logger) :warn)
                :error-logs  #((:at-level logger) :error)
                :advance-clock! (:advance! clock)
                :now-id      (:next-n ids)}})))
