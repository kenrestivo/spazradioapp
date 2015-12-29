(ns org.spaz.radio.playing
  (:require [neko.activity :as activity :refer [ set-content-view!]]
            [neko.threading :as threading :refer [on-ui]]
            [neko.resource :as r]
            [neko.context :as context]
            [cheshire.core :as json]
            [org.spaz.radio.utils :as utils]
            [neko.log :as log]
            [neko.ui :as ui :refer [make-ui]]))



;; can't use (r/string :checking) here because java.lang.ClassCastException: clojure.lang.Var$Unbound cannot be cast to android.content.Context
(defonce last-playing (agent "Checking..." 
                             :error-mode :continue
                             :validator (fn [v] (not (or (nil? v)
                                                (= "" v))))
                             :error-handler utils/warn))
;; TODO: move to settings
(defonce playing-url (atom "http://radio.spaz.org/playing"))


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
      (log/w e)
      nil)))



(defn playing
  ([_]
     (fetch-playing @playing-url))
  ([]
     (playing nil)))


(defn playing!
  []
  (send-off last-playing  playing))



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
