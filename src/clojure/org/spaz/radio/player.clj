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
           android.graphics.Color
           android.net.Uri
           de.schildbach.wallet.integration.android.BitcoinIntegration
           android.content.Intent))



(declare ^android.widget.LinearLayout playing-layout)
(declare ^android.widget.LinearLayout show-layout)

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
    (.setText (r/get-string :stop_button))
    utils/force-top-level-redraw
    (.setOnClickListener (lview/on-click-call (partial stop-player this))))
  (set-text! ::status-text (str (r/get-string :connecting_status) "..."))
  (services/start-service-unbound this utils/player-service-name))



(defn stop-player
  [^android.app.Activity this ^android.view.View v]
  (doto v
    (.setText (r/get-string :listen_button))
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
    {:name name
     :dates (format "%s -\n%s" start_timestamp end_timestamp)}))



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
                    [:text-view {:text (str (r/get-string :starts_date) ": " start_timestamp)}]
                    [:text-view {:text (str (r/get-string :ends_date) ": " end_timestamp)}]
                    (when (and (not (empty? url))
                               (= scheme "bitcoin"))
                      [:text-view {:text (r/get-string :dj_donation_beg)
                                   :horizontally-scrolling false
                                   :layout-margin-top 16}])]])
     (merge {:negative-name (r/get-string :cancel_button)
             :negative-func dialogs/cancel}
            (when-not (empty? url)
              {:positive-name (if (= scheme "bitcoin")
                                (r/get-string :donate_button)
                                (r/get-string :more_info_button))
               :positive-func (if (= scheme "bitcoin")
                                (fn [c _ _]
                                  (BitcoinIntegration/request ctx u))
                                (fn [c _ _]
                                  (debug/safe-for-ui
                                   (.startActivity ctx (Intent. Intent/ACTION_VIEW u)))))})))))


;; TODO: this really wants to be a custom horiz listview with the dates on the left
(def show-layout-tree
  [:linear-layout {:id-holder true
                   :def `show-layout
                   :orientation :vertical}
   [:text-view {:id ::show-name
                :text-color Color/YELLOW}]
   [:text-view {:id ::show-dates
                :text-color Color/WHITE}]])


(defn schedule-adapter [activity]
  (adapters/ref-adapter
   (fn [] show-layout-tree)
   (fn [_ view _ {:keys [url] :as show}]
     (let [{:keys [name dates]} (format-show show)]
       (debug/safe-for-ui
        (on-ui
         (.setOnClickListener ^android.widget.TextView view
                              (lview/on-click  (when-not (empty? url)
                                                 (show-dialog activity show))))
         (.setText ^android.widget.TextView (-> view .getTag  ::show-name) name)
         (.setText ^android.widget.TextView (-> view .getTag  ::show-dates) dates)))))
   schedule/schedule
   #(or (:future %) [])))


(def playing-layout* [:linear-layout {:orientation :vertical,
                                      :id-holder true,
                                      :def `playing-layout}
                      [:text-view {:id ::now-playing-text
                                   :text :now-playing}]
                      [:text-view {:id ::playing-text
                                   :text :checking
                                   :horizontally-scrolling false}]
                      [:linear-layout {:orientation :horizontal}
                       [:button {:id ::playing-button
                                 :text :configuring}]
                       [:text-view {:text ""
                                    :id  ::status-text}]]
                      [:text-view {:id ::upcoming-header
                                   :text :upcoming-shows}]
                      [:list-view {:id ::schedule}]])


(defn set-playing-button
  [this]
  (let [pb (get-view ::playing-button)] 
    (if (service/started?)
      (doto pb
        (.setOnClickListener (lview/on-click-call (partial stop-player this)))
        (.setText (r/get-string :stop_button)))
      (doto pb
        (.setOnClickListener (lview/on-click-call (partial start-player this)))
        (.setText (r/get-string :listen_button))))))


(defn donate-btc
  [ctx]
  (BitcoinIntegration/request ctx (rand-nth utils/btc-donation-addresses)))

(defn about-dialog
  [ctx]
  (dialogs/show-dialog!
   ctx
   (r/get-string :app_name)
   (make-ui ctx [:linear-layout {:orientation :vertical,
                                 :id-holder true}
                 [:text-view {:text (let [{:keys [version-name version-number]}
                                          (utildroid/get-version-info utils/package-name)]
                                      (format "%s %s, %s %d"
                                              (r/get-string :version) version-name
                                              (r/get-string :build) version-number))
                              :layout-margin-left 16
                              :horizontally-scrolling false}]
                 [:text-view {:text (r/get-string :developer_donation_beg)
                              :layout-width :fill
                              :layout-margin-left 16
                              :layout-margin-top 16
                              :layout-gravity :left}]]
            )
   {:negative-name (r/get-string :cancel_button)
    :negative-func dialogs/cancel
    :positive-name (r/get-string :donate_button)
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
                            (menu/make-menu menu [[:item {;; TODO: figure out how to use resources for title
                                                          :title "About" 
                                                          ;; TODO: :icon
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







