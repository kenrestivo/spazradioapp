(ns org.spaz.radio.player
  (:require [neko.resource :as r]
            [org.spaz.radio.utils :as utils]
            [neko.ui.menu :as menu]
            [neko.activity :as activity :refer [defactivity set-content-view!]]
            [utilza.android :as utildroid]
            [org.spaz.radio.service :as service]
            [org.spaz.radio.alarms :as alarm] ;; needed in order to get alarm class to resolve?
            [neko.ui.adapters :as adapters]
            [org.spaz.radio.playing :as playing]
            [neko.ui :as ui :refer [make-ui]]
            [neko.find-view :as view]
            [org.spaz.radio.media :as media]
            [neko.dialog.alert :as dialog]
            [utilza.misc :as utilza]
            [neko.debug :as debug]
            [org.spaz.radio.schedule :as schedule]
            [neko.log :as log]
            [net.clandroid.service :as services]
            [neko.threading :as threading :refer [on-ui]]
            [neko.listeners.view :as lview]
            [neko.notify :as notify]
            )
  (:import android.graphics.Color
           android.net.Uri
           android.view.View
           android.app.Activity
           de.schildbach.wallet.integration.android.BitcoinIntegration
           android.content.Intent
           ))

(r/import-all)

(declare stop-player) ;; because start-player needs it.

(defn safe-url
  "Copy/pasted from utilza"
  [^String s]
  (let [u (Uri/parse s)]
    (if (.getScheme u)
      u
      (recur (str "http://" s)))))



(defn set-text!
  [this k s]
  {:pre (keyword? k)}
  (on-ui
   (doto (view/find-view this k)
     (.setText (str s))
     utils/force-top-level-redraw)))



(defn start-player
  [^android.app.Activity this ^android.view.View v]
  (doto v
    (.setText (r/get-string R$string/stop_button))
    utils/force-top-level-redraw
    (.setOnClickListener (lview/on-click-call (partial stop-player this))))
  (set-text! this ::status-text (str (r/get-string R$string/connecting) "..."))
  (services/start-service-unbound this utils/player-service-name))



(defn stop-player
  [^android.app.Activity this ^android.view.View v]
  (doto v
    (.setText (r/get-string R$string/listen_button))
    utils/force-top-level-redraw
    (.setOnClickListener (lview/on-click-call (partial start-player this))))
  (set-text! this ::status-text "")
  (->> [:broadcast utils/end-service-signal]
       (notify/construct-pending-intent this)
       .send ;; have to use send not sendbroadcast?
       on-ui))



(defn refresh-playing
  [this k r old new]
  (future
    (when-not (= new (->> ::playing-text
                          (view/find-view this)
                          .getText))
      (log/d "updating activity view" new)
      (set-text! this ::playing-text new))))


(defn refresh-status
  [this k r old new]
  (future
    (when-not (= new (->> ::status-text
                          (view/find-view this)
                          .getText))
      (log/d "updating activity view" new)
      (set-text! this ::status-text new))))


(def display-colfixes {:start_timestamp schedule/output-datetime-format
                       :end_timestamp schedule/output-datetime-format})

(defn format-show
  [show]
  {:pre [(-> show nil? not)]}
  (let [{:keys [name url start_timestamp end_timestamp]} (utilza/munge-columns display-colfixes show)]
    {:name name
     :dates (format "%s -\n%s" start_timestamp end_timestamp)}))





;; TODO: this really wants to be a custom horiz listview with the dates on the left
(def show-layout-tree
  [:linear-layout {:id ::show-layout
                   :id-holder true
                   :orientation :vertical}
   [:text-view {:id ::show-name
                :text-color Color/YELLOW}]
   [:text-view {:id ::show-dates
                :text-color Color/WHITE}]])


(defn show-dialog
  [ctx show]
  (log/d "show dialog" show))

