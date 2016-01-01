(defproject spazradio/spazradio "0.1.8"
  :description "Streaming SPAZ Radio"
  :url "http://spaz.org/radio"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.5.0"

  :global-vars {*warn-on-reflection* true}

  :source-paths ["src/clojure" "src"]
  :java-source-paths ["src/java"]
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]

  :plugins [[lein-droid "0.4.3"]]

  :target-path "target"
 
  :dependencies [[org.clojure-android/clojure "1.7.0-r4" :use-resources true]
                 [neko/neko "4.0.0-alpha5"
                  :exclusions [org.clojure-android/clojure]]
                 [utilza "0.1.73"]
                 [com.android.support/support-v4 "21.0.0" :extension "aar"]
                 [cheshire "5.5.0"]]

  :profiles {:default [:dev]
             :dev  [:android-common :android-user
                    {:target-path "target/debug"
                     :dependencies [[org.clojure/tools.nrepl "0.2.10"]]
                     :android {:aot :all-with-unused
                               ;; The namespace of the app package - having a
                               ;; different one for dev and release allows you to
                               ;; install both at the same time.
                               :rename-manifest-package "org.spaz.radio.debug"
                               :manifest-options {:app-name "SPAZ Radio - debug"}
                               }}]
             :release
             [:android-common
              {:target-path "target/release"
               :global-vars ^:replace {clojure.core/*warn-on-reflection* true}
               :android {:use-debug-keystore false
                         :key-alias "kengoogleplay"
                         :keystore-path "/home/localkens/embed/droid/dev/ken-google-play.keystore"
                         :ignore-log-priority [:debug :verbose]
                         :aot :all
                         :build-type :release}}]

             :lean
             [:release
              {:dependencies ^:replace [[org.skummet/clojure "1.7.0-r1"]
                                        [utilza "0.1.73"]
                                        [com.android.support/support-v4 "21.0.0" :extension "aar"]
                                        [cheshire "5.5.0"]
                                        [neko/neko "4.0.0-alpha5"]]
               :exclusions [[org.clojure/clojure]
                            [org.clojure-android/clojure]]
               :jvm-opts ["-Dclojure.compile.ignore-lean-classes=true"]
               :global-vars ^:replace {clojure.core/*warn-on-reflection* true}
               :android {:proguard-execute true
                         :use-debug-keystore false
                         :proguard-conf-path "build/proguard-minify.cfg"
                         :lean-compile true
                         :skummet-skip-vars [;; You can list here var names that
                                             ;; you want to keep non-lean. E.g.:
                                             ;; "#'foo.bar/my-function"
                                             ]}}]}
  :android {;; Specify the path to the Android SDK directory either
            ;; here or in your ~/.lein/profiles.clj file.
            ;; :sdk-path "/home/user/path/to/android-sdk/"

            ;; to use the support library, massive bleah happens, unless you give it ram
            :dex-opts ["-JXmx4096M" "--incremental" "--num-threads=8"]

            ;; If previous option didn't work, uncomment this as well.
            ;; :force-dex-optimize true
            
            :target-version 18

            :manifest-options {:app-name "@string/app_name"}

            :aot-exclude-ns ["clojure.core.reducers"
                             "clojure.parallel"
                             #"cljs-tooling\..+"
                             "cider.nrepl" 
                             "cider-nrepl.plugin"
                             "org.spaz.radio.playing-test"
                             "org.spaz.radio.schedule-test"
                             "org.spaz.radio.ui-test"
                             ;; required just to cherrypick one lib from utilza
                             "utilza.base32"
                             "utilza.clutch"
                             "utilza.datomic"
                             "utilza.hiccupy"
                             "utilza.http"
                             "utilza.java" ;; can i put this back now?
                             "utilza.java.joda"
                             "utilza.json"
                             "utilza.jwt"
                             "utilza.log"
                             "utilza.mcp"
                             "utilza.memdb"
                             "utilza.memdb.file"
                             "utilza.noir.future"
                             "utilza.noir.misc"
                             "utilza.noir.session"
                             "utilza.pom"
                             "utilza.postgres"
                             "utilza.qrcode"
                             "utilza.ring"
                             "utilza.tcp"
                             "utilza.unix"
                             "utilza.zip"]
            })

