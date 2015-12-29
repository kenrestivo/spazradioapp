(ns org.spaz.radio.service
  (:require [neko.activity :as activity :refer [ set-content-view!]]
            [neko.threading :as threading :refer [on-ui]]
            [neko.notify :as notify]
            [net.clandroid.service :as service]
            [org.spaz.radio.media :as media]
            [org.spaz.radio.playing :as playing]
            [org.spaz.radio.utils :as utils]
            [neko.data :as data]
            [neko.context :as context]
            [neko.resource :as r]
            [neko.log :as log]
            [neko.ui :as ui :refer [make-ui]])
  (:import android.media.MediaPlayer
           android.net.wifi.WifiManager
           android.net.wifi.WifiInfo
           android.net.NetworkInfo
           android.media.AudioManager
           android.net.wifi.SupplicantState))






(defonce status (atom :stopped))

(defn started?
  []
  (= @status :started))


(service/defservice org.spaz.radio.PlayerService
  :def player-service
  :on-start-command (fn [^android.app.Service this intent flags start-id]
                      (log/i "player service created!")
                      (when-not (= @status :started)
                        (service/start-foreground this  utils/playing-service-id
                                                  (utils/notification this @playing/last-playing))
                        (reset! status :started)
                        (add-watch playing/last-playing
                                   ::notification
                                   (fn [k r old new]
                                     (when (not= old new)
                                       ;; XXX bug if notification is old?
                                       (future (utils/renotify this new)))))
                        (swap! utils/needs-alarm conj ::service)
                        (service/start-receiver this utils/end-service-signal
                                                (fn [context intent]
                                                  (.stopForeground this true)
                                                  (media/clear)
                                                  (try
                                                    (.stopSelf this)
                                                    (catch Exception e (log/e e)))))
                        (future (media/start))))
  :on-destroy (fn [^android.app.Service this]
                (log/i "player service destroyed")
                (.stopForeground this true) ;; redundant?
                (reset! status :stopped)
                (remove-watch playing/last-playing ::notification)
                (swap! utils/needs-alarm disj ::service)
                (->> [:broadcast utils/end-alarm-signal]
                     (notify/construct-pending-intent this)
                     .send) ;; have to use send not sendbroadcast?
                (media/clear)
                (service/stop-receiver this utils/end-service-signal))) 


