;; Copy/pasted shamelessly from NightWeb https://github.com/oakes/Nightweb
;;
;; This is free and unencumbered software released into the public domain.

;; Anyone is free to copy, modify, publish, use, compile, sell, or
;; distribute this software, either in source code form or as a compiled
;; binary, for any purpose, commercial or non-commercial, and by any
;; means.
;; 
;; In jurisdictions that recognize copyright laws, the author or authors
;; of this software dedicate any and all copyright interest in the
;; software to the public domain. We make this dedication for the benefit
;; of the public at large and to the detriment of our heirs and
;; successors. We intend this dedication to be an overt act of
;; relinquishment in perpetuity of all present and future rights to this
;; software under copyright law.
;; 
;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
;; EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
;; MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
;; IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
;; OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
;; ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
;; OTHER DEALINGS IN THE SOFTWARE.
;; 
;; For more information, please refer to <http://unlicense.org/>
;; 

(ns net.clandroid.service
  "Shamelessly copy/pasted from Nightweb http://github.com/oakes/Nightweb"
  (:require [neko.-utils :as utils])
  (:import android.app.Activity
           android.app.Service
            android.support.v4.content.LocalBroadcastManager
            ))

(defn start-service-unbound
  [^Activity context service-name]
  {:pre [(string? service-name)]}
  (->> (doto (android.content.Intent.)
         (.setClassName context service-name))
       (.startService context)))

(defn bind-service
  [^Service context class-name connected]
  {:pre [(string? class-name)]}
  (let [intent (android.content.Intent.)
        connection (proxy [android.content.ServiceConnection] []
                     (onServiceConnected [component-name binder]
                       (connected binder))
                     (onServiceDisconnected [component-name] ()))]
    (.setClassName intent context class-name) 
    (.startService context intent)
    (.bindService context intent connection 0)
    connection))

(defn unbind-service
  [^Service context connection]
  (.unbindService context connection))

(defn start-service
  ([^Service context service-name]
     (start-service context service-name (fn [binder])))
  ([^Service context service-name on-connected]
     (let [service (bind-service context service-name on-connected)]
       (swap! (.state context) assoc :service service))))

(defn stop-service
  [^Service context]
  (when-let [service (get @(.state context) :service)]
    (unbind-service context service)
    (swap! (.state context) dissoc :service))) ;; so it doesn't leak

(defn start-receiver
  [^android.app.Service context receiver-name func]
  (let [receiver (proxy [android.content.BroadcastReceiver] []
                   (onReceive [context intent]
                     (func context intent)))]
    (.registerReceiver context
                       receiver
                       (android.content.IntentFilter. receiver-name))
    (swap! (.state context) assoc receiver-name receiver)))

(defn start-local-receiver
  [^Service context receiver-name func]
  (-> (LocalBroadcastManager/getInstance context)
      (start-receiver receiver-name func)))

(defn stop-receiver
  [^Service context receiver-name]
  (when-let [receiver (get @(.state context) receiver-name)]
    (.unregisterReceiver context receiver)))

(defn stop-local-receiver
  [^Service context receiver-name]
  (-> (LocalBroadcastManager/getInstance context)
      (stop-receiver receiver-name)))

(defn send-broadcast
  [^Service context params action-name]
  (let [intent (android.content.Intent.)]
    (.putExtra intent "params" params)
    (.setAction intent action-name)
    (.sendBroadcast context intent)))

(defn send-local-broadcast
  [^Service context params action-name]
  (-> (LocalBroadcastManager/getInstance context)
      (send-broadcast params action-name)))

(defn start-foreground
  [service id notification]
  (.startForeground service id notification))

(do
  (gen-class
   :name "CustomBinder"
   :extends android.os.Binder
   :state "state"
   :init "init"
   :constructors {[android.app.Service] []}
   :prefix "binder-")
  (defn binder-init
    [service]
    [[] service])
  (defn create-binder
    [service]
    (CustomBinder. service)))

(defmacro defservice
  [name & {:keys [extends prefix on-start-command def] :as options}]
  (let [options (or options {})
        sname (utils/simple-name name)
        prefix (or prefix (str sname "-"))
        def (or def (symbol (utils/unicaseize sname)))]
    `(do
       (gen-class
        :name ~name
        :main false
        :prefix ~prefix
        :init "init"
        :state "state"
        :extends ~(or extends android.app.Service)
        :exposes-methods {~'onCreate ~'superOnCreate
                          ~'onDestroy ~'superOnDestroy})
       (defn ~(symbol (str prefix "init"))
         [] [[] (atom {})])
       (defn ~(symbol (str prefix "onBind"))
         [~(vary-meta 'this assoc :tag name),
          ^android.content.Intent ~'intent]
         (def ~(vary-meta def assoc :tag name) ~'this)
         (~create-binder ~'this))
       ~(when on-start-command
          `(defn ~(symbol (str prefix "onStartCommand"))
             [~(vary-meta 'this assoc :tag name),
              ^android.content.Intent ~'intent,
              ^int ~'flags,
              ^int ~'startId]
             (def ~(vary-meta def assoc :tag name) ~'this)
             (~on-start-command ~'this ~'intent ~'flags ~'startId)
             android.app.Service/START_STICKY))
       ~@(for [kw [:on-create :on-destroy]]
           (let [func (kw options)
                 event-name (utils/keyword->camelcase kw)]
             (when func
               `(defn ~(symbol (str prefix event-name))
                  [~(vary-meta 'this assoc :tag name)]
                  (~(symbol (str ".super" (utils/capitalize event-name)))
                   ~'this)
                  (~func ~'this))))
           ))))
