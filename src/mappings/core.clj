(ns mappings.core
  (:require [clojure.set :as set]))

(defn- calculate-id
  [provides required-keys optional-keys]
  [::rule provides required-keys optional-keys])

(defn- coerce-requires-form
  [requires]
  (cond
    (keyword? requires) [requires]
    (vector? requires) requires
    :else [requires]))

(defn- coerce-provides
  [provides]
  (cond
    (keyword? provides) #{provides}
    (vector? provides) (set provides)
    :else #{provides}))

(defonce id-counter (atom -1))

(defn transform
  ([outspec inspec f]
   (let [require-form (coerce-requires-form inspec)
         required-keys (set require-form)
         optional-keys #{}
         provides (coerce-provides outspec)]
     {::id (calculate-id provides required-keys optional-keys)
      ::nid (swap! id-counter inc)
      ::provides provides
      ::input inspec
      ::output outspec
      ::required-keys required-keys
      ::optional-keys optional-keys
      ::check (case (count required-keys)
                0 (constantly true)
                1 (let [required-key (first required-keys)]
                    #(contains? % required-key))
                (fn [in] (every? #(contains? in %) required-keys)))
      ::f
      ;; ordinal case
      (let [get-args (apply juxt (map (fn [k] #(get % k)) require-form))]
        (case (count provides)
          0 (throw (IllegalArgumentException. "Rule must provide at least one key"))
          1 (let [provided-key (first provides)]
              (fn apply-transform [in]
                (let [args (get-args in)
                      v (apply f args)]
                  (if (some? v)
                    (assoc! in provided-key v)
                    in))))))}))
  ([provides requires f & args] (transform provides requires #(apply f % args))))

(defn- coerce-rules
  [x]
  (cond
    (nil? x) x
    (map? x) (cond
               (::id x) [x]
               (::rules x) (vals (::rules x))
               :else (throw (Exception. "Cannot create rule(s) from map")))
    (coll? x) (mapcat coerce-rules x)
    :else x))

(defn- remove-rule
  [ruleset id]
  (let [{:keys [::rules]} ruleset
        {:keys [::provides]} (get rules id)
        ruleset (update ruleset ::rules dissoc id)

        rf (fn [ruleset provided-key]
             (let [set (get-in ruleset [::provision provided-key])
                   new-set (disj set id)]
               (if (empty? new-set)
                 (update ruleset ::provision dissoc provided-key)
                 (assoc-in ruleset [::provision provided-key] new-set))))
        ruleset (reduce rf ruleset provides)]
    ruleset))

(defn- add-rule*
  [ruleset rule]
  (let [{:keys [::rules]} ruleset
        {:keys [::provides
                ::id]} rule]
    (if (contains? rules id)
      (add-rule* (remove-rule ruleset id) rule)
      (let [ruleset (assoc-in ruleset [::rules id] rule)
            rf (fn [ruleset provided-key]
                 (update-in ruleset [::provision provided-key] (fnil conj #{}) id))
            ruleset (reduce rf ruleset provides)]
        ruleset))))

(defn add
  ([ruleset rule]
   (if (::id rule)
     (add-rule* ruleset rule)
     (reduce add-rule* ruleset (coerce-rules rule))))
  ([ruleset rule & rules]
   (reduce add (add ruleset rule) rules)))

(defn rules
  ([] {})
  ([& mappings] (reduce add (rules) mappings)))

(defn- compile-selection
  [ruleset keyseq seen]
  (if (empty? keyseq)
    identity
    (let [{:keys [::provision
                  ::rules]
           :or {rules {}
                provision {}}} ruleset

          keyset (set keyseq)

          all-deps (set (for [needed-key keyset
                              rule-id (get provision needed-key)
                              :let [rule (get rules rule-id)]
                              required-key (::required-keys rule)]
                          required-key))

          rules (into {} (for [needed-key keyset
                               :let [rule-ids (get provision needed-key)
                                     rules (into [] (comp (distinct) (map rules)) rule-ids)]]
                           [needed-key rules]))

          all-deps (set/difference all-deps seen)
          seen (set/union seen all-deps)
          dep-selection (compile-selection ruleset all-deps seen)]
      (if (empty? keyset)
        identity
        (fn run-selection [m]
          (reduce
            (fn [m needed-key]
              (if-some [ev (get m needed-key)]
                (assoc! m needed-key ev)
                (if-some [valid-rules (get rules needed-key)]
                  (if (= 1 (count valid-rules))
                    (let [rule (nth valid-rules 0)
                          check (::check rule)
                          f (::f rule)]
                      (if (check m)
                        (f m)
                        m))
                    (reduce
                      (fn [m rule]
                        (let [check (::check rule)
                              f (::f rule)]
                          (if (check m)
                            (f m)
                            m)))
                      m
                      valid-rules))
                  m)))
            (dep-selection m)
            keyset))))))

;; -- rules

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

           equiv? (or (and (keyword? outspec)
                           (keyword? inspec))
                      (and (= 'identity fn-form)
                           (= 'identity rfn-form))
                      (and (nil? fn-form)
                           (nil? rfn-form)))

           fn-form (or fn-form 'identity)

           fn-type (type fn-form)
           rfn-type (type rfn-form)

           inversable? (some? rfn-form)

           defaults-map `{::equiv? ~equiv?
                          ::ns (quote ~ns)
                          ::fn-form (quote ~fn-form)
                          ::rfn-form (quote ~rfn-form)
                          ::doc ~doc}
           inverse-defaults-map (merge
                                  defaults-map
                                  `{::fn-form (quote ~rfn-form)
                                    ::rfn-form (quote ~fn-form)})]

       (if inversable?
         `(let [output# ~outspec
                input# ~inspec
                defaults-map# ~defaults-map
                inverse-defaults-map# ~inverse-defaults-map]
            [(merge
               (transform output# input# ~fn-form)
               defaults-map#)
             (merge
               (transform input# output# ~rfn-form)
               inverse-defaults-map#)])

         `(merge
            (transform ~outspec ~inspec ~fn-form)
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

(defonce ^:private global-rules
  (atom (rules)))

(defn add-global-rule
  [rule]
  (swap! global-rules add rule)
  nil)

(defmacro defmappings
  [& forms]
  `(add-global-rule (mappings ~@forms)))

(defn selection
  ([keyseq] (selection @global-rules keyseq))
  ([ruleset keyseq]
   (let [f (compile-selection ruleset keyseq #{})]
     (fn [m] (select-keys (persistent! (f (transient m))) keyseq)))))

(defn select
  ([m keyseq] (select m @global-rules keyseq))
  ([m ruleset keyseq]
   ((selection ruleset keyseq) m)))

(comment

  (rules

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