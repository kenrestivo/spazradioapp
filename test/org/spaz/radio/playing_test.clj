(ns org.spaz.radio.playing-test
  (:use clojure.test
        org.spaz.radio.playing))

(deftest example-live-test
  (testing "example live")
  (is (= "[LIVE!] spukkin faceships fukkin spaceship · stoner bass · www.spaz.org"
         (formatp {:icy-genre "spukkin faceships fukkin spaceship",
                   :icy-name "stoner bass",
                   :icy-pub "1",
                   :icy-metaint "4096",
                   :icy-url "www.spaz.org",
                   :icy-br "128"}))))


(deftest blank-live-test
  (testing "blank live")
  (is (= "[LIVE!] "
         (formatp {:source_url "http://stream.spaz.org:8050/stream", :title ""}))))



(deftest broken-unknosn-test
  (testing "broken unknown")
  (is (=  "2013-05-19-all"
          (formatp {:filename "/home/streams/2013-05-19-all.ogg", 
                    :status "playing", 
                    :encoder "Liquidsoap/1.0.1+scm (Unix; OCaml 3.11.2)",
                    :source "archives", 
                    :initial_uri "/home/streams/2013-05-19-all.ogg",
                    :on_air "2014/01/26 00:40:11", 
                    :rid "1",
                    :kind "{audio=2;video=0;midi=0}", 
                    :decoder "OGG", 
                    :temporary "false",
                    :title "Unknown"}))))


(deftest nil-test
  (testing "nil format")
  (is (=  "IT'S A MYSTERY!!! Listen and guess."
          (formatp nil))))


(comment
  (formatp "checking...") ;; NOT legal.

  )

(comment
  (run-tests)
  )
