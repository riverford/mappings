(ns mappings.core
  (:require [mappings.impl.rule :as rule]
            [mappings.impl.global :as global]
            [mappings.impl.compile :as compile]
            [mappings.impl.ruleset :as ruleset]))

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

           equiv? (and (and (not (coll? outspec))
                            (not (coll? inspec)))
                       (or
                         (and (= 'identity fn-form)
                              (= 'identity rfn-form))
                         (and (nil? fn-form)
                              (nil? rfn-form))))

           fn-form (or fn-form 'identity)

           fn-type (type fn-form)
           rfn-type (type rfn-form)

           inversable? (some? rfn-form)

           defaults-map `{:equiv? ~equiv?
                          :ns (quote ~ns)
                          :fn-form (quote ~fn-form)
                          :rfn-form (quote ~rfn-form)
                          :doc ~doc}
           inverse-defaults-map (merge
                                  defaults-map
                                  `{:fn-form (quote ~rfn-form)
                                    :rfn-form (quote ~fn-form)})]

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

(defmacro defmappings
  [& forms]
  `(global/add-rule (mappings ~@forms)))

(defn selection
  ([keyseq] (selection (global/get-ruleset) keyseq))
  ([ruleset keyseq]
   (let [f (compile/selection ruleset keyseq #{})]
     (fn [m] (persistent! (f (transient m)))))))

(defn select
  ([m keyseq] (select m (global/get-ruleset) keyseq))
  ([m ruleset keyseq]
   ((selection ruleset keyseq) m)))

(defn ruleset
  [& rules]
  (ruleset/ruleset))

(comment

  (ruleset

    ;; identity mapping same as
    ;; (mapping :a :b :fn identity, :rfn identity)
    (mapping :b :a)

    (mapping :c :b :fn inc :rfn dec)

    (mapping :d :a :fn inc)
    ;; arg 4 > n assumes named args
    (mapping :d :a :fn inc :doc "Hello world")
    ;; single way transform is ok without fn
    (mapping :d :a inc)


    ;; expands to identity mappings
    (equiv
      :a :b
      :e :f
      :e :b)

    ;; positional deps to :fn
    (mapping
      :sum [:a :b :c] +)

    ;; multiout
    (mapping
      #{:foo :bar}
      [:a :b :c]
      :fn some-fn)

    ;; unordered deps received as map
    (mapping
      :sum
      #{:a :b :c}
      :fn map-sum)

    ;; req + optional deps
    (mapping
      :sum
      {:req #{:a :b :c} :opt #{:e :f}}
      :fn super-sum)

    ;; terser form for many mappings, docstrings can be used between
    ;; optionally and will be spliced into next form :doc
    (mappings
      "b is a incremented"
      (:b :a :fn inc)
      ;;same as
      (:b :a inc)

      "e is f"
      (:e :f))))

(comment
  (let [rules
        (ruleset/ruleset
          (mappings (:b :a :fn inc :rfn dec)
                    (:c [:a :b] +)))

        f (selection
            rules
            [:a :c])]
    (f {:b 42})))