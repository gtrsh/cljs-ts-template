(ns app.board-test
  (:require [cljs.test :refer [deftest testing is]]
            [app.board :as board]))

;; --- фикстуры ---

(defn fresh-board []
  {:columns      {"todo"  {:id "todo"  :title "To Do"  :wip-limit 3   :card-ids []}
                  "doing" {:id "doing" :title "Doing"  :wip-limit 2   :card-ids []}
                  "done"  {:id "done"  :title "Done"   :wip-limit nil :card-ids []}}
   :column-order ["todo" "doing" "done"]
   :cards        {}})

(defn card [id column-id]
  {:id id :title (str "card-" id) :column-id column-id :created-at 0})

;; --- add-card ---

(deftest add-card-happy-path
  (let [b (board/add-card (fresh-board) (card "c1" "todo"))]
    (is (board/ok? b))
    (is (= ["c1"] (get-in b [:columns "todo" :card-ids])))
    (is (= "todo" (get-in b [:cards "c1" :column-id])))))

(deftest add-card-unknown-column-fails
  (let [r (board/add-card (fresh-board) (card "c1" "ghost"))]
    (is (board/error? r))
    (is (= :column-not-found (:error r)))))

(deftest add-card-duplicate-id-fails
  (let [b1 (board/add-card (fresh-board) (card "c1" "todo"))
        r  (board/add-card b1 (card "c1" "doing"))]
    (is (= :card-exists (:error r)))))

(deftest add-card-wip-exceeded
  (let [b (-> (fresh-board)
              (board/add-card (card "c1" "doing"))
              (board/add-card (card "c2" "doing")))
        r (board/add-card b (card "c3" "doing"))]
    (is (= :wip-exceeded (:error r)))
    (is (= 2 (:limit r)))
    (is (= "doing" (:column-id r)))))

(deftest add-card-ignores-wip-when-nil
  (testing "колонка done без лимита принимает сколько угодно"
    (let [b (reduce (fn [b i] (board/add-card b (card (str "c" i) "done")))
                    (fresh-board)
                    (range 10))]
      (is (board/ok? b))
      (is (= 10 (count (get-in b [:columns "done" :card-ids])))))))

;; --- remove-card ---

(deftest remove-card-happy-path
  (let [b1 (board/add-card (fresh-board) (card "c1" "todo"))
        b2 (board/remove-card b1 "c1")]
    (is (board/ok? b2))
    (is (= [] (get-in b2 [:columns "todo" :card-ids])))
    (is (nil? (get-in b2 [:cards "c1"])))))

(deftest remove-card-not-found
  (is (= :card-not-found
         (:error (board/remove-card (fresh-board) "ghost")))))

;; --- move-card: межколоночное ---

(deftest move-card-across-columns
  (let [b (board/add-card (fresh-board) (card "c1" "todo"))
        r (board/move-card b {:card-id "c1" :target-column-id "doing" :target-index nil})]
    (is (board/ok? r))
    (is (= []       (get-in r [:columns "todo"  :card-ids])))
    (is (= ["c1"]   (get-in r [:columns "doing" :card-ids])))
    (is (= "doing"  (get-in r [:cards "c1" :column-id]))
        "денормализованный column-id в карточке тоже обновляется")))

(deftest move-card-to-specific-index
  (let [b (-> (fresh-board)
              (board/add-card (card "a" "done"))
              (board/add-card (card "b" "done"))
              (board/add-card (card "src" "todo")))
        r (board/move-card b {:card-id "src" :target-column-id "done" :target-index 1})]
    (is (= ["a" "src" "b"] (get-in r [:columns "done" :card-ids])))))

(deftest move-card-index-overflow-clamps-to-end
  (let [b (-> (fresh-board)
              (board/add-card (card "a" "done"))
              (board/add-card (card "src" "todo")))
        r (board/move-card b {:card-id "src" :target-column-id "done" :target-index 99})]
    (is (= ["a" "src"] (get-in r [:columns "done" :card-ids])))))

