(ns app.ports
  "Протоколы — контракты с внешним миром. Минус в имени метода — конвенция,
   чтобы не путать с публичным API сервисов (см. шаг 4).")

(defprotocol KVStore
  (-get    [this k]
    "Promise<значение | nil>. nil если ключа нет.")
  (-put    [this k v]
    "Promise<v> после успешной записи.")
  (-delete [this k]
    "Promise<nil>.")
  (-keys-with-prefix [this prefix]
    "Promise<[ключи...]>."))

(defprotocol Socket
  (-connect [this url handlers]
    "Promise<conn>. handlers — мапа {:on-message fn :on-close fn :on-error fn}.
     Promise резолвится когда соединение реально открыто.")
  (-send [this conn msg]
    "Синхронная отправка. Если соединение ещё не открыто — сообщение
     буферизуется и будет послано при open. Если connect был отменён
     или соединение закрыто — сообщения молча дропаются.")
  (-close [this conn]
    "Закрывает соединение. Идемпотентно."))

(defprotocol Clock
  (-now [this]
    "Текущее время в миллисекундах (number)."))
