(ns geex.core.utils

  "This is the implementation of the *Polhem* compiler, along with fundamental tools.

  "
  
  (:require [bluebell.utils.wip.party :as party]
            [clojure.spec.alpha :as spec]
            [bluebell.utils.wip.traverse :as traverse]
            [bluebell.utils.wip.core :as utils]
            [clojure.pprint :as pp]
            [clojure.string :as cljstr]
            [bluebell.utils.wip.debug :as debug]
            [clojure.spec.test.alpha :as stest]
            [bluebell.utils.wip.party.coll :as partycoll]
            [geex.debug :refer [set-inspector inspect inspect-expr-map]]
            [bluebell.utils.wip.specutils :as specutils]
            [bluebell.utils.wip.trace :as trace]
            [geex.core.defs :as defs]
            [geex.core.seed :as sd]
            [geex.core.jvm :as gjvm]
            [geex.core.exprmap :as exm]
            [geex.core.datatypes :as datatypes]
            [geex.core.loop :as looputils]
            [geex.core.xplatform :as xp]
            [clojure.set :as cljset]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;   Definitions and specs
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Phases:
;;
;;  - The user builds a nested datastructure, where some values are seeds
;;      NOTE:
;;        - Symbols represent unknown values
;;  - We traverse the datastructure, and every seed becomes a seed
;;  - We remap the datastructure, assigning a symbol to every seed.
;;  - We build a graph
;;  - We traverse the graph from the bottom, compiling everything.


(def ^:dynamic scope-state nil)

(def contextual-gensym defs/contextual-gensym)

(def contextual-genkey (comp keyword contextual-gensym))

(def contextual-genstring (comp str contextual-gensym))


;;; Pass these as arguments to utils/with-flags, e.g.
;; (with-context []
;;  (utils/with-flags [debug-seed-names debug-seed-order]
;;    (compile-full
;;     (pure+ (pure+ 1 2) (pure+ 1 2))
;;     terminate-return-expr)))

(def ^:dynamic debug-seed-names false)
(def ^:dynamic debug-init-seed false)
(def ^:dynamic debug-check-bifurcate false)
(def ^:dynamic debug-full-graph false)
(def ^:dynamic with-trace false)

(defn wrap-expr-compiler [c]
  {:pre [(fn? c)]}
  (fn [comp-state expr cb]
    (cb (defs/compilation-result comp-state (c expr)))))

(def state-defaults {:platform :clojure
                     :disp-total-time? false})

(defn only-non-whitespace? [x]
  (->> x
      vec
      (map str)
      (not-any? cljstr/blank?)))

(def base-seed (-> {}
                   (sd/access-tags #{})
                   (sd/referents #{})
                   (sd/compiler nil)
                   (sd/datatype nil)
                   (defs/access-omit-for-summary [])))

(def access-original-coll (party/key-accessor :original-coll))

;;??
(defn value-literal-type [x]
  (if (symbol? x)
    defs/dynamic-type
    (datatypes/unboxed-class-of x)))


(defn ensure-seed? [x]
  (assert (sd/compilable-seed? x))
  x)

;;;;;; Analyzing an expression 
(def access-no-deeper-than-seeds
  (party/wrap-accessor
   {:desc "access-no-deeper-than-seeds"
    :getter (fn [x] (if (sd/seed? x)
                      []
                      x))
    :setter (fn [x y] (if (sd/seed? x)
                        x
                        y))}))

(def top-seeds-accessor
  (party/chain
   access-no-deeper-than-seeds
   partycoll/normalized-coll-accessor))

;;; Helper for flat-seeds-traverse

(defn selective-conj-mapping-visitor [pred-fn f]
  (fn [state x0]
    (let [x (symbol-to-seed x0)]
      (if (pred-fn x)
        [(conj state x) (f x)]
        [state x]))))

(defn flat-seeds-traverse
  "Returns a vector with first element being a list of 
  all original expr, the second being the expression
  with mapped seeds"
  [pred-fn expr f]
  (traverse/traverse-postorder-with-state
   [] expr
   {:visit (selective-conj-mapping-visitor pred-fn f)
    :access-coll top-seeds-accessor
    }))

;; Get a datastructure that represents this type.
(defn type-signature [x]
  (second
   (flat-seeds-traverse
    sd/seed?
    x
    sd/strip-seed)))

;; Get only the seeds, in a vector, in the order they appear
;; when traversing. Opposite of populate-seeds
(defn flatten-expr
  "Convert a nested expression to a vector of seeds"
  [x]
  (let [p (flat-seeds-traverse sd/seed? x identity)]
    (first p)))

(def size-of (comp count flatten-expr))

(defn populate-seeds-visitor
  [state x]
  (if (sd/seed? x)
    [(rest state) (first state)]
    [state x]))

(defn populate-seeds
  "Replace the seeds in dst by the provided list"
  ([dst seeds]
   (second
    (traverse/traverse-postorder-with-state
     seeds dst
     {:visit populate-seeds-visitor
      :access-coll top-seeds-accessor}))))

(defn map-expr-seeds
  "Apply f to all the seeds of the expression"
  [f expr]
  (let [src (flatten-expr expr)
        dst (map f src)]
    (assert (every? sd/seed? dst))
    (populate-seeds expr dst)))


(def access-bind-symbol (party/key-accessor :bind-symbol))

(defn inherit-datatype [x from]
  (defs/datatype x (defs/datatype from)))

(defn var-symbol [x]
  (-> x :var :name symbol))

;; Normalize something to a type such that we get the same type when we call rest on it.

(xp/register
 :clojure
 {:render-bindings
  (fn [tail body]
    `(let ~(reduce into [] (map (fn [x]
                                  [(:name x) (:result x)])
                                tail))
       ~body))

  :to-variable-name symbol

  :get-type-signature gjvm/get-type-signature
  :get-compilable-type-signature
  gjvm/get-compilable-type-signature

  :compile-coll
  (fn [comp-state expr cb]
    (cb (defs/compilation-result
          comp-state
          (partycoll/normalized-coll-accessor
           (access-original-coll expr)
           (exm/lookup-compiled-indexed-results comp-state expr)))))

  :compile-static-value
  (fn  [state expr cb]
    (cb (defs/compilation-result state (sd/static-value expr))))


  :declare-local-vars
  (fn [comp-state cb]
    (let [vars (::defs/local-vars comp-state)]
      (if (empty? vars)
        (cb comp-state)

        ;; Generate the code for local variables
        `(let ~(transduce
                (comp (map (comp :vars second))
                      cat
                      (map (fn [x] [(-> x :name symbol) `(atom nil)]))
                      cat)
                conj
                []
                vars)
           ~(cb (assoc comp-state ::defs/local-vars {}))))))

  :render-sequential-code
  (fn [code]
    `(do
       ~@code
       nil))

  :compile-bind
  (fn [comp-state expr cb]
    (cb (defs/compilation-result
          comp-state (access-bind-symbol expr))))


  :compile-bind-name
  (fn [x]
    (throw (ex-info "Not applicable for this platform" {:x x})))

  :compile-loop-header
  (fn [comp-state expr cb]
    (let [bindings (sd/access-indexed-deps expr)]
      `(loop ~(reduce
               into []
               (map (partial make-loop-binding
                             comp-state) bindings))
         ~(cb (defs/compilation-result
                comp-state
                (-> expr
                    defs/access-compiled-deps
                    :wrapped))))))

  :compile-return-value
  (fn [datatype expr]
    (throw (ex-info "Return value not supported on this platform"
                    {:datatype datatype
                     :expr expr})))

  :compile-nil
  (fn [comp-state expr cb]
    (cb (defs/compilation-result
          comp-state
          nil)))

  :check-compilation-result (constantly nil)
  
  })
