# Architecture Decision Records

ADRs separate accepted requirements and documented directions from proposals. Implementation status and operational verification must be recorded independently.

| ADR | Topic | Status |
|---|---|---|
| [001](ADR-001-domain-independent-core.md) | Domain-independent core | Requirement |
| [002](ADR-002-separation-of-responsibilities.md) | Separate model, calculation, and presentation | Documented direction |
| [003](ADR-003-verifiable-models.md) | Start with small, verifiable models | Documented direction |
| [004](ADR-004-postgresql-baseline.md) | PostgreSQL persistence baseline | Implemented for food-service MVP |
| [005](ADR-005-single-container-application.md) | UI and API in one image | Implemented locally; platform verification pending |
| [006](ADR-006-analysis-tree-view.md) | Analysis tree as a derived view | Filtering/grouping implemented; remainder proposed |
| [007](ADR-007-portable-expression-ast.md) | Portable expression AST | Proposed |
| [008](ADR-008-conformance-across-languages.md) | Shared specification and conformance cases | Language support required; design proposed |
| [009](ADR-009-workload-driven-data-stores.md) | Evaluate additional data stores against workloads | Documented direction |
| [010](ADR-010-normalized-analysis-population.md) | Normalize generated and observed variant records | Observed path implemented; generated path planned |
| [011](ADR-011-analysis-dimensions.md) | Separate analysis dimensions from configuration features | Observed path implemented; feature path planned |
| [012](ADR-012-portfolio-optimization-boundary.md) | Separate portfolio optimization from configuration validity | Documented direction |
| [013](ADR-013-food-service-analysis-mvp-semantics.md) | Freeze food-service analysis MVP semantics | Accepted |
| [014](ADR-014-energy-configuration-identity.md) | Quantitative energy identity and bounded generation | Accepted for new equipment; implemented and verified locally |
| [015](ADR-015-energy-simulation-and-publication.md) | Verified energy input and deterministic evaluation | Accepted for the local reference slice; implemented and verified locally |
| [016](ADR-016-energy-run-execution.md) | Durable owned energy run execution | Accepted for the deterministic new-equipment slice; locally tested |

## Decision process

Define the problem and affected requirements, compare realistic alternatives, record the decision and consequences, specify validation evidence, and update dependent architecture and planning documents. Supersede accepted records rather than silently rewriting their history.
