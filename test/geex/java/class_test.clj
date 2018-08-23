(ns geex.java.class-test
  (:require [geex.java.class :refer :all :as jc]
            [clojure.spec.alpha :as spec]
            [clojure.test :refer :all]))

(deftest spec-test
  (is (spec/valid? ::jc/class-def '(ClassName)))
  (is (not (spec/valid? ::jc/class-def '(ClassName :kattskit))))
  (is (spec/valid? ::jc/class-def '(Class [:private])))
  (is (spec/valid? ::jc/class-def '(Class [:private] [:public])))
  (is (spec/valid? ::jc/classes '[a b c]))
  (is (spec/valid? ::jc/class-def '(ClassName :implements [a b c]
                                              ;:extends [x y z]
                                              )))
  (is (spec/valid? ::jc/class-def '(ClassName :implements [Kattskit]
                                              :static (getX [] 119)
                                              (getY [] 120))))
  (is (spec/valid? ::jc/class-def '(ClassName :implements [Kattskit]
                                              (getY [] 120))))
  (is (spec/valid? ::jc/class-def '(ClassName :implements [Kattskit]
                                              [:private (getY [] 120)])))
  (is (spec/valid? ::jc/class-def '(ClassName :implements [Kattskit]
                                              [:private (getY [] 120)])))
  (is (spec/valid? ::jc/class-def '(ClassName :implements [Kattskit]
                                              :extends [Mjao]
                                              [:private {:a Double/TYPE} mjao
                                               (getX [Double/Type k] k)]
                                              [:public :static {:a Double/TYPE} mjao]))))
