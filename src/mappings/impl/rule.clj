(ns mappings.impl.rule)

(defn- calculate-id
  [provides required-keys]
  [::rule provides required-keys])

(defn- inspec-requirements
  [inspec]
  (cond
    (keyword? inspec) {:req #{inspec} :opt #{}}
    (vector? inspec) {:req (set inspec) :opt #{}}
    (set? inspec) {:req (set inspec) :opt #{}}
    (map? inspec) inspec
    :else {:req #{inspec}}))

(defn- outspec-provides
  [outspec]
  (cond
    (keyword? outspec) #{outspec}
    (vector? outspec) (set outspec)
    (set? outspec) outspec
    :else #{outspec}))

(defonce ^:private id-counter (atom -1))

(defn- check-fn
  [required-keys]
  (case (count required-keys)
    0 (constantly true)
    1 (let [required-key (first required-keys)]
        #(contains? % required-key))
    (fn [in] (every? #(contains? in %) required-keys))) )

(defn- run-fn
  [outspec eval-fn]
  (cond
    (vector? outspec) (run-fn (set outspec) eval-fn)
    (set? outspec) (fn [m] (if-some [m2 (eval-fn m)]
                             (reduce-kv assoc! m m2)
                             m))
    :else
    (fn [m]
      (if-some [x (eval-fn m)]
        (assoc! m outspec x)
        m))))

(defn- positional-run-fn
  [inspec f]
  (case (count inspec)
    0 f
    1 (let [[a] inspec]
        (fn [m] (f (get m a))))
    2 (let [[a b] inspec]
        (fn [m] (f (get m a) (get m b))))
    3 (let [[a b c] inspec]
        (fn [m] (f (get m a) (get m b) (get m c))))
    (let [args (apply juxt (map (fn [k] (fn [m] (get m k))) inspec))]
      (fn [m] (apply f (args m))))))

(defn- mapin-run-fn
  [inspec f]
  f)

(defn- eval-fn
  [inspec f]
  (cond
    (vector? inspec) (positional-run-fn inspec f)
    (set? inspec) (mapin-run-fn inspec f)
    (map? inspec) (mapin-run-fn inspec f)
    :else (positional-run-fn [inspec] f)))

(defn- run-rule-fn
  [check-fn run-fn]
  (fn [m]
    (if (check-fn m)
      (run-fn m)
      m)))

(defn rule
  [outspec inspec f]
  (let [requirements (inspec-requirements inspec)
        required-keys (:req requirements #{})
        optional-keys (:opt requirements #{})
        provides (outspec-provides outspec)
        check-fn (check-fn required-keys)
        eval-fn (eval-fn inspec f)
        run-fn (run-fn outspec eval-fn)]
    {::id (calculate-id provides required-keys)
     ::nid (swap! id-counter inc)
     ::provides provides
     ::inspec inspec
     ::outspec outspec
     ::required-keys required-keys
     ::optional-keys optional-keys
     ::f (run-rule-fn check-fn run-fn)}))

(defn execute!
  [transient-map rule]
  (let [f (::f rule)]
    (f transient-map)))

(defn ruleform
  [rule]
  (let [{:keys [::inspec
                ::outspec
                :equiv?
                :fn-form
                :rfn-form]} rule]

    (cond
      equiv? (list outspec inspec)
      rfn-form (list outspec inspec :fn fn-form :rfn rfn-form)
      :else (list outspec inspec fn-form))))