(ns org.spaz.radio.alarms
  (:require  [org.spaz.radio.player :as player]
             [org.spaz.radio.playing :as playing]
             [net.clandroid.service :as service]
             [org.spaz.radio.utils :as utils]
             [neko.log :as log]
             [utilza.android :as utilza]
             [org.spaz.radio.schedule :as schedule])

  )


(def refresh-interval 30000)


(defonce alarm-handler (atom nil))



(defn cancel-alarm
  []
  (swap! alarm-handler (fn [h] (and h (.quit h)) nil)))


(defn update-periodic-items!
  []
  (playing/playing!)
  (schedule/update-schedule!))

(defn assure-handler
  [handler]
  (if (and handler (.isAlive handler))
    handler
    (utilza/periodic update-periodic-items! refresh-interval "Alarm")))





(service/defservice org.spaz.radio.AlarmService
  :def alarm-service
  :on-create (fn [^android.app.Service this]
               (log/i "alarm created!")
               (swap! alarm-handler assure-handler)
               (service/start-receiver this utils/end-alarm-signal
                                       (fn [context intent]
                                         (when (empty? @utils/needs-alarm)
                                           (cancel-alarm)
                                           (.stopForeground this true)
                                           (try
                                             (.stopSelf this)
                                             (catch Exception e (log/e e)))))))
  :on-destroy (fn [^android.app.Service this]
                (log/i "alarm destroyed")
                (cancel-alarm)
                (service/stop-receiver this utils/end-alarm-signal)))





(comment

  (.isAlive @alarm-handler)

  
  )
