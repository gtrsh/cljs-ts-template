(ns app.domain)

;; --- доменная модель kanban-доски ---
;;
;; Board:
;;   {:columns      {column-id {:id ... :title ... :wip-limit ... :card-ids [...]}}
;;    :column-order [column-id ...]
;;    :cards        {card-id {:id ... :title ... :created-at ... :column-id ...}}}
;;
;; Пока всё пусто; логика появится на шаге 2.

(def empty-board
  {:columns      {}
   :column-order []
   :cards        {}})

(defn demo-board
  "Образец доски для первой проверки, что ядро реально гоняет данные до view."
  []
  {:columns      {"todo"  {:id "todo"  :title "To Do"  :wip-limit 5   :card-ids ["c1"]}
                  "doing" {:id "doing" :title "Doing"  :wip-limit 3   :card-ids ["c2"]}
                  "done"  {:id "done"  :title "Done"   :wip-limit nil :card-ids []}}
   :column-order ["todo" "doing" "done"]
   :cards        {"c1" {:id "c1" :title "Починить баг" :created-at 0 :column-id "todo"}
                  "c2" {:id "c2" :title "Написать тест" :created-at 0 :column-id "doing"}}})
