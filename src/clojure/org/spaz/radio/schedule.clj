(ns org.spaz.radio.schedule
  (:require [neko.activity :as activity :refer [defactivity set-content-view!]]
            [neko.threading :as threading :refer [on-ui]]
            [neko.resource :as r]
            [neko.context :as context]
            [cheshire.core :as json]
            [utilza.misc :as utilza]
            [org.spaz.radio.utils :as utils]
            [neko.log :as log]
            [neko.ui :as ui :refer [make-ui]])
  (:import  java.text.SimpleDateFormat
            java.util.Locale
            java.util.TimeZone))


(defonce schedule (atom {:current []
                         :future []
                         :last-started nil}))

;; TODO: move to settings
(defonce schedule-url (atom "http://spazradio.bamfic.com/api/week-info"))

(def read-df
  (doto (SimpleDateFormat. "yyyy-MM-dd HH:mm", Locale/US)
    (.setTimeZone (TimeZone/getTimeZone "America/Los_Angeles"))))


(defn datefix
  [^java.lang.String s]
  (.parse read-df s))

(def colfixes {:start_timestamp datefix
               :end_timestamp datefix})


(defn fix-record
  "Takes a single record map for a show,
    returns a map of the properly formatted data"
  [m]
  (utilza/munge-columns colfixes
                        (select-keys m [:start_timestamp :end_timestamp :name :url])))



(defn parse-weekly
  "Takes vector of vectors of records, outputs formatted, sorted schedule as seq of maps"
  [xs]
  (->> xs
       vals
       (filter vector?)
       (apply concat)
       (map fix-record)
       (sort-by :start_timestamp)))


(defn split-by-current
  "Takes a current #inst, and a map of the schedule atom.
   Returns the updated schedule atom with the current and future updated for that time provided."
  [^java.util.Date d m]
  (->> m
       (group-by #(-> % :start_timestamp (.after d)))
       vals
       (zipmap [:current :future])))


(def output-long-date-instance (SimpleDateFormat/getDateInstance SimpleDateFormat/FULL (Locale/getDefault)))
(def output-short-date-instance (SimpleDateFormat. "EEEE" (Locale/getDefault)))
(def output-time-instance (SimpleDateFormat/getTimeInstance SimpleDateFormat/SHORT (Locale/getDefault)))

(defn output-datetime-format
  [^java.util.Date d]
  (apply str (interpose " "
                        [(.format output-long-date-instance d)
                         (.format output-time-instance d)])))


(defn fetch-schedule
  ([^java.lang.String url]
  (try
    (some-> url
            slurp
            (json/decode true)
            parse-weekly)
    (catch Exception e
      (log/e e)
      "formatting error, checking again...")))
  ([]
     (fetch-schedule @schedule-url)))




;; ugly but it works
(defn update-schedule-fn
  "Takes a URL and a (current) #inst, and returns a function
   which takes the old schedule atom and updates the future/current and last-started,
   and goes out and fetches a new schedule if a new show has started since last check."
  [^java.lang.String url ^java.util.Date d]
  (fn [{:keys [last-started current future] :as old-sched}]
    (let [{:keys [current future] :as new-sched} (->> (concat current future) ;; rejoining for resplitting
                                                      (split-by-current d))
          new-last-started (-> current last :start_timestamp)]
      (-> (if (and last-started ;; could be nil if app just started up?
                   (.after last-started new-last-started))
            (or (some->> url fetch-schedule  (split-by-current d))
                new-sched) ;; in case it fails
            new-sched)
          (assoc :last-started new-last-started)))))

(defn init-schedule!
  ([^java.lang.String url]
  (->> url
       fetch-schedule
       (split-by-current (java.util.Date.))
       (reset! schedule)))
  ([]
     (init-schedule! @schedule-url)))


(defn update-schedule!
  ([^java.lang.String url ^java.util.Date date]
     (swap! schedule (update-schedule-fn url date)))
  ([^java.lang.String url]
     (update-schedule! url (java.util.Date.)))
  ([]
     (update-schedule! @schedule-url)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment


  ;; functional tests, needs a working mock server

  (reset! schedule-url (str "http://" utils/fake-server "/schedule-logs/schedule-week"))

  (fetch-schedule)
  
  (init-schedule!)
  
  ;; check that the atom is being updated properly.
  (update-schedule!)

  
  )





