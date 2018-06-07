(ns geex.java.defs
  (:require [clojure.spec.alpha :as spec]))

(spec/def ::typed-argument (spec/cat :type any?
                                     :name symbol?))

(spec/def ::typed-arguments (spec/spec (spec/* ::typed-argument)))

(spec/def ::meta (spec/or :debug #{:debug}))

(spec/def ::defn-args (spec/cat :name symbol?
                                :meta (spec/* ::meta)
                                :arglist ::typed-arguments
                                :body (spec/* any?)))

(def binary-math-operators ["+" "-" "*" "/"])

(def operator-info-map (into {} (map (fn [s]
                                       [s {:clojure-fn (eval (symbol s))
                                           :name s}])
                                     binary-math-operators)))