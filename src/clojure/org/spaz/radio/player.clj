(ns org.spaz.radio.player
  (:require [neko.activity :as activity :refer [defactivity set-content-view!]]
            [neko.threading :as threading :refer [on-ui]]
            [neko.notify :as notify]
            [net.clandroid.service :as services]
            [neko.resource :as r]
            [neko.context :as context]
            [utilza.misc :as utilza]
            [neko.find-view :as view]
            [neko.listeners.view :as lview]
            [neko.ui.adapters :as adapters]
            [neko.data :as data]
            [neko.doc :as doc]
            [neko.debug :as debug]
            [org.spaz.radio.playing :as playing]
            [org.spaz.radio.schedule :as schedule]
            [org.spaz.radio.utils :as utils]
            [org.spaz.radio.service :as service]
            [neko.log :as log]
            [neko.ui :as ui :refer [make-ui]])
  (:import android.media.MediaPlayer
           android.content.ComponentName
           android.content.Intent))



(declare ^android.widget.LinearLayout playing-layout)

(declare stop-player) ;; because start-player needs it.



(defn get-view
  [k]
  (->> playing-layout
       .getTag
       k))

(defn set-text!
  [k s]
  {:pre (keyword? k)}
  (on-ui
   (doto (get-view k)
     (.setText s)
     utils/force-top-level-redraw)))



(def set-playing!
  (partial set-text! ::playing-text))


(defn start-player
  [^android.app.Activity this ^android.view.View v]
  (doto v
    (.setText "Stop")
    utils/force-top-level-redraw
    (.setOnClickListener (lview/on-click-call (partial stop-player this))))
  (services/start-service-unbound this utils/player-service-name))



(defn stop-player
  [^android.app.Activity this ^android.view.View v]
  (doto v
    (.setText "Listen")
    utils/force-top-level-redraw
    (.setOnClickListener (lview/on-click-call (partial start-player this))))
  (->> [:broadcast utils/end-service-signal]
       notify/construct-pending-intent
       .send ;; have to use send not sendbroadcast?
       on-ui))



(defn refresh-playing
  [k r old new]
  (future
    (when-not (= new (->> ::playing-text
                          get-view
                          .getText))
      (log/d "updating activity view" new)
      (set-playing! new))))


(def display-colfixes {:start_timestamp schedule/output-datetime-format
                       :end_timestamp schedule/output-datetime-format})

(defn format-show
  [show]
  (let [{:keys [name url start_timestamp end_timestamp]} (utilza/munge-columns display-colfixes show)]
    ;; TODO: return some kind of map maybe?
    ;; TODO: maybe if there's an url, make the show clickable
    (format "%s\n%s -\n%s" name start_timestamp end_timestamp)))



(defn schedule-adapter []
  (adapters/ref-adapter
   (fn [] [:text-view {}])
   (fn [_ view _ show]
     (on-ui
      (.setText view (format-show show)))) ;; TODO: typehint
   schedule/schedule
   :future))

(def playing-layout* [:linear-layout {:orientation :vertical,
                                      :id-holder true,
                                      :def `playing-layout}
                      [:text-view {:text "Now Playing:"}]
                      [:text-view {:text "checking..."
                                   :id ::playing-text}]
                      [:text-view {:text ""
                                   :id  ::status-text}]
                      [:button {:text "Configuring"
                                :id ::playing-button}]
                      [:text-view {:text "Upcoming Shows"
                                   :id ::upcoming-header}]
                      [:list-view {:id ::schedule}]])


(defn set-playing-button
  [this]
  (let [pb (get-view ::playing-button)] 
    (if (service/started?)
      (doto pb
        (.setOnClickListener (lview/on-click-call (partial stop-player this)))
        (.setText "Stop"))
      (doto pb
        (.setOnClickListener (lview/on-click-call (partial start-player this)))
        (.setText "Listen")))))



(defactivity org.spaz.radio.Player
  :state (atom {})
  
  :on-create (fn [this bundle]
               (on-ui
                (->> playing-layout*
                     make-ui
                     (set-content-view! this))
                ;; XXX hack. figure out how to do it with layoutparams instead?
                (->  ::playing-text
                     get-view
                     (.setSingleLine true))
                (set-playing-button this))
               (add-watch playing/last-playing ::player refresh-playing)
               (-> ::schedule
                   get-view
                   (ui/config :adapter (schedule-adapter)))
               (future (playing/playing!))
               (future (schedule/update-schedule!))
               ;; it'll only get started once, so no need to check here
               (swap! utils/needs-alarm conj ::player)
               (services/start-service-unbound this utils/alarm-service-name))
  
  :on-stop (fn [this]
             (remove-watch playing/last-playing ::player)
             (swap! utils/needs-alarm disj ::player)
             (->> [:broadcast utils/end-alarm-signal]
                  notify/construct-pending-intent
                  .send ;; have to use send not sendbroadcast?
                  on-ui))
  
  :on-resume (fn [this]
               (swap! utils/needs-alarm conj ::player)
               (add-watch playing/last-playing ::player refresh-playing)
               (future (playing/playing!))
               (future (schedule/update-schedule!))))




(comment

  ;; TO unit tests or functional tests:
  
  ;; to click on the play button
  (debug/safe-for-ui
   (->  ::playing-button
        get-view
        .performClick
        on-ui))


  )

(comment

  (->> @schedule/schedule
       :future
       first
       format-show)
  
  (debug/safe-for-ui
   (on-ui
    (-> ::schedule
        get-view
        (ui/config :adapter (schedule-adapter)))))

  
  )

(comment



  )



