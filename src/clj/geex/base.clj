(ns geex.base

  "Main API for using Geex independently of which platform you are using it for."
  
  (:require [geex.core :as core]
            [clojure.core :as c]
            [clojure.spec.alpha :as spec]
            [geex.core.seed :as seed]
            [geex.core.defs :as defs]
            [geex.core.datatypes :as dt]
            [geex.core.xplatform :as xp]
            [geex.ebmd.type :as gtype]
            [bluebell.utils.ebmd :as ebmd]
            [bluebell.utils.wip.debug :as debug]
            [bluebell.utils.render-text :as render-text]
            [bluebell.utils.ebmd.type :as etype]
            [geex.java.defs :as jdefs]
            [clojure.pprint :as pp])
  (:refer-clojure :only [defn
                         fn
                         apply
                         defmacro
                         case
                         comp
                         identity
                         fn?
                         let
                         map?
                         ->]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Code private to this file
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Specs
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Common stuff
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ebmd/declare-def-poly
 disp-sub [etype/any x]
 (pp/pprint x)
 x)

(defmacro disp [x]
  `(do
     (c/println "\nDISP " ~(c/str x))
     (disp-sub ~x)))

(defn seed-wrapper [predicate]
  (fn [x]
    (if (c/or (seed/seed? predicate)
              (c/not (predicate x)))
      x
      (core/to-seed x))))

(def number-to-seed (seed-wrapper c/number?))
(def char-to-seed (seed-wrapper c/char?))
(def string-to-seed (seed-wrapper c/string?))
(def keyword-to-seed (seed-wrapper c/keyword?))
(def symbol-to-seed (seed-wrapper c/symbol?))

(defn wrap-args [wrapper f]
  (fn [& args]
    (debug/exception-hook
     (apply f (c/map wrapper args))
     (render-text/disp
      (render-text/add-line "Error for these raw args")
      (render-text/pprint args)))))

(def wrap-numeric-args (c/partial wrap-args number-to-seed))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Various utilities
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro generalizable-fn [name arglist & body]
  `(do
     (ebmd/declare-poly ~name)
     (ebmd/def-poly ~name
       ~(c/reduce c/into []
                  (c/mapv (fn [a]
                            ['etype/any a])
                          arglist))
       ~@body)))

(c/defn- make-arglist [arg-spec n]
  (c/mapv (fn [i] (c/symbol (c/str "arg" i))) (c/range n)))

(defmacro generalize-fn [new-name arg-spec arg-count specific-name]
  (let [arg-symbols (make-arglist arg-spec arg-count)
        arg-types (c/vec (c/take arg-count (c/repeat arg-spec)))
        arg-list (c/reduce c/into [] (c/map c/vector arg-types arg-symbols))]
    `(ebmd/declare-def-poly ~new-name ~arg-list
                            (~specific-name ~@arg-symbols))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Polymorphic functions
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def xp-numeric (comp wrap-numeric-args xp/caller))


(generalize-fn bit-not etype/any 1 (xp-numeric :bit-not))
(generalize-fn bit-shift-left etype/any 2 (xp-numeric :bit-shift-left))
(generalize-fn unsigned-bit-shift-left etype/any 2
               (xp-numeric :unsigned-bit-shift-left))
(generalize-fn bit-shift-right etype/any
               2 (xp-numeric :bit-shift-right))
(generalize-fn unsigned-bit-shift-right etype/any 2
               (xp-numeric :unsigned-bit-shift-right))


(generalize-fn binary-bit-flip gtype/maybe-seed-of-primitive 2 (xp-numeric :bit-flip))
(generalize-fn binary-bit-and gtype/maybe-seed-of-primitive 2 (xp-numeric :bit-and))
(generalize-fn binary-bit-or gtype/maybe-seed-of-primitive 2 (xp-numeric :bit-or))

(generalize-fn negate gtype/maybe-seed-of-number 1 (xp-numeric :negate))
(generalize-fn binary-add gtype/maybe-seed-of-number 2 (xp-numeric :binary-add))
(generalize-fn unary-add gtype/maybe-seed-of-number 1 (xp-numeric :unary-add))
(generalize-fn binary-sub gtype/maybe-seed-of-number 2 (xp-numeric :binary-sub))
(generalize-fn binary-div gtype/maybe-seed-of-number 2 (xp-numeric :binary-div))
(generalize-fn binary-mul gtype/maybe-seed-of-number 2 (xp-numeric :binary-mul))

(def basic-random (xp/caller :basic-random))


(defmacro generalize-binary-op [name
                                op
                                args
                                zero-arg-output
                                one-arg-output]
  `(defn ~name [& ~args]
     (c/case (c/count ~args)
       0 ~zero-arg-output
       1 ~one-arg-output
       (c/reduce ~op ~args))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Outer API
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;





;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Forward decls
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def typed-seed seed/typed-seed)
(def nil-of core/nil-of)
(def wrap core/to-seed)
(def unwrap (xp/caller :unwrap))

(def void (xp/caller :make-void))

(def nil? core/basic-nil?)

(def call-method (xp/caller :call-method))

(def flatten-expr core/flatten-expr)
(def size-of core/size-of)
(def populate-seeds core/populate-seeds)

;;;------- Common math operators -------

(generalize-binary-op + binary-add args
                      0
                      (unary-add (c/first args)))

(generalize-binary-op - binary-sub args
                      0
                      (negate (c/first args)))

(defn insufficient-number-of-args [op-name]
  
  (throw
   (c/ex-info
    (c/str "Insufficient number of arguments to '"
           op-name
           "'")
    {})))

(generalize-binary-op / binary-div args
                      (insufficient-number-of-args "/")
                      (c/first args))

(generalize-binary-op * binary-mul args
                      1
                      (c/first args))

(generalize-binary-op bit-and binary-bit-and args
                      (insufficient-number-of-args
                       "bit-and")
                      (insufficient-number-of-args
                       "bit-and"))

(generalize-binary-op bit-or binary-bit-or args
                      (insufficient-number-of-args
                       "bit-or")
                      (insufficient-number-of-args
                       "bit-or"))

(generalize-binary-op bit-flip binary-bit-flip args
                      (insufficient-number-of-args
                       "bit-flip")
                      (insufficient-number-of-args
                       "bit-flip"))



;;;------- Platform properties -------
(def int-type (xp/caller :int-type))
(def float-type (xp/caller :float-type))
(def size-type (xp/caller :size-type))



;;;------- Errors -------

(defn error [message]
  (xp/call :error message))

(defmacro check
  ([condition]
   (check condition "(no message)"))
  ([condition message]
   {:pre [(c/string? message)]}
   (let [full-message (c/format "CHECK '%s' FAILED: %s"
                                (c/str condition)
                                message)]
     `(core/If ~condition
               [] ;; TODO: What about void???
               (do (error ~full-message)
                   [])))))


;;;------- More math functions -------
(defn inc [x]
  (+ x 1))

(defn dec [x]
  (- x 1))

(defn sqr [x]
  (* x x))

(generalize-fn quot gtype/maybe-seed-of-number 2 (xp-numeric :quot))
(generalize-fn rem gtype/maybe-seed-of-number 2 (xp-numeric :rem))

(defmacro math-functions-from-java []
  `(do
     ~@(c/map
        (fn [[k _ arg-count]]
          (let [sym (-> k
                        c/name
                        c/symbol)]
            (c/assert (c/symbol? sym))
            `(generalize-fn ~sym gtype/maybe-seed-of-number ~arg-count (xp-numeric ~k))))
        jdefs/math-functions)))
(math-functions-from-java)


;;;------- Comparison operators -------

(generalize-fn == etype/any  2 (xp-numeric :==))
(generalize-fn <= gtype/maybe-seed-of-primitive  2 (xp-numeric :<=))
(generalize-fn >= gtype/maybe-seed-of-primitive  2 (xp-numeric :>=))
(generalize-fn > gtype/maybe-seed-of-primitive  2 (xp-numeric :>))
(generalize-fn < gtype/maybe-seed-of-primitive 2 (xp-numeric :<))
(generalize-fn != etype/any 2 (xp-numeric :!=))

(generalize-fn = etype/any 2 (xp/caller :=))

(generalize-fn finite? gtype/maybe-seed-of-primitive 1 (xp-numeric :finite?))
(generalize-fn infinite? gtype/maybe-seed-of-primitive 1 (xp-numeric :infinite?))
(generalize-fn nan? gtype/maybe-seed-of-primitive 1 (xp-numeric :nan?))



;;;------- More math functions -------

(defn pos? [x]
  (< 0 x))

(defn neg? [x]
  (< x 0))

(defn zero? [x]
  (== x 0))

(ebmd/declare-def-poly
 mod [gtype/maybe-seed-of-number a
      gtype/maybe-seed-of-number b]
 (let [c (rem a b)]
   (core/If (< c 0)
            (+ c b)
            c)))

;;;------- Logic operators -------

(defmacro and [& args]
  (if (c/empty? args)
    `(core/to-seed true)
    `(core/If ~(c/first args)
              (and ~@(c/rest args))
              (core/to-seed false))))

(defmacro or [& args]
  (if (c/empty? args)
    `(core/to-seed false)
    `(core/If ~(c/first args)
              (core/to-seed true)
              (or ~@(c/rest args)))))

(def not (xp/caller :not))

(def not= (comp not =))

(defmacro implies [a b]
  `(or (not ~a) ~b))




;;;------- Array functions -------
(def array-class dt/array-class)


(def make-array (xp/caller :make-array))

(ebmd/declare-poly aget)

(ebmd/def-poly aget [gtype/array-seed x
                     gtype/maybe-seed-of-integer i]
  (xp/call :aget x i))

(ebmd/declare-poly aset)

(ebmd/def-poly aset [gtype/array-seed x
                     gtype/maybe-seed-of-integer i
                     etype/any value]
  (xp/call :aset x i value))

(ebmd/declare-poly alength)
(ebmd/def-poly alength [gtype/array-seed x]
  (xp/call :alength x))


;;;------- Collection functions -------

(generalizable-fn conj [dst x]
  (xp/call :conj dst x))

(generalizable-fn seq [x]
  (xp/call :seq x))


(ebmd/declare-poly empty?)
(ebmd/def-poly empty? [etype/any x]
  (nil? (seq x)))


(ebmd/declare-poly first)

(ebmd/def-poly first [etype/any x]
  (xp/call :first x))

(ebmd/declare-poly rest)

(ebmd/def-poly rest [etype/any x]
  (xp/call :rest x))

(ebmd/declare-poly count)

(ebmd/def-poly count [etype/any x]
  (xp/call :count x))


(generalizable-fn cast [dst-type src-value]
  (core/cast dst-type src-value))

;; Mainly when working with array indices
(defn to-size-type [x]
  (cast (size-type) x))



;; Normalize a value to a type such that when we apply rest, we get the same type back.


(ebmd/declare-poly iterable)

(ebmd/def-poly iterable [etype/any x]
  (xp/call :iterable x))

(defn result-vector
  "Returns an empty vector suitable for conj-ing into."
  []
  (cast clojure.lang.IPersistentCollection (wrap [])))








;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Iteration
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn reduce
  ([f input0]
   (c/let [input (iterable input0)]
     (reduce f (first input) (rest input))))
  ([f result input]
   (core/Loop
    [result result
     remain (iterable input)]
    (core/If (empty? remain)
             result
             (core/Recur
              (f result
                 (first remain))
              (rest remain))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Transducers
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn wrapped-step? [x]
  ;;; See jo-reproduced-bug!!!!
  (c/and (map? x)
       (fn? (:wrap x))
       (fn? (:unwrap x))
       (fn? (:step x))))

(defn bad-wrapped-step? [x]
  ;;; See jo-reproduced-bug!!!!
  (and true                             ;(map? x)
       true
                                        ;(fn? (:wrap x))
                                        ;(fn? (:unwrap x))
                                        ;(fn? (:step x))
       ))

(defn wrap-step [step]

  ;; THIS IS GOOD
  {:pre [(c/or (wrapped-step? step)
                 (fn? step))]}

  ;; This used to be BAD, is it still???
  ;{:pre [
  
  (if (fn? step)
    {:wrap identity
     :unwrap identity
     :step step}
    step))

(defn map [f]
  {:pre [(fn? f)]}
  (fn [s]
    {:pre [(wrapped-step? s)]}
    (c/update s :step (fn [step] (fn [result x] (step result (f x)))))))

(defn filter [f]
  {:pre [(fn? f)]}
  (fn [s]
    {:pre [(wrapped-step? s)                                        
           ]}
    (c/update s :step (fn [step]
                        (fn [result x]
                          (core/If (f x)
                              (step result x)
                              result))))))

(defn transduce [transduce-function
                 step-function
                 accumulator
                 src-collection]
  (bad-wrapped-step? step-function)
  (let [tr (transduce-function (wrap-step step-function))]
    ((:unwrap tr)
     (reduce (:step tr)
             ((:wrap tr) accumulator)
             src-collection))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Sliceable array
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn sliceable-array
  ([src-array]
   (sliceable-array src-array (alength src-array)))
  ([src-array size]
   (sliceable-array src-array size (wrap (c/int 0))))
  ([src-array size offset]
   (let [k {:type :sliceable-array
            :data src-array
            :size (to-size-type size)
            :offset (to-size-type offset)}]
     k)))

(def sliceable-array-arg (gtype/map-with-key-value
                          :type :sliceable-array))

(ebmd/def-poly count [sliceable-array-arg arr]
  (:size arr))

(ebmd/def-poly first [sliceable-array-arg arr]
  (aget (:data arr) (:offset arr)))

(ebmd/def-poly rest [sliceable-array-arg arr]
  (c/merge arr
           {:size (to-size-type (dec (:size arr)))
            :offset (to-size-type (inc (:offset arr)))}))

(ebmd/def-poly iterable [gtype/array-seed x]
  (sliceable-array x))

(ebmd/def-poly empty? [sliceable-array-arg arr]
  (== 0 (:size arr)))

(ebmd/declare-poly slice)

(ebmd/def-poly slice [sliceable-array-arg arr
                      gtype/maybe-seed-of-integer from
                      gtype/maybe-seed-of-integer to]
  (c/merge arr
           {:offset (to-size-type (+ (:offset arr) from))
            :size (to-size-type (- to from))}))


(ebmd/def-poly slice [gtype/array-seed x
                      gtype/maybe-seed-of-integer from
                      gtype/maybe-seed-of-integer to]
  (slice (sliceable-array x) from to))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  More slicing
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn slice-from [src from]
  (slice src from (count src)))

(defn slice-to [src to]
  (slice src 0 to))

(defn slice-but [src n]
  (slice-to src (- (count src) n)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Ranges
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn range
  ([n] (range (c/long 0) n))
  ([lower upper] (range lower upper (c/long 1)))
  ([lower0 upper0 step0]
   (let [lower (wrap lower0)
         upper (wrap upper0)
         step (wrap step0)]
     {:type :range
      :offset lower
      :size (/ (- upper lower) step)
      :step step})))

(def range-arg (gtype/map-with-key-value :type :range))

(ebmd/def-poly count [range-arg x]
  (:size x))

(ebmd/def-poly first [range-arg x]
  (c/assert (map? x))
  (:offset x))

(ebmd/def-poly rest [range-arg x]
  (c/merge x
           {:offset (+ (:offset x) (:step x))
            :size (dec (:size x))}))

(ebmd/def-poly iterable [range-arg x] x)

(ebmd/def-poly empty? [range-arg x]
  (<= (:size x) 0))

(ebmd/def-poly aget [range-arg x
                     gtype/maybe-seed-of-integer i]
  (+ (:offset x)
     (* i (:step x))))

(ebmd/def-poly slice [range-arg x
                      gtype/maybe-seed-of-integer from
                      gtype/maybe-seed-of-integer to]
  (c/merge x
           {:offset (+ (:offset x)
                       (* from (:step x)))
            :size (- to from)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Structured arrays
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn wrap-struct-array [type src-data]
  (let [struct-size (size-of type)]
    {:data src-data
     :type :struct-array
     :public-type type
     ::struct-size struct-size
     :size (to-size-type (quot (cast Long/TYPE (alength src-data))
                         (cast Long/TYPE struct-size)))
     :offset (wrap (c/int 0))}))

(ebmd/def-arg-spec struct-size-key-arg
  {:pred (c/partial c/= ::struct-size)
   :pos [::struct-size]
   :neg [:kattskit]})

(ebmd/def-poly core/wrap-at-key? [struct-size-key-arg _]
  false)

(defn make-struct-array [public-type private-type size]
  (wrap-struct-array
   public-type (make-array private-type
                           (* size (size-of public-type)))))

(defn populate-and-cast [dst-type src]
  {:pre [(c/vector? src)]}
  (let [flat-dst (flatten-expr dst-type)]
    (c/assert (c/= (c/count flat-dst)
                   (c/count src)))
    (populate-seeds
     dst-type
     (c/map (fn [d s]
              (cast (seed/datatype d) s))
            flat-dst
            src))))

(defn compute-struct-array-offset [src i]
  (+ (* i (::struct-size src))
     (:offset src)))

(defn aget-struct-array [arr i]
  (let [at (compute-struct-array-offset arr i)]
    (populate-and-cast
     (:public-type arr)
     (c/vec
      (c/map (fn [p] (aget (:data arr) (to-size-type (+ at p))))
             (c/range (::struct-size arr)))))))

(def struct-array-arg (gtype/map-with-key-value
                       :type :struct-array))

(ebmd/def-poly aget [struct-array-arg arr
                     gtype/maybe-seed-of-integer i]
  (aget-struct-array arr i))


(defn aset-struct-array [arr i x]
  (let [data (:data arr)
        inner-type (dt/component-type (seed/datatype data))
        at (compute-struct-array-offset arr i)
        flat-x (flatten-expr x)
        n (::struct-size arr)]
    (c/assert (c/number? n))
    (c/assert (c/= (c/count flat-x) n))
    (c/doseq [i (c/range n)]
      (aset data i (cast inner-type (c/nth flat-x i))))))

(ebmd/def-poly aset [struct-array-arg arr
                     gtype/maybe-seed-of-integer i
                     etype/any x]
  (aset-struct-array arr i x))

(ebmd/def-poly count [struct-array-arg arr]
  (:size arr))

(ebmd/def-poly first [struct-array-arg arr]
  (aget-struct-array arr 0))

(ebmd/def-poly rest [struct-array-arg arr]
  (c/merge arr {:offset (+ (::struct-size arr)
                           (:offset arr))
                :size (to-size-type (dec (:size arr)))}))

(ebmd/def-poly iterable [struct-array-arg x] x)

(ebmd/def-poly empty? [struct-array-arg x]
  (<= (:size x) 0))

(ebmd/def-poly slice [struct-array-arg arr
                      gtype/maybe-seed-of-integer lower
                      gtype/maybe-seed-of-integer upper]
  (c/merge arr
           {:size (- upper lower)
            :offset (+ (:offset arr)
                       (* lower (::struct-size arr)))}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  More control structures
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn iterate-while [initial-state
                     next-state-fn
                     loop-condition-fn]
  {:pre [(fn? next-state-fn)
         (fn? loop-condition-fn)]}
  (core/Loop [state initial-state]
             (core/If (loop-condition-fn state)
                      (core/Recur (next-state-fn state))
                      state)))

(defn iterate-times [n init-state next-state]
  {:pre [(fn? next-state)]}
  (core/Loop [n n
              state init-state]
             (core/If (< 0 n)
                      (core/Recur (dec n) (next-state state))
                      state)))

(defn iterate-until [initial-state
                     next-state-fn
                     stop-condition-fn]
  {:pre [(fn? stop-condition-fn)]}
  (iterate-while initial-state next-state-fn
                 (c/comp not stop-condition-fn)))

(defmacro doseq [[item input-seq] & body]
  `(do
     (reduce
      (fn [result# ~item]
        ~@body
        result#)
      []
      ~input-seq)
     (void)))