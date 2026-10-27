# Product Roadmap

Status: planning sequence without confirmed dates or effort estimates.

| Step | Outcome | Current evidence | Acceptance gate |
|---|---|---|---|
| 1. Semantics and stack | Initial language, UI, types, expressions, identity, and limits selected | Observed-population subset accepted; generative expressions open | Binding prototype contract documented |
| 2. Evaluation core | Validation, bounded enumeration, constraints, presence evaluation, and variant deduplication | Observed-population analysis implemented; generative evaluation open | Reference fixtures produce expected results |
| 3. Application and persistence | Results view plus PostgreSQL save/load | Food-service populations, verified energy publications, and durable owned energy runs implemented | Round trip and error cases verified |
| 4. First deployment | UI/API image on the Developer Platform | CI deployment recorded; owner-confirmed production energy/AI journey | Complete accessibility, security, persistence/recovery, and resource evidence still required |
| 5. Interactive analysis | Reorder, hide, combine, filter, and drill through dimensions | Filtering and reorderable grouping implemented; hiding/combining open | View invariants and aggregation rules verified |
| 6. Normalized populations | Generated and imported records share structural signatures, provenance, and analysis contracts | Imported records implemented; generated records open | Generated and observed fixtures produce expected record and distinct-variant counts |
| 7. Food-service demonstration | Licensed recipe dataset, documented adapter, ingredient impact, and analytical views | Synthetic MVP implemented; external licensed dataset open | Mapping, derivations, provenance, and reference results verified |
| 8. Impact and configuration | Versioned scenarios and partial-requirement completion | Element-unavailability impact and bounded energy configuration with AI assistance implemented; general-purpose completion open | Baseline deltas and compatible completions match exhaustive fixtures |
| 9. Portfolio optimization | Menu constraints, objectives, explanations, and later Pareto alternatives | Planned | Small problems match exhaustive reference solutions and all claims identify solver status |
| 10. Framework components | Shared schemas, conformance cases, SDKs, and UI packages | Planned | Equivalent normalized results across implementations |
| 11. Experiments and additional domains | Defined algorithm or storage comparisons and cross-domain validation | Planned | Workload, method, evidence, and limitations published |

The synthetic food-service fixture is the current observed-data correctness oracle.
A future external dataset and a separate generative fixture will validate those paths
before contracts are extracted into multiple framework implementations. Optimization
starts with small fully enumerable portfolio fixtures; adopting a specialized solver
is a later evidence-driven decision.

The bounded [energy workflow](../02-architecture/energy-configuration.md) has
its own implementation and acceptance track against contract kit 0.5.0. It
includes ODIP-backed deterministic optimization and reviewed AI assistance;
this does not complete general-purpose model evaluation or portfolio selection.
The [verification record](../04-development/energy-contract-kit-verification.md)
includes guided checks through October 26. AI-05/06/08/10/11, remaining deployed
regression/recovery and final release acceptance remain open; the
[AI runbook](../04-development/energy-ai-acceptance-runbook.md) lists the assertions.

The selected observed-data correctness oracle is the versioned [food-service MVP v1 fixture](../../examples/food-service/mvp-v1/README.md). Its expected results cover normalization, dimensions, filtering, leverage, and ingredient unavailability.
