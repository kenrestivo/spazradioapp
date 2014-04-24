(ns org.spaz.radio.ui-test
  (:require             [neko.debug :as debug])
  (:use clojure.test
        org.spaz.radio.player))

(comment
  ;; works
  (debug/safe-for-ui
   (about-dialog a))


  ;; works
  (debug/safe-for-ui
   (->> "http://www.google.com"
        safe-url
        (Intent. Intent/ACTION_VIEW)
        (.startActivity a)))

  )

(comment
  
  (debug/safe-for-ui
   (let [ctx a]
     (on-ui
      (about-dialog ctx))))



  (debug/safe-for-ui
   (let [ctx a]
     (on-ui
      (show-dialog ctx
                   (->> @schedule/schedule
                        :future
                        (remove #(-> % :url empty?))
                        second)))))

  (debug/safe-for-ui
   (let [ctx a]
     (on-ui
      (show-dialog ctx
                   (->  @schedule/schedule
                        :future
                        first
                        (assoc :url "spaz.org"))))))
  )