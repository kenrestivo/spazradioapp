(ns org.spaz.radio.player
  (:require [neko.activity :as activity :refer [defactivity set-content-view!]]
            [neko.threading :as threading :refer [on-ui]]
            [neko.notify :as notify]
            [net.clandroid.service :as services]
            [neko.resource :as r]
            [neko.context :as context]
            [utilza.misc :as utilza]
            [neko.find-view :as view]
            [utilza.android :as utildroid]
            [neko.listeners.view :as lview]
            [neko.ui.adapters :as adapters]
            [neko.ui.menu :as menu]
            [neko.data :as data]
            [neko.doc :as doc]
            [neko.debug :as debug]
            [org.spaz.radio.media :as media]
            [org.spaz.radio.playing :as playing]
            [org.spaz.radio.schedule :as schedule]
            [org.spaz.radio.utils :as utils]
            [org.spaz.radio.service :as service]
            [neko.log :as log]
            [net.nightweb.dialogs :as dialogs]
            [neko.ui :as ui :refer [make-ui]])
  (:import android.media.MediaPlayer
           android.content.ComponentName
           android.net.Uri
           de.schildbach.wallet.integration.android.BitcoinIntegration
           android.content.Intent))



(declare ^android.widget.LinearLayout playing-layout)

(declare stop-player) ;; because start-player needs it.

(defn safe-url
  "Copy/pasted from utilza"
  [^String s]
  (let [u (Uri/parse s)]
    (if (.getScheme u)
      u
      (recur (str "http://" s)))))


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



(defn start-player
  [^android.app.Activity this ^android.view.View v]
  (doto v
    (.setText "Stop")
    utils/force-top-level-redraw
    (.setOnClickListener (lview/on-click-call (partial stop-player this))))
  (set-text! ::status-text "Connecting...")
  (services/start-service-unbound this utils/player-service-name))



(defn stop-player
  [^android.app.Activity this ^android.view.View v]
  (doto v
    (.setText "Listen")
    utils/force-top-level-redraw
    (.setOnClickListener (lview/on-click-call (partial start-player this))))
  (set-text! ::status-text "")
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
      (set-text! ::playing-text new))))


(defn refresh-status
  [k r old new]
  (future
    (when-not (= new (->> ::status-text
                          get-view
                          .getText))
      (log/d "updating activity view" new)
      (set-text! ::status-text new))))


(def display-colfixes {:start_timestamp schedule/output-datetime-format
                       :end_timestamp schedule/output-datetime-format})

(defn format-show
  [show]
  {:pre [(-> show nil? not)]}
  (let [{:keys [name url start_timestamp end_timestamp]} (utilza/munge-columns display-colfixes show)]
    ;; TODO: return some kind of map maybe?
    ;; TODO: maybe if there's an url, make the show clickable
    ;; TODO: this really wants to be a custom horiz listview with the dates on the left
    ;;       and the shows on the right, with the shows in a different font/color than the dates, etc.
    (format "%s\n%s -\n%s" name start_timestamp end_timestamp)))



(defn show-dialog
  [ctx show]
  (let [{:keys [name url start_timestamp end_timestamp]} (utilza/munge-columns display-colfixes show)
        u (safe-url url)
        scheme (.getScheme u)]
    (dialogs/show-dialog!
     ctx
     name
     (make-ui ctx [:linear-layout {:orientation :vertical,
                                   :id-holder true}
                   [:linear-layout {:orientation :vertical
                                    :layout-margin-left 16}
                    [:text-view {:text (str "Starts: " start_timestamp)}]
                    [:text-view {:text (str "Ends: " end_timestamp)}]
                    (when (and (not (empty? url))
                               (= scheme "bitcoin"))
                      [:text-view {:text "Like the show? Got some BTC for the DJ?"
                                   :horizontally-scrolling false
                                   :layout-margin-top 16}])]])
     (merge {:negative-name "Cancel"
             :negative-func dialogs/cancel}
            (when-not (empty? url)
              {:positive-name (if (= scheme "bitcoin")
                                "Donate"
                                "More info")
               :positive-func (if (= scheme "bitcoin")
                                (fn [c _ _]
                                  (BitcoinIntegration/request ctx u))
                                (fn [c _ _]
                                  (debug/safe-for-ui
                                   (as-> u x
                                         (Intent. Intent/ACTION_VIEW x)
                                         (.startActivity ctx x)))))})))))





