(ns mappings.impl.ruleset
  (:require [mappings.impl.rule :as rule]))

(defn- coerce-rules
  [x]
  (cond
    (nil? x) x
    (map? x) (cond
               (::rule/id x) [x]
               (::rules x) (vals (::rules x))
               :else (throw (Exception. "Cannot create rule(s) from map")))
    (coll? x) (mapcat coerce-rules x)
    :else x))

(defn- remove-rule
  [ruleset id]
  (let [{:keys [::rules]} ruleset
        {:keys [::rule/provides]
         :as rule} (get rules id)
        _ (println "Replacing rule" (rule/ruleform rule))
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
        {:keys [::rule/provides
                ::rule/id]} rule]
    (if (contains? rules id)
      (add-rule* (remove-rule ruleset id) rule)
      (let [ruleset (assoc-in ruleset [::rules id] rule)
            rf (fn [ruleset provided-key]
                 (update-in ruleset [::provision provided-key] (fnil conj #{}) id))
            ruleset (reduce rf ruleset provides)]
        ruleset))))

(defn add
  ([ruleset rule]
   (if (::rule/id rule)
     (add-rule* ruleset rule)
     (reduce add-rule* ruleset (coerce-rules rule))))
  ([ruleset rule & rules]
   (reduce add (add ruleset rule) rules)))

(defn ruleset
  ([] {})
  ([& mappings] (reduce add (ruleset) mappings)))

(defn get-providing
  [ruleset k]
  (map (::rules ruleset {}) (get (::provision ruleset) k)))