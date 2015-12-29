(ns org.spaz.radio.media
  (:require [neko.activity :as activity :refer [ set-content-view!]]
            [neko.threading :as threading :refer [on-ui]]
            [neko.resource :as r]
            [neko.notify :as notify]
            [neko.resource :as r]
            [neko.context :as context]
            [cheshire.core :as json]
            [org.spaz.radio.utils :as utils]
            [neko.log :as log]
            [neko.ui :as ui :refer [make-ui]])
  (:import android.media.MediaPlayer
           android.media.AudioManager
           neko.App
           android.os.PowerManager
           android.net.wifi.WifiManager
           android.net.wifi.WifiInfo
           android.net.NetworkInfo
           android.media.AudioManager
           java.io.StringWriter
           java.io.PrintWriter
           android.net.wifi.SupplicantState))

(r/import-all)

(defonce mp (atom nil))

;; TODO: move to settings
(defonce datasource (atom "http://radio.spaz.org:8050/radio-low.ogg"))

(defonce last-pos (atom 0))

(defonce status (atom "Connecting...")) ;; TODO: use resources in an init function?

(declare assure-mp start clear release-lock)


(def wifi-lock (atom nil))

(defn set-lock
  []
  (try
    (.acquire
     (or @wifi-lock
         (swap! wifi-lock
                #(or %
                     (-> :wifi
                         context/get-service 
                         (.createWifiLock WifiManager/WIFI_MODE_FULL "SpazPlayerLock"))))))
    (catch Exception e
      (log/e "wifilock bug in google")
      (release-lock))))




(defn release-lock
  []
  (and @wifi-lock
       (.isHeld @wifi-lock)
       (.release @wifi-lock)))


(defn setup-ducking
  []
  (-> :audio
      context/get-service
      (.requestAudioFocus
       (reify android.media.AudioManager$OnAudioFocusChangeListener
         (onAudioFocusChange [this i]
           ((case i
              AudioManager/AUDIOFOCUS_GAIN #(.setVolume (assure-mp) 1.0 1.0)
              AudioManager/AUDIOFOCUS_LOSS clear ;;#(log/i "audio focus loss, ignoring it")
              AudioManager/AUDIOFOCUS_LOSS_TRANSIENT #(and (.isPlaying (assure-mp))
                                                           (.pause (assure-mp)))
              AudioManager/AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK #(and (.isPlaying (assure-mp))
                                                                    (.setVolume (assure-mp) 0.1 0.1))
              #(log/e "audio focus: no matching clause for " i)))))
       AudioManager/STREAM_MUSIC AudioManager/AUDIOFOCUS_GAIN)))


(defn decode-error
  [i]
  (-> {MediaPlayer/MEDIA_INFO_BUFFERING_START   (str (r/get-string R$string/buffering) "...")
       MediaPlayer/MEDIA_INFO_BUFFERING_END  (r/get-string R$string/reconnected)
       703  (r/get-string R$string/resyncing)} ;; XXX undocumented, but i get it a lot
      (get i)))



(defn start*
  [^MediaPlayer mp]
  (set-lock)
  (reset! status (str (r/get-string R$string/connecting) "..."))
  (try
    (doto mp
      .reset ;; it doesn't hurt to be sure
      (.setOnPreparedListener
       (reify android.media.MediaPlayer$OnPreparedListener
         (onPrepared [this mp]
           (log/i "prepared" (.getCurrentPosition mp))
           (reset! status (r/get-string R$string/connected))
           (.setVolume mp 1.0 1.0)
           (setup-ducking)
           (.start mp))))

      (.setOnBufferingUpdateListener 
       (reify android.media.MediaPlayer$OnBufferingUpdateListener
         (onBufferingUpdate [this mp percent]
           (reset! last-pos (.getCurrentPosition mp))
           #_(log/d "buffered" percent "% and pos" (.getCurrentPosition mp)))))
      
      (.setOnCompletionListener 
       (reify android.media.MediaPlayer$OnCompletionListener
         (onCompletion [this mp]
           (log/i "lost connection" (.getCurrentPosition mp))
           (clear)
           (reset! status (str (r/get-string R$string/disconnected) "..."))
           (start))))
      
      (.setOnSeekCompleteListener 
       (reify android.media.MediaPlayer$OnSeekCompleteListener
         (onSeekComplete [this mp]
           ;;(reset! status "Done seeking")
           (log/i "seek complete" (.getCurrentPosition mp))
           )))

      (.setOnErrorListener 
       (reify android.media.MediaPlayer$OnErrorListener
         (onError [this mp what extra]
           (log/i "error" what extra (.getCurrentPosition mp))
           (.reset mp) ;; must do this, otherwise nothing else will work.
           false))) ;; grab the error and hold on to it

      (.setOnInfoListener
       (reify android.media.MediaPlayer$OnInfoListener
         (onInfo [this mp what extra]
           (let [m (decode-error what)]
             (reset! status m)
             (log/i "info" m what extra (.getCurrentPosition mp)))
           false))) ;; let others handle it

      (.setAudioStreamType AudioManager/STREAM_MUSIC)
      (.setWakeMode App/instance   PowerManager/PARTIAL_WAKE_LOCK)
      (.setDataSource  @datasource)
      .prepareAsync)

    
    (catch Exception e
      (log/e "error in start*")
      (.printStackTrace e)
      ;; very important! reaase the kraken!
      (clear))))


(defn start
  "Returns the mp. Will reset it if it doesn't exist"
  []
  (or @mp (swap! mp (fn [mp] (or mp
                                 (-> (MediaPlayer.)
                                   start*))))))




(defn clear []
  (when @mp
    (release-lock)
    (.release @mp)
    (reset! mp nil)))


(comment

  (start)

  (-> (MediaPlayer.)
    start*)

  (clear)

  (reset! datasource (str "http://" utils/fake-server ":8000/stream"))
  (reset! datasource (str "http://" utils/fake-server "/test.ogg"))

  (add-watch status ::mon (fn [k r old new] (log/i old "-->" new))) 
  
  )
