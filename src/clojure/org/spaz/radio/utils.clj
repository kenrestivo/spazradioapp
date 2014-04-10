(ns org.spaz.radio.utils
  (:require [neko.activity :as activity :refer [defactivity set-content-view!]]
            [neko.threading :as threading :refer [on-ui]]
            [neko.context :as context]
            [neko.find-view :as view]
            [neko.notify :as notify]
            [utilza.android :as utilza]
            [net.clandroid.service :as service]
            [neko.resource :as r]
            [neko.log :as log]
            [neko.ui :as ui :refer [make-ui]])
  (:import android.media.MediaPlayer
           android.content.ComponentName
           android.view.View
           android.content.pm.PackageInfo
           android.content.Intent))

(defonce ^:const package-name "org.spaz.radio")
(defonce ^:const end-service-signal "END_SPAZ_PLAYER_SERVICE")
(defonce ^:const end-alarm-signal "END_SPAZ_PLAYER_ALARM")
(defonce ^:const main-activity-signal "org.spaz.radio.MAIN")
(defonce ^:const alarm-service-name "org.spaz.radio.AlarmService")
(defonce ^:const player-service-name "org.spaz.radio.PlayerService")

;; THIS CANNOT BE A RESOURCE!
(defonce ^:const playing-service-id 42) 


;; this is here only because it has to be
(defonce needs-alarm (atom #{}))

(defn log-and-toast
  [& msgs]
  (try 
    (let [msg  (->> msgs (interpose " ") (apply str))]
      (log/i msg)
      (on-ui
       (notify/toast msg)))
    (catch Exception e
      nil)))



;; TODO: all the flags!
;;  PendingIntent.FLAG_UPDATE_CURRENT);
;;    how the hell? just blow off notify/notification?
;; notification.flags |= Notification.FLAG_ONGOING_EVENT;
;;    try after the fact?

(defn notification
  [^android.content.Context context text]
  (notify/notification :icon (r/get-resource context :drawable :ic_launcher)
                       :content-title "SPAZ Radio"
                       :content-text text
                       ;; TODO: must also Intent.FLAG_ACTIVITY_NEW_TASK somehow
                       :action [:activity main-activity-signal]))


(defn renotify
  [^android.content.Context context text]
  ;; MUST use resource here since start-foreground requires an id
  ;; can't use the neko notification id atom becasue it's private :-/
  (notify/fire playing-service-id (notification context text)))





(defn force-top-level-redraw
  "XXX miserable hack. do not know why this is necessary?"
  [^android.view.View v]
  (.invalidate v)
  (-> v
      .getParent
      .getParent
      .getParent
      .getParent
      .invalidate))

(comment



  (utilza/get-version-info package-name)


    ;; for mocking

  (def fake-server "192.168.0.46")
  (def fake-server "192.168.43.169")

  

  )