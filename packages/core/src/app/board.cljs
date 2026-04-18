(ns app.board
  "Чистые функции над моделью kanban-доски. Pure, side-effect-free.
   Возвращают либо новый board (happy path), либо {:error kw :details...}."
  (:require [clojure.string :as str]))

;; --- вспомогательные функции над вектором id'ов ---

(defn- remove-id [xs id]
  (vec (remove #(= % id) xs)))

(defn- insert-at
  "Вставляет id в xs на позицию idx. nil idx или idx > (count xs) → в конец.
   Отрицательные idx клампятся к 0."
  [xs idx id]
  (let [n (count xs)
        i (cond
            (nil? idx)   n
            (> idx n)    n
            (neg? idx)   0
            :else        idx)]
    (vec (concat (take i xs) [id] (drop i xs)))))

;; --- предикаты результата ---

(defn error? [result]
  (contains? result :error))

(defn ok? [result]
  (not (error? result)))

;; --- операции над карточками ---

(defn add-card
  "Добавляет карточку в колонку. Карточка должна прийти со всеми полями:
   :id, :title, :column-id, :created-at. Генерация id и времени —
   ответственность сервисного слоя (шаг 4)."
  [board {:keys [id title column-id created-at]}]
  (let [column (get-in board [:columns column-id])]
    (cond
      (nil? column)
      {:error :column-not-found :column-id column-id}

      (contains? (:cards board) id)
      {:error :card-exists :card-id id}

      (and (:wip-limit column)
           (>= (count (:card-ids column)) (:wip-limit column)))
      {:error :wip-exceeded :column-id column-id :limit (:wip-limit column)}

      :else
      (-> board
          (assoc-in  [:cards id]
                     {:id id :title title :created-at created-at :column-id column-id})
          (update-in [:columns column-id :card-ids] conj id)))))

(defn remove-card
  [board card-id]
  (if-let [card (get-in board [:cards card-id])]
    (-> board
        (update :cards dissoc card-id)
        (update-in [:columns (:column-id card) :card-ids] remove-id card-id))
    {:error :card-not-found :card-id card-id}))

(defn move-card
  "Перемещает карточку в target-column-id на позицию target-index.
   target-index = nil → в конец колонки.
   Если target-column-id совпадает с текущей колонкой — это реордер,
   WIP-лимит не проверяется."
  [board {:keys [card-id target-column-id target-index]}]
  (let [card          (get-in board [:cards card-id])
        target-column (get-in board [:columns target-column-id])]
    (cond
      (nil? card)
      {:error :card-not-found :card-id card-id}

      (nil? target-column)
      {:error :column-not-found :column-id target-column-id}

      (and (not= (:column-id card) target-column-id)
           (:wip-limit target-column)
           (>= (count (:card-ids target-column)) (:wip-limit target-column)))
      {:error :wip-exceeded :column-id target-column-id :limit (:wip-limit target-column)}

      :else
      (let [source-id (:column-id card)]
        (-> board
            (update-in [:columns source-id        :card-ids] remove-id card-id)
            (update-in [:columns target-column-id :card-ids] insert-at target-index card-id)
            (assoc-in  [:cards card-id :column-id] target-column-id))))))

(defn rename-card
  [board card-id new-title]
  (cond
    (nil? (get-in board [:cards card-id]))
    {:error :card-not-found :card-id card-id}

    (or (not (string? new-title))
        (-> new-title str/trim empty?))
    {:error :invalid-title}

    :else
    (assoc-in board [:cards card-id :title] (str/trim new-title))))

;; --- операции над колонками ---

(defn reorder-columns
  [board {:keys [column-id target-index]}]
  (if (contains? (:columns board) column-id)
    (assoc board :column-order
           (insert-at (remove-id (:column-order board) column-id) target-index column-id))
    {:error :column-not-found :column-id column-id}))

(defn set-wip-limit
  "Устанавливает/снимает WIP-лимит колонки. nil → без лимита."
  [board column-id limit]
  (cond
    (nil? (get-in board [:columns column-id]))
    {:error :column-not-found :column-id column-id}

    (and (some? limit) (or (not (int? limit)) (neg? limit)))
    {:error :invalid-limit :limit limit}

    :else
    (assoc-in board [:columns column-id :wip-limit] limit)))