(deftest move-card-across-columns-respects-wip
  (let [b (-> (fresh-board)
              (board/add-card (card "c1" "doing"))
              (board/add-card (card "c2" "doing"))
              (board/add-card (card "c3" "todo")))
        r (board/move-card b {:card-id "c3" :target-column-id "doing" :target-index nil})]
    (is (= :wip-exceeded (:error r)))
    (is (= "doing" (:column-id r)))))

;; --- move-card: внутриколоночный реордер ---

(deftest move-card-reorder-within-column
  (let [b (-> (fresh-board)
              (board/add-card (card "a" "todo"))
              (board/add-card (card "b" "todo"))
              (board/add-card (card "c" "todo")))
        r (board/move-card b {:card-id "c" :target-column-id "todo" :target-index 0})]
    (is (board/ok? r))
    (is (= ["c" "a" "b"] (get-in r [:columns "todo" :card-ids])))))

(deftest move-card-reorder-ignores-wip
  (testing "колонка doing на пределе (2/2), реордер внутри не должен падать"
    (let [b (-> (fresh-board)
                (board/add-card (card "c1" "doing"))
                (board/add-card (card "c2" "doing")))
          r (board/move-card b {:card-id "c2" :target-column-id "doing" :target-index 0})]
      (is (board/ok? r))
      (is (= ["c2" "c1"] (get-in r [:columns "doing" :card-ids]))))))

(deftest move-card-unknown-column
  (let [b (board/add-card (fresh-board) (card "c1" "todo"))
        r (board/move-card b {:card-id "c1" :target-column-id "ghost" :target-index 0})]
    (is (= :column-not-found (:error r)))))

;; --- rename-card ---

(deftest rename-card-happy-path
  (let [b1 (board/add-card (fresh-board) (card "c1" "todo"))
        b2 (board/rename-card b1 "c1" "Новое название")]
    (is (= "Новое название" (get-in b2 [:cards "c1" :title])))))

(deftest rename-card-trims-whitespace
  (let [b1 (board/add-card (fresh-board) (card "c1" "todo"))
        b2 (board/rename-card b1 "c1" "  spaced  ")]
    (is (= "spaced" (get-in b2 [:cards "c1" :title])))))

(deftest rename-card-rejects-blank
  (let [b1 (board/add-card (fresh-board) (card "c1" "todo"))]
    (is (= :invalid-title (:error (board/rename-card b1 "c1" ""))))
    (is (= :invalid-title (:error (board/rename-card b1 "c1" "   "))))))

;; --- reorder-columns ---

(deftest reorder-columns-happy-path
  (let [r (board/reorder-columns (fresh-board) {:column-id "done" :target-index 0})]
    (is (= ["done" "todo" "doing"] (:column-order r)))))

(deftest reorder-columns-preserves-other-columns
  (let [r (board/reorder-columns (fresh-board) {:column-id "doing" :target-index 2})]
    (is (= ["todo" "done" "doing"] (:column-order r)))))

;; --- set-wip-limit ---

(deftest set-wip-limit-happy-path
  (let [r (board/set-wip-limit (fresh-board) "todo" 10)]
    (is (= 10 (get-in r [:columns "todo" :wip-limit])))))

(deftest set-wip-limit-nil-removes-limit
  (let [r (board/set-wip-limit (fresh-board) "todo" nil)]
    (is (nil? (get-in r [:columns "todo" :wip-limit])))))

(deftest set-wip-limit-rejects-negative
  (is (= :invalid-limit (:error (board/set-wip-limit (fresh-board) "todo" -1)))))

;; --- интеграция: сценарий "перетащил и переименовал" ---

(deftest integration-scenario
  (let [b (-> (fresh-board)
              (board/add-card    (card "c1" "todo"))
              (board/add-card    (card "c2" "todo"))
              (board/move-card   {:card-id "c1" :target-column-id "doing" :target-index nil})
              (board/rename-card "c1" "Задача в работе"))]
    (is (board/ok? b))
    (is (= ["c2"]   (get-in b [:columns "todo"  :card-ids])))
    (is (= ["c1"]   (get-in b [:columns "doing" :card-ids])))
    (is (= "doing"  (get-in b [:cards "c1" :column-id])))
    (is (= "Задача в работе" (get-in b [:cards "c1" :title])))))
