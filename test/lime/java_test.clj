(ns lime.java-test
  (:require [clojure.test :refer :all]
            [lime.java :refer :all]))

(def c (time (janino-cook-and-load-object
              "Kattskit"
              "public class Kattskit {public double sq(double x) {return x*x;}}")))


(deftest cooked-c-test
  (is (= 81.0 (.sq c 9))))

(deftest arglist-parse-test
  (is (= (parse-typed-defn-args '(kattskit [:cobra b :mjao d] (+ b d)))
         '{:name kattskit, :arglist [{:type :cobra, :symbol b}
                                     {:type :mjao, :symbol d}],
           :body [(+ b d)]})))