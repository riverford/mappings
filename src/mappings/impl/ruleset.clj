(ns mappings.impl.ruleset
  (:require [mappings.impl.rule :as rule]
            [mappings.impl.graph :as graph]
            [ubergraph.alg :as alg]
            [ubergraph.core :as uber]
            [clojure.set :as set]))

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

(defn- remove-rule*
  [ruleset id]
  (let [{:keys [::rules]} ruleset
        {:keys [::rule/provides
                ::rule/nid]
         :as rule} (get rules id)
        _ (println "Replacing rule" (rule/ruleform rule))
        ruleset (update ruleset ::rules dissoc id)
        ruleset (update ruleset ::nids dissoc nid)
        ruleset (update ruleset ::g graph/remove-rule)
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
                ::rule/id
                ::rule/nid]} rule]
    (if (contains? rules id)
      (add-rule* (remove-rule* ruleset id) rule)
      (let [ruleset (assoc-in ruleset [::rules id] rule)
            ruleset (assoc-in ruleset [::nids nid] id)
            ruleset (update ruleset ::g graph/add-rule rule)
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
  ([] {::g (graph/graph)})
  ([& mappings] (reduce add (ruleset) mappings)))

(defn identify-rule
  [ruleset id]
  (if-some [rule (get-in ruleset [::rules id])]
    id
    (if-some [id (get-in ruleset [::nids id])]
      id)))

(defn remove-rule
  [ruleset id]
  (if-some [id (identify-rule ruleset id)]
    (remove-rule* ruleset id)
    ruleset))

(defn- edge-desc
  [g edge]
  [(uber/src edge)
   (uber/dest edge)
   (uber/attrs g edge)])

(defn- rulenode?
  [x]
  (and (vector? x)
       (= ::graph/rule (nth x 0 nil))))

(defn- rulenode->form
  [ruleset rulenode]
  (when-some [ruleid (when (rulenode? rulenode)
                       (identify-rule ruleset (nth rulenode 1)))]
    (some-> (get (::rules ruleset) ruleid) rule/ruleform)))

(defn- shortest-path
  [g has-nodes k]
  (let [path (alg/shortest-path g {:start-nodes
                                   (if (nil? has-nodes)
                                     (remove #{k} (graph/key-nodes g))
                                     has-nodes)
                                   :end-node k})
        edges (map (partial edge-desc g) (alg/edges-in-path path))
        empty-search? (nil? has-nodes)
        has-nodes (atom
                    (if empty-search?
                      #{}
                      (set has-nodes)))]

    (for [[src dest {:keys [required] :as attrs} :as edge] edges
          :let [satisfied? (set/subset? (set required) @has-nodes)]
          edge (if satisfied?
                 (do
                   (when-not (rulenode? dest)
                     (swap! has-nodes conj dest))
                   [edge])
                 (concat
                   (for [needed (set/difference (set required) @has-nodes)
                         edge (shortest-path g @has-nodes needed)
                         :let [_ (swap! has-nodes conj needed)]]
                     edge)
                   [edge]))]
      edge)))

(defn- do-print-paths
  [ruleset start-nodes outspec]
  (let [{:keys [::g
                ::rules]} ruleset
        edges (shortest-path g start-nodes outspec)]
    (if (empty? edges)
      (println "No path found to " outspec)
      (doseq [[src dest attrs] edges
              :let [ruleid (when (rulenode? dest)
                             (identify-rule ruleset (nth dest 1)))

                    rule (get rules ruleid)]]

        (if rule
          (print src "via" (rule/ruleform rule))
          (print " provides" dest "\n"))))))