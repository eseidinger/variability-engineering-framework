# Quality Assurance and Experiments

Status: verification catalog. Observed-population, fixture, HTTP, PostgreSQL, UI
summary/error, and Compose-shell checks are implemented; generative, optimization,
cross-language, browser end-to-end, and platform checks remain proposed.

| Check | Expectation |
|---|---|
| Enumeration and constraints | Exactly the valid configurations are analyzed. |
| Presence conditions | Each reference configuration produces its expected element set. |
| Deduplication | Equal element sets count as one variant. |
| Record identity | Separate generated or imported records remain countable when they share one structural variant. |
| Invalid versus empty | Invalid configurations remain distinct from valid empty variants. |
| Persistence | Saving and loading preserves semantics. |
| Dimension origin | Feature-derived, imported, and derived dimensions retain documented types, values, and provenance. |
| Dimension order | Reordering does not change population membership or overall results. |
| Projection | Hiding alone does not remove records. |
| Aggregation | Distinct variants use set operations rather than additive counts. |
| Imported population | License, source version, mapping version, rejected rows, missing values, and derivations are recorded. |
| MVP structural identity | Alias normalization precedes set comparison; ordering, duplicates, quantities, and recipe identity do not change the binary signature. |
| Missing and empty values | Explicit unknown values remain distinct from known empty multi-valued classifications. |
| Multi-valued grouping | Overlapping child groups do not inflate root record, variant, or element metrics. |
| Population versioning | A changed dataset checksum, adapter, mapping, or derivation produces a new immutable population version. |
| Impact scenario | The baseline, operation, feasibility rule, and absolute and relative deltas are reproducible. |
| Ingredient unavailability | Every record using an unavailable ingredient is excluded as a whole and the baseline remains unchanged. |
| Partial requirements | Every completion is valid and compatible; exhaustive fixtures contain no omitted compatible completion. |
| Portfolio feasibility | Every selected record belongs to the candidate population and every portfolio constraint is evaluated. |
| Optimization | Small fixtures match exhaustive feasible sets and objective values; solver status supports every optimality claim. |
| Pareto frontier | Every reported point is feasible and non-dominated, and exhaustive fixtures contain no omitted non-dominated point. |
| Language conformance | Shared inputs produce equivalent normalized results. |

Experiments must state the question, hypothesis, versions, hardware, configuration, input generator or dataset version, mapping and derivations, revision, repeated measurements, raw results, and limitations. Optimization experiments also record solver, parameters, seed where applicable, termination status, bounds, and elapsed time. Record runtime, peak memory, relevant latency distributions, storage and index costs, and operational effort where applicable. A successful deployment is not a benchmark result.
