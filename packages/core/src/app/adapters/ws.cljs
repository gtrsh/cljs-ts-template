(ns app.adapters.ws
  "Адаптер Socket поверх нативного WebSocket. Буферизует send до open,
   конвертит JSON на лету на границе."
  (:require [app.ports :as p]
            [promesa.core :as pr]))

(defn- encode [msg]
  (js/JSON.stringify (clj->js msg)))

(defn- decode [s]
  (try
    (js->clj (js/JSON.parse s) :keywordize-keys true)
    (catch :default _
      {:type :decode-error :raw s})))

(defn- flush-buffer! [{:keys [ws buffer]}]
  (doseq [msg @buffer]
    (.send ws msg))
  (reset! buffer []))

(defrecord BrowserSocket []
  p/Socket
  (-connect [_ url {:keys [on-message on-close on-error]}]
    (pr/create
      (fn [resolve reject]
        (let [ws   (js/WebSocket. url)
              conn {:ws     ws
                    :buffer (atom [])
                    :open?  (atom false)}]
          (set! (.-onopen ws)
                (fn [_]
                  (reset! (:open? conn) true)
                  (flush-buffer! conn)
                  (resolve conn)))
          (set! (.-onmessage ws)
                (fn [event]
                  (when on-message
                    (on-message (decode (.-data event))))))
          (set! (.-onclose ws)
                (fn [event]
                  (reset! (:open? conn) false)
                  (when on-close
                    (on-close {:code   (.-code event)
                               :reason (.-reason event)
                               :clean? (.-wasClean event)}))))
          (set! (.-onerror ws)
                (fn [event]
                  (if @(:open? conn)
                    (when on-error (on-error event))
                    (reject (ex-info "WebSocket failed to open"
                                     {:url url})))))))))

  (-send [_ conn msg]
    (let [encoded (encode msg)]
      (if @(:open? conn)
        (.send (:ws conn) encoded)
        (swap! (:buffer conn) conj encoded))))

  (-close [_ conn]
    (reset! (:open? conn) false)
    (.close (:ws conn))))

(defn make [] (->BrowserSocket))
