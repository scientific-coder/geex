(ns geex.java-test
  (:require [clojure.test :refer :all]
            [geex.java :refer :all]
            [geex.core.seed :as seed]
            [bluebell.utils.debug :as debug]
            [geex.core.seedtype :as seedtype]))

(def c (time (janino-cook-and-load-object
              "Kattskit"
              "public class Kattskit {public double sq(double x) {return x*x;}}")))


(deftest cooked-c-test
  (is (= 81.0 (.sq c 9))))

(deftest arglist-parse-test
  (is (= (parse-typed-defn-args '(kattskit [:cobra b :mjao d] (+ b d)))
         '{:name kattskit, :arglist [{:type :cobra, :name b}
                                     {:type :mjao, :name d}],
           :body [(+ b d)]})))


(typed-defn return-119-2 [(seed/typed-seed java.lang.Double/TYPE) x] 119.0)

(deftest return-119-test
  (is (= 119.0 (return-119-2 30))))

#_(typed-defn second-arg-fun [(seed/typed-seed java.lang.Double) x
                            (seed/typed-seed java.lang.Long) y
                              (seed/typed-seed java.lang.Float) z] y)
(typed-defn second-arg-fun2 [(seed/typed-seed java.lang.Double/TYPE) x
                             (seed/typed-seed java.lang.Long/TYPE) y
                             (seed/typed-seed java.lang.Float/TYPE) z] y)
(deftest second-arg-test
  (is (= 119 (second-arg-fun2 3 119 4))))


(deftest is-a-test
  (is (isa? java.lang.Double java.lang.Number))
  (is (not (isa? java.lang.Number java.lang.Double))))


(typed-defn return-some-class2 [(seed/typed-seed java.lang.CharSequence) ch]
            ch)

(typed-defn check-cast2 [(seed/typed-seed java.lang.Object) obj]
            (unpack (seed/typed-seed java.lang.Double) obj))

(deftest return-some-class-test
  (is (= "kattskit" (return-some-class2 "kattskit")))
  (is (thrown? ClassCastException (return-some-class2 3)))
  (is (= 3.0 (check-cast2 3.0)))
  (is (thrown? ClassCastException (check-cast2 3))))

(deftest find-member-info-test
  (is (= 2 (count (find-member-info java.lang.String 'substring)))))

(typed-defn hash-code-test2 [(seed/typed-seed java.lang.String) obj]
            (call-method "hashCode" obj))

(deftest hash-code-test--
  (is (int? (hash-code-test2 "asdf"))))

#_(deftest java-symbol-type-to-class-test
  (is (= (java-type-symbol-to-class 'byte)
         java.lang.Byte)))

(typed-defn substring-from2 [(seed/typed-seed java.lang.String) s
                             (seed/typed-seed java.lang.Integer/TYPE) from]
            (call-method "substring" s from))

(deftest substring-2-test
  (is (= "cd" (substring-from2 "abcd" 2))))

(typed-defn int-to-float [(seed/typed-seed java.lang.Integer/TYPE) x]
            (call-method "floatValue"
                         (call-static-method "valueOf" java.lang.Integer x)))

(deftest nested-calls-static-method-test
  (is (= 9.0 (int-to-float 9))))

(typed-defn box-float [seedtype/float x]
            (box x))

(typed-defn no-box-float [(seed/typed-seed java.lang.Float) x]
            (box x))

(deftest boxing-test
  (is (= 3.0 (box-float 3)))
  (is (= 3.0 (no-box-float (float 3.0)))))

(typed-defn unbox-float [(seed/typed-seed java.lang.Float) x]
            (unbox x))

(deftest unboxing-test
  (is (= 3.0 (unbox-float (float 3.0)))))

(typed-defn second-element-v [[seedtype/int seedtype/double] x]
                (let [[a b] x]
                  b))

(deftest second-element-test
  (is (= 4.0 (second-element-v [3 4.0]))))

(typed-defn my-plus [seedtype/int a
                     seedtype/int b]
            (call-operator "+" a b))

(deftest my-plus-test
  (is (= 7 (my-plus 3 4))))

(typed-defn my-negate2 [seedtype/float x]
            (call-operator "-" x))

(deftest my-neg-test
  (is (= -9.0 (my-negate2 9))))

(typed-defn my-sq-norm [seedtype/int x
                        seedtype/int y]
            (call-operator "+"
                           (call-operator "*" x x)
                           (call-operator "*" y y)))

(deftest my-sq-norm-test
  (is (= 25 (my-sq-norm 3 4))))