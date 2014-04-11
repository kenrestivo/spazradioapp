(ns org.spaz.radio.schedule-test
  (:use clojure.test
        org.spaz.radio.schedule))


(deftest date-fix
  (testing "date fix")
  (is (= #inst "2014-04-08T06:59:00.000-00:00"
         (datefix "2014-04-07 23:59:00"))))

(deftest output-format
  (testing "output-format")
  (is (= "Monday, April 7, 2014 11:59 PM"
         (output-datetime-format #inst "2014-04-08T06:59:00.000-00:00"))))

(comment

  ;; short
  ;; (= "Monday 11:59 PM"
  ;;    (output-datetime-format #inst "2014-04-08T06:59:00.000-00:00"))

  
  
  )

(comment

  (.format  (SimpleDateFormat. "EEEE" (Locale/getDefault)) (java.util.Date.))


  (.format (SimpleDateFormat/getDateInstance SimpleDateFormat/DEFAULT (Locale/getDefault)) (java.util.Date.))
  
  )