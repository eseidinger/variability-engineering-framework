# Expression and Analysis View

Status: ordered grouping and filtering are implemented for observed populations;
portable generative expressions, hiding, dimension combination, and broader
interaction semantics remain target design.

## Portable expressions

Rules use a dedicated serializable abstract syntax tree rather than native Java, Python, or JavaScript expressions. The initial operation set contains Boolean constants, `AND`, `OR`, `NOT`, Boolean-feature evaluation, and equality or inequality for enumeration values.

## Derived analysis tree

The analysis tree is a view of an identified analysis population, not the underlying domain model or imported dataset. Reordering dimensions changes hierarchy but not model constraints, population membership, records, or structural variants.

Each dimension declares its cardinality, origin, and observed values for the current
population. Missing-value and derivation semantics belong to the binding population
contract and provenance. Future generative dimensions may additionally declare an
allowed domain. Configuration features, imported attributes, and derived
classifications may all provide dimensions without becoming interchangeable in the core model.

| Operation | Required invariant |
|---|---|
| Reordering | Overall results remain unchanged. |
| Hiding or projection | Hidden dimension states are merged, not silently removed. |
| Grouping | Membership and counting rules are explicit. |
| Filtering | The restricted subspace is visible to the user. |
| Drill-down and roll-up | Metrics remain tied to the identified subspace. |

## Metrics

`configurationCount` counts valid complete configurations when a generative model is the source. `recordCount` counts identified records in the selected population. `variantCount` counts distinct structural signatures. `uniqueElementCount` is the element union size; `commonElements` is the intersection; `optionalElements` is union minus intersection. Frequencies must state whether configurations, records, or distinct variants form the reference population.

Counts aggregate differently: record counts can be added across disjoint record groups, while configuration and variant counts require attention to their identities and may not be additive after projection because a configuration or structural variant can occur in several groups.

Impact views display the baseline, scenario, absolute and relative deltas, and the exact feasibility or transformation rule. Optimization views identify the candidate population, constraints, objectives, solver status, and whether displayed alternatives are merely feasible, optimal for one objective, or Pareto-optimal.
