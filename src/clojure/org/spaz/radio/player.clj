(ns org.spaz.radio.player
  (:require [neko.activity :as activity :refer [defactivity set-content-view!]]
            [neko.threading :as threading :refer [on-ui]]
            [neko.notify :as notify]
            [net.clandroid.service :as services]
            [neko.resource :as r]
            [neko.context :as context]
            [neko.find-view :as view]
            [neko.listeners.view :as lview]
            [neko.data :as data]
            [neko.doc :as doc]
            [neko.debug :as debug]
            [org.spaz.radio.playing :as playing]
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


(def playing-layout* [:linear-layout {:orientation :vertical,
                                      :id-holder true,
                                      :def `playing-layout}
                      [:text-view {:text "Now Playing:"}]
                      [:text-view {:text "checking..."
                                   :id ::playing-text}]
                      [:text-view {:text ""
                                   :id  ::status-text}]
                      [:button {:text "Configuring"
                                :id ::playing-button}]])


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
               ;; TODO: add-watch for schedule too
               (future (playing/playing!))
               ;; it'll only get started once, so no need to check here
               (swap! utils/needs-alarm conj ::player)
               (services/start-service-unbound this utils/alarm-service-name))
  
  :on-stop (fn [this]
             (remove-watch playing/last-playing ::player)
             ;; TODO: remove-watch for schedule too
             (swap! utils/needs-alarm disj ::player)
             (->> [:broadcast utils/end-alarm-signal]
                  notify/construct-pending-intent
                  .send ;; have to use send not sendbroadcast?
                  on-ui))
  
  :on-resume (fn [this]
               (swap! utils/needs-alarm conj ::player)
               (add-watch playing/last-playing ::player refresh-playing)
               ;; TODO: add-watch for schedule too
               (future (playing/playing!))))




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



  )



