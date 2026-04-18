(ns app.hello)

(defn say-hello
  "Простое приветствие."
  [name]
  (str "Привет, " name "!"))

(defn compute-stats
  "Принимает JS-массив чисел, возвращает JS-объект со статистикой.
   Демонстрирует границу JS<->CLJS: вход JS, обработка на идиоматичном
   CLJS, выход JS."
  [^js js-numbers]
  (let [nums (js->clj js-numbers)]
    (if (empty? nums)
      #js {:count 0 :sum 0 :avg nil :min nil :max nil}
      (let [sum (reduce + nums)]
        #js {:count (count nums)
             :sum   sum
             :avg   (/ sum (count nums))
             :min   (apply min nums)
             :max   (apply max nums)}))))
