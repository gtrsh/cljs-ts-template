(ns app.ports
  "Протоколы — контракты с внешним миром. Минус в имени метода — конвенция,
   чтобы не путать с публичным API сервисов.")

(defprotocol KVStore
  (-get [this k]
    "Promise<значение | nil>.")
  (-put [this k v]
    "Promise<v>.")
  (-delete [this k]
    "Promise<nil>.")
  (-keys-with-prefix [this prefix]
    "Promise<[ключи...]>."))

(defprotocol Socket
  (-connect [this url handlers]
    "Promise<conn>.")
  (-send [this conn msg]
    "Синхронная отправка с буферизацией до open.")
  (-close [this conn]
    "Идемпотентно."))

(defprotocol Clock
  (-now [this]
    "Миллисекунды, number."))

(defprotocol Logger
  (-log [this level msg data]
    "level: :debug :info :warn :error. msg — строка. data — мапа."))

(defprotocol IdGen
  (-new-id [this]
    "Строка-идентификатор. В тестах — детерминированный счётчик,
     в проде — nanoid/uuid."))
