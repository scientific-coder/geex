(ns geex.core.seed
  (:import [geex Seed TypedSeed])
  (:require [clojure.spec.alpha :as spec]
            [geex.core.defs :as defs]
            [bluebell.utils.wip.party :as party]
            [bluebell.utils.wip.party.coll :as partycoll]
            [bluebell.utils.wip.core :as utils]
            [bluebell.utils.wip.tag.core :as tg]))


(def seed? (partial instance? Seed))

;; The dependencies of a seed
(defn access-deps [x]
  {:pre [(seed? x)]}
  (.deps x))

(defn typed-seed? [x]
  (instance? TypedSeed x))

(defn compilable-seed?
  "A seed that can be compiled"
  [x]
  (and (seed? x)
       (not (typed-seed? x))))

(defn only-numeric-keys [m]
  (filter (fn [[k v]] (number? k)) m))

(def access-indexed-map
  (party/wrap-accessor
   {:desc "access-indexed-map"
    :getter (fn [x] (mapv second (sort-by first (only-numeric-keys x))))
    :setter (fn [x y] (merge x (zipmap (range (count y)) y)))}))

(defn access-compiled-indexed-deps
  ([seed]
   {:pre [(instance? Seed seed)]}
   (.compilationResultsToArray (.deps seed))))

(defn access-indexed-deps [seed-params]
  (assert false))

(defn filter-deps [seed pred]
  (->> seed
       access-deps
       (map (fn [[k v]]
              (if (pred k)
                v)))
       (filter (complement nil?))))

;; Helper for filter-referents-of-seed
(defn dep-tagged? [x]
  (fn [y]
    (and (vector? y)
         (= (first y) x))))

(defn find-dep [seed pred]
  (first (filter-deps seed pred)))

(def static-value (party/key-accessor :static-value))

(defn datatype [x]
  {:pre [(seed? x)]}
  (.getType x))

(defn description [x]
  (.getDescription x))

(defn typed-seed [tp]
  (TypedSeed. tp))

(defn strip-seed [seed]
  (typed-seed (datatype seed)))

(defn access-compiled-deps [sd]
  {:pre [(seed? sd)]}
  (.getCompilationResults (.deps sd)))

(defn access-seed-data [x]
  {:pre [(seed? x)]}
  (.getData x))

(defn disp-deps [x]
  (println "DEPS:" (-> x access-deps keys))
  x)


(defn compilation-result
  ([state x]
   (.setCompilationResult state x)
   state)
  ([state]
   (.getCompilationResult state)))
