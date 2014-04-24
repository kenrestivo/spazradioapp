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

(ns net.nightweb.dialogs
  "Shamelessly copy/pasted from Nightweb http://github.com/oakes/Nightweb"
  (:require [neko.resource :as r]
            [neko.threading :as thread]
            [neko.ui :as ui])
  (:import [android.app Activity AlertDialog DialogFragment]
           [android.content DialogInterface]
           [android.text InputType]
           [android.view View]
           [android.widget Button]))

(defn create-dialog
  [^Activity context ^String message ^View view buttons]
  (let [builder (android.app.AlertDialog$Builder. context)]
    (when-let [positive-name (:positive-name buttons)]
      (.setPositiveButton builder positive-name nil))
    (when-let [neutral-name (:neutral-name buttons)]
      (.setNeutralButton builder neutral-name nil))
    (when-let [negative-name (:negative-name buttons)]
      (.setNegativeButton builder negative-name nil))
    (.setMessage builder message)
    (.setView builder view)
    (let [^AlertDialog dialog (.create builder)
          btn-action (fn [dialog button func]
                       (proxy [android.view.View$OnClickListener] []
                         (onClick [v]
                           (when (func context view button)
                             (try (.dismiss dialog)
                                  (catch Exception e nil))))))]
      (.setOnShowListener
       dialog
       (proxy [android.content.DialogInterface$OnShowListener] []
         (onShow [d]
           (when-let [positive-btn (.getButton d AlertDialog/BUTTON_POSITIVE)]
             (->> (btn-action d positive-btn (:positive-func buttons))
                  (.setOnClickListener positive-btn)))
           (when-let [neutral-btn (.getButton d AlertDialog/BUTTON_NEUTRAL)]
             (->> (btn-action d neutral-btn (:neutral-func buttons))
                  (.setOnClickListener neutral-btn)))
           (when-let [negative-btn (.getButton d AlertDialog/BUTTON_NEGATIVE)]
             (->> (btn-action d negative-btn (:negative-func buttons))
                  (.setOnClickListener negative-btn))))))
      (.setCanceledOnTouchOutside dialog false)
      dialog)))

(defn show-dialog!
  ([^Activity context ^String title ^String message]
     (let [builder (android.app.AlertDialog$Builder. context)]
       (.setPositiveButton builder (r/get-string :ok) nil)
       (let [^AlertDialog dialog (.create builder)]
         (.setTitle dialog title)
         (.setMessage dialog message)
         (.setCanceledOnTouchOutside dialog false)
         (try (.show dialog)
              (catch Exception e nil)))))
  ([^Activity context ^String message ^View view buttons]
     (-> (proxy [DialogFragment] []
           (onCreate [bundle]
             (proxy-super onCreate bundle)
             (.setRetainInstance ^DialogFragment this true))
           (onDetach []
             (proxy-super onDetach)
             (when view (.removeView (.getParent view) view)))
           (onDestroyView []
             (when (and (.getDialog ^DialogFragment this)
                        (.getRetainInstance ^DialogFragment this))
               (.setDismissMessage (.getDialog ^DialogFragment this) nil))
             (proxy-super onDestroyView))
           (onCreateDialog [bundle]
             (proxy-super onCreateDialog bundle)
             (create-dialog context message view buttons)))
         (.show (.getFragmentManager context) "dialog")
         (try (catch Exception e nil)))))


(defn cancel
  "Used by dialogs to perform no action other than closing themselves."
  [^Activity context ^View dialog-view ^Button button-view]
  true)