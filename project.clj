(defproject spazradio/spazradio "0.1.13"
  :description "Streaming SPAZ Radio"
  :url "http://spaz.org/radio"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"

  :plugins [[lein-droid "0.4.4-SNAPSHOT"]]
  :source-paths ["src/clojure" "src"]
  :java-source-paths ["src/java" "gen"]
  :dependencies [[org.clojure-android/clojure "1.7.0" :use-resources true]
                 [neko/neko "4.0.0-alpha5"]
                 [utilza "0.1.73"]
                 [com.google.android/support-v4 "r7"]
                 [cheshire "5.5.0"]]
  :javac-options ["-target" "1.7" "-source" "1.7" "-bootclasspath" "/opt/oracle/jdk1.7.0_09/lib/"]
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
               :android { 
                         :ignore-log-priority [:debug :verbose]
                         :enable-dynamic-compilation true
                         :aot :all
                         :build-type :release}}]

             :lean
             [:release
              {:dependencies ^:replace [[org.skummet/clojure "1.7.0-r1"]
                                        [neko/neko "4.0.0-alpha5"]]
               :exclusions [[org.clojure/clojure]
                            [org.clojure-android/clojure]]
               :jvm-opts ["-Dclojure.compile.ignore-lean-classes=true"]
               :global-vars ^:replace {clojure.core/*warn-on-reflection* true}
               :android {:use-debug-keystore true ;; wait, what? XXX no.
                         :proguard-execute true
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
            :dex-opts ["-JXmx4096M" "--incremental"]

            ;; If previous option didn't work, uncomment this as well.
            ;; :force-dex-optimize true
            
            :target-version "14"

            :manifest-options {:app-name "@string/app_name"}

            :aot-exclude-ns ["clojure.core.reducers"
                             "clojure.parallel"
                             "cljs-tooling.util.analysis" 
                             "cljs-tooling.util.misc"
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

