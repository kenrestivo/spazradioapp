(defproject spazradio/spazradio "0.0.1-SNAPSHOT"
  :description "Streaming SPAZ Radio"
  :url "http://spaz.org"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"


  :global-vars {*warn-on-reflection* true}

  :plugins [[lein-droid "0.2.2"]]
  :source-paths ["src/clojure" "src"]
  :java-source-paths ["src/java" "gen"]
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :dependencies [[org.clojure-android/clojure "1.5.1-jb" :use-resources true]
                 [neko/neko "3.0.0"]
                 [utilza "0.1.53"]
                 [cheshire "5.3.0"]]
  :profiles {:dev {:dependencies [[android/tools.nrepl "0.2.0-bigstack"]
                                  [clojure-complete "0.2.3"]
                                  [compliment "0.0.3"]]
                   :android {:aot :all-with-unused}} ;; nrepl doesn't work without it
             :release {:android
                       {;; Specify the path to your private keystore
                        ;; and the the alias of the key you want to
                        ;; sign APKs with. Do it either here or in
                        ;; ~/.lein/profiles.clj
                        ;; :keystore-path "/home/user/.android/private.keystore"
                        ;; :key-alias "spaz"
                        :ignore-log-priority [:debug :verbose]
                        :aot :all}}}

  :android {;; Specify the path to the Android SDK directory either
            ;; here or in your ~/.lein/profiles.clj file.
            ;; :sdk-path "/home/user/path/to/android-sdk/"

            ;; to use the support library, massive bleah happens, unless you give it ram
            :dex-opts ["-JXmx4096M"]

            ;; If previous option didn't work, uncomment this as well.
            ;; :force-dex-optimize true
            
            :support-libraries ["v4"] ;; needed for localbroadcast
            :target-version "14"
            :aot-exclude-ns [clojure.parallel
                             clojure.core.reducers
                             ;; required just to cherrypick one lib from utilza
                             utilza.base32
                             utilza.noir.future
                             utilza.noir.misc
                             utilza.noir.session
                             utilza.clutch
                             utilza.datomic
                             utilza.hiccupy
                             utilza.log
                             utilza.pom
                             utilza.http
                             utilza.json
                             utilza.zip
                             utilza.memdb.file
                             utilza.java
                             utilza.qrcode
                             utilza.postgres
                             utilza.ring]})


