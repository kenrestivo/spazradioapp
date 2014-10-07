(ns org.spaz.radio.playing
  (:require [neko.activity :as activity :refer [defactivity set-content-view!]]
            [neko.threading :as threading :refer [on-ui]]
            [neko.resource :as r]
            [neko.context :as context]
            [cheshire.core :as json]
            [org.spaz.radio.utils :as utils]
            [neko.log :as log]
            [neko.ui :as ui :refer [make-ui]]))


(defonce last-playing (atom "Checking..."))

;; TODO: move to settings
(defonce playing-url (atom "http://spazradio.bamfic.com/playing"))


(defn fetch-playing
  [url]
  (try
    (some-> url
            slurp
            (json/decode true)
            :playing)
    (catch Exception e
      (log/e e))))



(defn playing
  ([_]
     (or (fetch-playing @playing-url) (r/get-string :checking)))
  ([]
     (playing nil)))


(defn playing!
  []
  (swap! last-playing  playing))


(defn init
  "Stuff that has to happen at init time, with a context present"
  []
  (reset! last-playing (r/get-string :checking)))

(comment


  



  ;; functional tests, needs a working mock server

  
  (reset! playing-url (str "http://" utils/fake-server "/playing-logs/playing-spukkin"))
  (reset! playing-url (str "http://" utils/fake-server "/playing-logs/playing-blank"))
  (reset! playing-url (str "http://" utils/fake-server "/playing-logs/playing-archive"))

  (reset! playing-url (str "http://" utils/fake-server "/playing-logs/nowhere-nothing-none"))


  ;; check the values
  (playing)

  ;; check that the atom is being updated properly.
  (playing!)

  
  )
