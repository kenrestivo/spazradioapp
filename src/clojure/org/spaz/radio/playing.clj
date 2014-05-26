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

(def icy-keys #{:icy-aim
                :icy-name
                :icy-genre
                :icy-url})


(defn clean-filename
  [s]
  (some-> s
          (clojure.string/split  #"/")
          last
          (clojure.string/split  #"\.")
          first
          ((partial apply str))))


(defn clean-title
  [title]
  (if (= "Unknown" title)
    nil
    title))

(defmulti formatp
  #(if (->> %
            keys
            (some (conj icy-keys :icy-br :source_url)))
     :live
     :archive))


(defmethod formatp :live 
  [m]
  (->> (select-keys m icy-keys)
       vals
       (interpose " · ")
       (apply str "[" (r/get-string :live) "] ")))


(defmethod formatp :archive
  [{:keys [title filename]}]
  (some->> [(clean-title title) (clean-filename filename) (r/get-string :mystery)]
           (filter (comp not empty?))
           first))



(defn fetch-playing
  [url]
  (try
    (some-> url
            slurp
            (json/decode true))
    (catch Exception e
      (log/e e)
      ;; kind of a hack, but it'll work. just don't bother if the network dies.
      {:title  @last-playing})))



(defn playing-formatted
  [url]
  (try
    (some-> url
            fetch-playing
            formatp)
    (catch Exception e
      (log/e e)
      "formatting error, checking again...")))


(defn playing
  ([_]
     (playing-formatted @playing-url))
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
