(ns org.spaz.radio.playing
  (:require [neko.activity :as activity :refer [defactivity set-content-view!]]
            [neko.threading :as threading :refer [on-ui]]
            [neko.resource :as r]
            [neko.context :as context]
            [cheshire.core :as json]
            [org.spaz.radio.utils :as utils]
            [neko.log :as log]
            [neko.ui :as ui :refer [make-ui]]))

(def error
  #(log/e %&)) ;; cough, hack.

;; can't use (r/string :checking) here because java.lang.ClassCastException: clojure.lang.Var$Unbound cannot be cast to android.content.Context
(defonce last-playing (agent "Checking..." 
                             :error-mode :continue
                             :error-handler error))
;; TODO: move to settings
(defonce playing-url (atom "http://spazradio.bamfic.com/playing"))


(defonce playing-thread (atom nil))

(def default-timeout 10000)


(defn fetch-playing
  [url]
  (try
    (some-> url
            slurp
            (json/decode true)
            :playing)
    (catch Exception e
      (error e))))



(defn playing
  ([_]
     (fetch-playing @playing-url))
  ([]
     (playing nil)))


(defn playing!
  []
  (send-off last-playing  playing))


(defn init
  "Stuff that has to happen at init time, with a context present"
  []
  (set-validator! last-playing (fn [v] (and (not (nil? v))
                                            (not= "" v))))
  (send last-playing (fn [_] (r/get-string :checking))))



(comment


  ;; functional tests, needs a working mock server

  
  (reset! playing-url (str "http://" utils/fake-server "/playing-logs/playing-spukkin"))
  (reset! playing-url (str "http://" utils/fake-server "/playing-logs/playing-blank"))
  (reset! playing-urlc (str "http://" utils/fake-server "/playing-logs/playing-archive"))

  (reset! playing-url (str "http://" utils/fake-server "/playing-logs/nowhere-nothing-none"))

  (reset! playing-url (str "http://" utils/fake-server "/playing-logs/playing"))

  
  ;; check the values
  (playing)

  ;; check that the atom is being updated properly.
  (playing!)

  
  )
