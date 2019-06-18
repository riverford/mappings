(ns mappings.core
  (:require [mappings.impl.mapping :as mapping]
            [mappings.impl.global :as global]
            [mappings.impl.compile :as compile]
            [mappings.impl.ruleset :as ruleset]
            [mappings.impl.rule :as rule]
            [clojure.string :as str]))

(defn- dissoc-nils
  [m]
  (reduce-kv (fn [m k v] (if (nil? v) (dissoc m k) m)) m m))

(defn providing
  "Returns data about rules providing the given key"
  ([k] (providing :default k))
  ([ruleset k]
   (let [ruleset (if (keyword? ruleset)
                   (global/get-rules ruleset)
                   ruleset)
         rules (ruleset/get-providing ruleset k)]
     (for [rule rules]
       (dissoc-nils
         (merge
           {:rid (::rule/nid rule)
            :ns (:ns rule)
            :doc (:doc rule)}
           (if (:equiv? rule)
             {:equiv (first (::rule/required-keys rule))}
             {:req (::rule/required-keys rule)
              :opt (::rule/optional-keys rule)
              :fn (:fn-form rule)
              :rfn (:rfn-form rule)})))))))

(defn- assert-req
  [ruleset m req selection-name ns-name]
  (let [missing-keys (reduce (fn [acc k] (if (contains? m k) acc (conj acc k))) [] req)]
    (when-not (= 0 (count missing-keys))
      (let [ks (try (sort missing-keys) (catch Throwable _ missing-keys))
            msg (if selection-name
                  (str "Selection (" selection-name ", " ns-name ") could not provide required key(s)\n  - " (str/join ",\n  - " ks))
                  (str "Selection could not provide required key(s)\n  - " (str/join "\n  - " ks)))]
        (throw (AssertionError. msg))))))

(defn selection
  ([outspec] (selection (global/get-rules :default) outspec))
  ([ruleset outspec]
   (let [f (compile/selection
             (if (keyword? ruleset)
               (global/get-rules ruleset)
               ruleset)
             (cond
               (map? outspec) (set (concat (:req outspec) (:opt outspec)))
               (coll? outspec) outspec
               :else [outspec])
             #{})
         selection-name (:name outspec)
         ns-name (str *ns*)]
     (cond
       (vector? outspec) (fn [m] (select-keys (f (transient m)) outspec))
       (set? outspec) (fn [m] (persistent! (f (transient m))))
       (map? outspec) (let [req (:req outspec)
                            opt (:opt outspec)
                            all (set (concat req opt))
                            add (fn [m] (persistent! (f (transient m))))
                            assert-req (fn [m] (assert-req ruleset m req selection-name ns-name))]
                        (if (set? req)
                          (fn [m]
                            (let [ret (add m)]
                              (assert-req ret)
                              ret))
                          (fn [m]
                            (let [ret (add m)]
                              (assert-req ret)
                              (select-keys ret all)))))
       :else (fn [m] (get (f (transient m)) outspec))))))

(defmacro select
  ([m outspec] `(select ~m :default ~outspec))
  ([m ruleset outspec]
   `((selection ~ruleset ~outspec) ~m)))

(defmacro add
  [ruleset? & forms]
  (if (keyword? ruleset?)
    `(binding [mapping/*ruleset-name* ~ruleset?]
       (global/add-rule ~ruleset? (mapping/mappings ~@forms)))
    `(mappings.core/add :default ~@(cons ruleset? forms))))

(defn delete
  [k? & rids]
  (if (keyword? k?)
    (doseq [rid rids]
      (global/delete-rule k? rid))
    (apply delete :default (cons k? rids))))

(comment
  (let [rules
        (ruleset/ruleset
          (mappings (:b :a :fn inc :rfn dec)
                    (:c [:a :b] +)))

        f (selection
            rules
            [:a :c])]
    (f {:b 42})))