(defn schedule-adapter 
  [activity]
  (adapters/ref-adapter
   (fn [ctx] (make-ui ctx show-layout-tree))
   (fn [_ view _ {:keys [url] :as show}]
     (let [{:keys [name dates]} (format-show show)]
       (debug/safe-for-ui
        (on-ui
         (.setOnClickListener ^android.widget.TextView (-> view .getTag  ::show-name)
                              (lview/on-click  (when-not (empty? url)
                                                 (show-dialog activity show))))
         (.setText ^android.widget.TextView (-> view .getTag  ::show-name) name)
         (.setText ^android.widget.TextView (-> view .getTag  ::show-dates) dates)))))
   schedule/schedule
   #(or (:future %) [])))


(defn playing-layout 
  []
  [:linear-layout {:orientation :vertical,
                   :id ::playing-layout}
   [:text-view {:id ::now-playing-text
                :text (str (r/get-string R$string/now_playing) ": ")}]
   [:text-view {:id ::playing-text
                :text (str (r/get-string R$string/checking) "...")
                :horizontally-scrolling false}]
   [:linear-layout {:orientation :horizontal}
    [:button {:id ::playing-button
              :text (r/get-string R$string/configuring)}]
    [:text-view {:text ""
                 :id  ::status-text}]]
   [:text-view {:id ::upcoming-header
                :text (r/get-string R$string/upcoming_shows)}]
   [:list-view {:id ::schedule}]])


(defn set-playing-button
  [^Activity this]
  (when-let [pb (view/find-view this ::playing-button)] 
    (if (service/started?)
      (doto pb
        (.setOnClickListener (lview/on-click-call (partial stop-player this)))
        (.setText (r/get-string R$string/stop_button)))
      (doto pb
        (.setOnClickListener (lview/on-click-call (partial start-player this)))
        (.setText (r/get-string R$string/listen_button))))))


(defn donate-btc
  [ctx]
  (BitcoinIntegration/request ctx (rand-nth utils/btc-donation-addresses)))


(defn version-str
  [ctx]
  (let [{:keys [version-name version-number]} (utildroid/get-version-info (.getPackageName ctx) ctx)]
    (format "%s: %s, %s: %d" 
            (r/get-string R$string/version)
            version-name
            (r/get-string R$string/build)
            version-number)))

(defn about-dialog
  [ctx]
  (-> 
   (dialog/alert-dialog-builder 
    ctx
    {:negative-text (r/get-string R$string/cancel_button)
     :negative-callback (fn [dialog _] (.cancel dialog))
     :positive-text (r/get-string R$string/donate_button)
     :positive-callback (fn [_ __]
                          (donate-btc ctx))
     :message (str (version-str ctx) "\n\n"
                   (r/get-string R$string/developer_donation_beg))})
   .create
   .show))


(defactivity org.spaz.radio.Player
  :key ::player
  (onCreate [this bundle]
    (.superOnCreate this bundle)
    (on-ui
     (set-content-view! this (playing-layout))
     (set-playing-button this))
    (add-watch playing/last-playing ::player (partial refresh-playing this))
    (add-watch media/status ::player (partial refresh-status this))
    (future (playing/playing!))
    (future
      (schedule/update-schedule!)
      (on-ui
       (-> this 
           (view/find-view ::schedule)
           (ui/config :adapter (schedule-adapter this)))))
    ;; it'll only get started once, so no need to check here
    (services/start-service-unbound this utils/alarm-service-name)
    (swap! utils/needs-alarm conj ::player)
    (send-off schedule/schedule assoc-in [:last-started] nil))

  (onStop [this]
    (.superOnStop this)
    (remove-watch playing/last-playing ::player)
    (remove-watch media/status ::player)
    (swap! utils/needs-alarm disj ::player)
    (->> [:broadcast utils/end-alarm-signal]
         (notify/construct-pending-intent this)
         .send ;; have to use send not sendbroadcast?
         on-ui))

  (onCreateOptionsMenu [this menu]
    (.superOnCreateOptionsMenu this menu)
    (menu/make-menu menu [[:item {:title "About"
                                  ;; :icon
                                  :show-as-action :if-room
                                  :on-click (fn [_] (about-dialog this))}]])
    true) ;; why true?

  (onResume [this]
    (.superOnResume this)
    (send-off schedule/schedule assoc-in [:last-started] nil)
    (swap! utils/needs-alarm conj ::player)
    (add-watch playing/last-playing ::player (partial refresh-playing this))
    (add-watch media/status ::player (partial refresh-status this))
    (future (playing/playing!))
    (future (schedule/update-schedule!)))

  )



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
   (let [this (debug/*a)]
     (services/start-service-unbound this utils/player-service-name)))



  (debug/safe-for-ui
   (on-ui
    (-> (view/find-view (debug/*a) ::schedule)
        (ui/config :adapter (schedule-adapter (debug/*a))))))


  (utildroid/get-version-info (str utils/package-name ".debug") (debug/*a))



  (debug/safe-for-ui
   (view/find-view (debug/*a) ::schedule))

  (debug/safe-for-ui
   (on-ui
    (about-dialog (debug/*a))))

  (add-watch playing/last-playing ::player (partial refresh-playing (debug/*a)))

  (set-text! (debug/*a) ::playing-text "yo yo yo")

  (service/started?)


  (debug/safe-for-ui
   (services/start-service-unbound (debug/*a) utils/alarm-service-name))


  (version-str (debug/*a))




  )