(defn schedule-adapter [activity]
  (adapters/ref-adapter
   (fn [] [:text-view {}])
   (fn [_ view _ {:keys [url] :as show}]
     (on-ui
      (.setOnClickListener ^android.widget.TextView view
                           (lview/on-click  (show-dialog activity show)))
      (.setText ^android.widget.TextView view (format-show show))))
   schedule/schedule
   #(or (:future %) [])))



(def playing-layout* [:linear-layout {:orientation :vertical,
                                      :id-holder true,
                                      :def `playing-layout}
                      [:text-view {:text "Now Playing:"}]
                      [:text-view {:text "checking..."
                                   :id ::playing-text
                                   :horizontally-scrolling false}]
                      [:linear-layout {:orientation :horizontal}
                       [:button {:text "Configuring"
                                 :id ::playing-button}]
                       [:text-view {:text ""
                                    :id  ::status-text}]]
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


(defn donate-btc
  [ctx]
  (BitcoinIntegration/request ctx (rand-nth utils/btc-donation-addresses)))

(defn about-dialog
  [ctx]
  (dialogs/show-dialog!
   ctx
   "SPAZ Radio App"
   (make-ui ctx [:linear-layout {:orientation :vertical,
                                 :id-holder true}
                 [:text-view {:text (let [{:keys [version-name version-number]}
                                          (utildroid/get-version-info utils/package-name)]
                                      (format "Version %s, build %d"
                                              version-name version-number))
                              :layout-margin-left 16
                              :horizontally-scrolling false}]
                 [:text-view {:text "Like the app? Got some BTC for the developer?"
                              :layout-width :fill
                              :layout-margin-left 16
                              :layout-margin-top 16
                              :layout-gravity :left}]]
            )
   {:negative-name "Cancel"
    :negative-func dialogs/cancel
    :positive-name "Donate"
    :positive-func (fn [ctx v btn]
                     (donate-btc ctx))}))



(defactivity org.spaz.radio.Player
  :state (atom {})  ;; when needed, (swap! (.state ctx) assoc k v)

  :def a ;; used for debug in repl
  
  :on-create (fn [this bundle]
               (on-ui
                (->> playing-layout*
                     make-ui
                     (set-content-view! this))
                (set-playing-button this))
               (add-watch playing/last-playing ::player refresh-playing)
               (add-watch media/status ::player refresh-status)
               (future (playing/playing!))
               (future
                 (schedule/update-schedule!)
                 (on-ui
                  (-> ::schedule
                      get-view
                      (ui/config :adapter (schedule-adapter this)))))
               ;; it'll only get started once, so no need to check here
               (swap! utils/needs-alarm conj ::player)
               (services/start-service-unbound this utils/alarm-service-name))
  
  :on-stop (fn [this]
             (remove-watch playing/last-playing ::player)
             (remove-watch media/status ::player)
             (swap! utils/needs-alarm disj ::player)
             (->> [:broadcast utils/end-alarm-signal]
                  notify/construct-pending-intent
                  .send ;; have to use send not sendbroadcast?
                  on-ui))
  
  :on-resume (fn [this]
               (swap! utils/needs-alarm conj ::player)
               (add-watch playing/last-playing ::player refresh-playing)
               (add-watch media/status ::player refresh-status)
               (future (playing/playing!))
               (future (schedule/update-schedule!)))
  
  :on-create-options-menu (fn [this menu]
                            (menu/make-menu menu [[:item {:title "About"
                                                          ;; :icon
                                                          :show-as-action :if-room
                                                          :on-click (fn [_] (about-dialog this))}]])))



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







