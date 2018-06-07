(ns lime.java

  "Generation of Java backed code"

  (:require [lime.java.defs :as jdefs]
            [lime.core :as lime]
            [lime.core.defs :as defs]
            [lime.platform.low :as low]
            [lime.platform.high :as high]
            [clojure.spec.alpha :as spec]
            [lime.core.seed :as seed]
            [lime.core :as core]
            [bluebell.utils.specutils :as specutils]
            [bluebell.utils.core :as utils]
            [lime.core.seed :as sd]
            [bluebell.tag.core :as tg]
            [clojure.reflect :as r]
            [clojure.string :as cljstr])
  (:import [org.codehaus.janino SimpleCompiler]))

(def platform-tag [:platform :java])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Implementation
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Unpacking
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def compact {:prefix " " :step ""})


(declare unpack)
(declare call-method)

(defn unpack-to-seed [dst-seed src-seed]
  (assert (sd/seed? src-seed))
  (assert (sd/seed? dst-seed))
  (let [dst-type (defs/datatype dst-seed)]
    (if (isa? (defs/datatype src-seed)
              dst-type)
      src-seed
      (high/cast-seed dst-type src-seed))))

(defn unpack-to-vector [dst-type src-seed]
  src-seed)


(defn unpack [dst-type src-seed]
  (assert (sd/seed? src-seed))
  (cond
    (sd/seed? dst-type) (unpack-to-seed dst-type src-seed)
    (vector? dst-type) (unpack-to-vector
                        dst-type
                        (unpack-to-seed
                         (sd/typed-seed clojure.lang.ISeq)
                         src-seed))))







(defn janino-cook-and-load-class [class-name source-code]
  "Dynamically compile and load Java code as a class"
  [class-name source-code]
  (try
    (let [sc (SimpleCompiler.)]
      (.cook sc source-code)
      (.loadClass (.getClassLoader sc) class-name))
    (catch Throwable e
      (throw (ex-info "Failed to compile code"
                      {:code source-code
                       :exception e})))))

(defn janino-cook-and-load-object  [class-name source-code]
  (.newInstance (janino-cook-and-load-class
                 class-name
                 source-code)))

(defn parse-typed-defn-args [args0]
  (specutils/force-conform ::jdefs/defn-args args0))

(defn java-class-name [parsed-args]
  (-> parsed-args
      :name
      name
      low/str-to-java-identifier))



(defn java-package-name [parsed-args]
  (-> parsed-args
      :ns
      low/str-to-java-identifier))

(defn full-java-class-name [parsed-args]
  (str (java-package-name parsed-args)
       "."
       (java-class-name parsed-args)))



(defn quote-arg-name [arg]
  (assert (map? arg))
  (merge arg
         {:name `(quote ~(:name arg))}))

(defn make-arg-decl [parsed-arg]
  (let [tp (:type parsed-arg)]
    [{:prefix " "
      :step ""}
     (r/typename (low/get-type-signature platform-tag tp))
     (low/to-variable-name platform-tag (:name parsed-arg))
     ]))

(defn join-args2
  ([]
   nil)
  ([c0 c1]
   (if (nil? c0)
     c1
     (into [] [c0 [", "] c1]))))

(defn join-args [args]
  (reduce join-args2 args))

(defn make-arg-list [parsed-args]
  (reduce join-args2 (map make-arg-decl parsed-args)))

(defn find-member-info [cl member-name0]
  (assert (class? cl))
  (let [member-name (symbol member-name0)]
    (->> cl
         clojure.reflect/reflect
         :members
         (filter #(= (:name %) member-name)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Interface
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn export-to-java [x0]
  (let [x (lime/to-seed x0)]))

(defn compile-call-method [comp-state expr cb]
  (cb
   (defs/compilation-result
     comp-state
     (high/wrap-in-parens
      [(:obj (sd/access-compiled-deps expr))
       "."
       (defs/access-method-name expr)
       (let [dp (sd/access-compiled-indexed-deps expr)]
         (high/wrap-in-parens [compact (join-args dp)]))]))))

(defn call-method [obj0 method-name & args0]
  (let [obj (lime/to-seed obj0)
        args (mapv lime/to-seed args0)
        cl (sd/datatype obj)
        arg-types (into-array java.lang.Class (mapv sd/datatype args))
        _ (println "cl" cl)
        _ (println "method-name" method-name)
        _ (println "arg-types" (vec  arg-types) )
        method (.getDeclaredMethod cl method-name arg-types)]
    (lime/with-new-seed
      "call-method"
      (fn [x]
        (-> x
            (sd/datatype (.getReturnType method))
            (sd/add-deps {:obj obj})
            (sd/access-indexed-deps args)
            (sd/compiler compile-call-method)
            (defs/access-method-name method-name))))))

;; (supers (class (fn [x] (* x x))))
;; #{java.lang.Runnable java.util.Comparator java.util.concurrent.Callable clojure.lang.IObj java.io.Serializable clojure.lang.AFunction clojure.lang.Fn clojure.lang.IFn clojure.lang.AFn java.lang.Object clojure.lang.IMeta}

(defn to-binding [quoted-arg]
  (let [tp (:type quoted-arg)
        _ (println "tp=" tp)
        t (low/get-type-signature platform-tag tp)]
    ;;; TODO: Get the type, depending on what...
    (unpack
     
     ;; The actual type used by us:
     tp 

     ;; A seed holding the raw runtime value
     (core/bind-name t (:name quoted-arg)))))


(defn generate-typed-defn [args]
  (let [arglist (:arglist args)
        quoted-args (mapv quote-arg-name arglist)]
    `(let [[top# code#] (lime/top-and-code
                         [{:platform :java}]
                         (core/return-value (apply
                                             (fn [~@(map :name arglist)]
                                               ~@(:body args))
                                             (map to-binding ~quoted-args))))]
       (utils/indent-nested
        [[{:prefix " "
           :step ""}
          "package " ~(java-package-name args) ";"]
         ~(str "public class " (java-class-name args) " {")
         ["public " (r/typename (low/get-type-signature platform-tag top#))
          " apply("
          (make-arg-list ~quoted-args)
          ") {"
          code#
          "}"]
         "}"]))))

(defn contains-debug? [args]
  (some (tg/tagged? :debug) (:meta args)))

(defmacro typed-defn [& args0]
  (let [args (merge (parse-typed-defn-args args0)
                    {:ns (str *ns*)})
        _ (println "args = " args)
        code (generate-typed-defn args)
        arg-names (mapv :name (:arglist args))]
    `(let [obj# (janino-cook-and-load-object ~(full-java-class-name args)
                                             ~code)]
       ~@(when (contains-debug? args)
           [`(println ~code)])
       (defn ~(:name args) [~@arg-names]
         (.apply obj# ~@arg-names)))))

(defmacro disp-ns []
  (let [k# *ns*]
    k#))

(comment
  (do

    (typed-defn return-primitive-number [(seed/typed-seed java.lang.Double) x]
                1)


    (typed-defn return-some-class [(seed/typed-seed java.lang.CharSequence) ch]
                ch)

    (typed-defn check-cast :debug [(seed/typed-seed java.lang.Object) obj]
                (unpack (seed/typed-seed java.lang.Double) obj))

   

    
    )


  )