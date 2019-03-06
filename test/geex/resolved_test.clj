(ns geex.resolved-test
  (:require [geex.core :as core]
            [geex.java :as java]
            [geex.common :as c]
            [clojure.test :refer :all]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  This file is for tests that previously did not work
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;; This is the one that does not work.
(java/typed-defn find-index
                 [(c/array-class Integer/TYPE) data
                  Integer/TYPE value]
                 (let [len (c/count data)]
                   (core/Loop
                    [index 0]
                    (core/If
                     (c/= index len)
                     -1
                     (let [x (c/aget data index)]
                       (core/If (c/= x value)
                                (c/+ 1000 index)
                                (core/Recur (c/inc index))))))))


(deftest nested-if-problem
  (doseq [[number-to-find expected] (map vector
                                         [0 1 2 3 4 5 6]
                                         [-1 -1 1000 1001 1002 -1 -1])]
    (is (= expected (find-index (int-array [2 3 4])
                                number-to-find)))))


(java/typed-defn set-element []
                 (let [dst (c/make-array Float/TYPE 1)]
                   (c/aset dst 0 (float 3.0))))
