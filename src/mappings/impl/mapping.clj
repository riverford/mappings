(ns mappings.impl.mapping
  (:require [mappings.impl.rule :as rule])
  (:import (clojure.lang Compiler$LocalBinding)))

(def ^:dynamic *ruleset-name* nil)

(defmacro mapping
  ([outspec inspec]
   `(mapping ~outspec ~inspec :fn identity :rfn identity))
  ([outspec inspec & opts]
   (if (= 1 (count opts))
     `(mapping ~outspec ~inspec :fn ~@opts)

     (let [opts (into {} (map vec (partition 2 opts)))

           {:keys [doc]} opts
           doc (or doc "Anonymous mapping")
           ns (.getName *ns*)

           fn-form (:fn opts)
           rfn-form (:rfn opts)

           env &env
           env-fn (get env fn-form)
           env-rfn (get env rfn-form)

           fn-local? (instance? Compiler$LocalBinding env-fn)
           rfn-local? (instance? Compiler$LocalBinding env-rfn)

           equiv? (and (and (not (coll? outspec))
                            (not (coll? inspec)))
                       (or
                         (and (= `identity fn-form)
                              (= `identity rfn-form))
                         (and (= 'identity fn-form)
                              (= 'identity rfn-form))
                         (and (nil? fn-form)
                              (nil? rfn-form))))

           fn-form (or fn-form `identity)

           fn-type (type fn-form)
           rfn-type (type rfn-form)

           inversable? (some? rfn-form)

           defaults-map `{:equiv? ~equiv?
                          :ns (quote ~ns)
                          :fn-form (quote ~fn-form)
                          :rfn-form (quote ~rfn-form)
                          :fn-local? ~fn-local?
                          :rfn-local? ~rfn-local?
                          :doc ~doc
                          :ruleset ~*ruleset-name*}
           inverse-defaults-map (merge
                                  defaults-map
                                  `{:fn-form (quote ~rfn-form)
                                    :rfn-form (quote ~fn-form)
                                    :fn-local? ~rfn-local?
                                    :rfn-local? ~fn-local?})]

       (if inversable?
         `(let [output# ~outspec
                input# ~inspec
                defaults-map# ~defaults-map
                inverse-defaults-map# ~inverse-defaults-map]
            [(merge
               (rule/rule output# input# ~fn-form)
               defaults-map#)
             (merge
               (rule/rule input# output# ~rfn-form)
               inverse-defaults-map#)])

         `(merge
            (rule/rule ~outspec ~inspec ~fn-form)
            ~defaults-map))))))

(defmacro mappings
  [& forms]
  (loop [acc []
         forms forms]
    (let [x (first forms)]
      (if (list? x)
        (recur (conj acc `(mapping ~@x))
               (rest forms))
        (let [doc x
              x (first (rest forms))
              next-ar (count x)]
          (if (nil? x)
            acc
            (recur
              (conj acc
                    (case next-ar
                      0 (throw (Exception. "Wrong arity for mapping form"))
                      1 (throw (Exception. "Wrong arity for mapping form"))
                      2 `(mapping ~@x :fn identity :rfn identity :doc ~doc)
                      3 `(mapping ~@(take 2 x) :fn ~(nth x 2) :doc ~doc)
                      `(mapping ~@x :doc ~doc)))
              (rest (rest forms)))))))))