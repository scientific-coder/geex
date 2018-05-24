(ns lime.platform.low

  "Platform specific code needed by the compiler"
  
  (:require [bluebell.utils.defmultiple :refer [defmultiple defmultiple-extra]]
            [bluebell.utils.core :as utils]
            [lime.core.defs :as defs]
            [bluebell.utils.setdispatch :as sd]
            [lime.core.seed :as seed]
            [lime.core.typesystem :as ts]
            [bluebell.utils.symset :as ss]
            [lime.core.datatypes :as dt]
            ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;   Common utilities
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def known-platforms (atom #{:clojure
                             :java}))

(defn known-platform? [x]
  (contains? (deref known-platforms) x))

(defn register-platform
  "When extending this library to support new paltforms (e.g. OpenCL), register it here"
  [new-platform]
  (swap! known-platforms conj new-platform))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;   Code generators
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmultiple compile-static-value defs/platform-dispatch
  (defs/clojure-platform [value] value)
  (defs/java-platform [value] (str value)))

(sd/def-dispatch get-type-signature ts/system ts/feature)

(sd/def-set-method get-type-signature
  "A seed with a general Java class"
  [[[:platform :java] p]
   [(ss/difference [:seed :class]
                   [:seed :java-primitive]) x]]
  (.getName (seed/datatype x)))

(sd/def-set-method get-type-signature
  "A seed with a Java primitive"
  [[[:platform :java] p]
   [[:seed :java-primitive] x]]
  (-> dt/primitive-types
      (get (seed/datatype x))
      :java-name))

(sd/def-set-method get-type-signature
  "A vector"
  [[[:platform :java] p]
   [:vector x]]
  "clojure.lang.IPersistentVector")

(sd/def-set-method get-type-signature
  "A map"
  [[[:platform :java] p]
   [:map x]]
  "clojure.lang.IPersistentMap")

(sd/def-set-method get-type-signature
  "A map"
  [[[:platform :java] p]
   [:set x]]
  "clojure.lang.IPersistentSet")

(sd/def-set-method get-type-signature
  "Anything else"
  [[[:platform :java] p]
   [:any x]]
  "java.lang.Object")
