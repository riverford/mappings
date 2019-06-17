(ns mappings.impl.graph
  (:require [ubergraph.core :as ubergraph]
            [mappings.impl.rule :as rule]))

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

(defn- add-rule*
  [g rule]
  (let [{:keys [::rule/nid
                ::rule/required-keys
                ::rule/optional-keys
                ::rule/provides]} rule

        rulenode [::rule nid]

        g (apply
            ubergraph/add-nodes
            g
            rulenode
            (concat required-keys optional-keys provides))

        g (apply
            ubergraph/add-directed-edges
            g
            (for [output-key provides]
              [rulenode output-key]))

        g (apply
            ubergraph/add-directed-edges
            g
            (for [needed-key (concat required-keys optional-keys)]
              [needed-key rulenode {:required required-keys}]))]

    g))

(defn add-rule
  [g & rules]
  (let [rules (coerce-rules rules)]
    (reduce add-rule* g rules)))

(defn graph
  [& rules]
  (add-rule
    (ubergraph/multidigraph)
    rules))

(defn remove-rule
  [g id]
  (let [rulenode [::rule id]
        g (ubergraph/remove-nodes g rulenode)
        orphaned (ubergraph.alg/loners g)]
    (apply ubergraph/remove-nodes g orphaned)))

(defn key-nodes
  [g]
  (->> (ubergraph/nodes g)
       (remove (fn [node] (and (vector? node) (= ::rule (nth node 0 nil)))))))