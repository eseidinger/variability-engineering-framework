# Variability Engineering Framework Documentation

Updated on October 9, 2026 to align the documented food-service MVP, HTTP contract,
local verification, and Developer Platform configuration with the implementation.
This documentation covers the product, target architecture, architectural decisions,
development sequence, and prototype operations.

## Getting started

| Area | Key question | Documents |
|---|---|---|
| Product | Who is this for, and what should it achieve? | [Vision](01-product/vision.md), [Use cases](01-product/use-cases.md), [Requirements](01-product/requirements.md), [Roadmap](01-product/roadmap.md) |
| Architecture | How should modeling, analysis, configuration, and optimization work? | [Overview](02-architecture/overview.md), [Domain model](02-architecture/domain-model.md), [Analysis view](02-architecture/analysis-view.md), [Food-service MVP semantics](02-architecture/food-service-mvp-semantics.md), [Food-service HTTP contract](02-architecture/food-service-http-contract.md), [Persistence and deployment](02-architecture/persistence-and-deployment.md) |
| Decisions | Why was this approach selected? | [ADR index](03-decisions/README.md) |
| Development | What is built first, and how will it be verified? | [Development plan](04-development/development-plan.md), [MVP implementation plan](04-development/mvp-implementation-plan.md), [Energy configuration implementation plan](04-development/energy-configuration-implementation-plan.md), [Quality assurance](04-development/quality-assurance.md), [Open decisions](04-development/open-decisions.md) |
| Traceability | Which sources support the baseline, and how is it maintained? | [Sources and maintenance](sources.md) |
| Operations | How will the prototype be deployed and checked? | [Deployment](05-operations/deployment.md) |

## Status and evidence

The food-service MVP is implemented across the Angular UI, Quarkus API, PostgreSQL persistence, and combined local container image. The API contains a domain-independent observed-population analysis core with unit and conformance tests, a verified food-service fixture adapter, and a public HTTP contract. The UI exposes summary metrics, filters, reorderable grouping, ingredient leverage, and ingredient-unavailability impact. The bounded energy model validates typed inputs and enumerates new-equipment configurations against the released kit; general-purpose generative evaluation and broader framework capabilities remain planned.

- **Requirement:** intended project scope, not implementation evidence.
- **Documented direction:** an objective recorded in the current project description.
- **Proposal:** a design requiring confirmation.
- **Open:** unresolved or insufficiently specified.
- **Accepted:** a binding design choice; acceptance does not by itself prove implementation.
- **Implemented:** reserved for behavior present in source.
- **Verified:** reserved for a recorded, reproducible check.

The repository evidences food-service UI behavior, local container assembly, a domain-independent analysis core, verified fixture import, PostgreSQL round trips, public API behavior, a Compose-backed black-box workload check, and bounded energy configuration generation. It does not yet evidence general-purpose generative model evaluation, configuration assistance, completed energy optimization, actual Docker Hub publication, or successful deployment to the Developer Platform.

### Capability status

| Capability | Source | Local verification | Platform verification |
|---|---|---|---|
| Observed-population normalization and analysis | Implemented | Unit, conformance, HTTP, persistence, and Compose checks | Not yet recorded |
| Element-unavailability impact | Implemented | Unit, conformance, HTTP, and Compose checks | Not yet recorded |
| Angular analysis dashboard | Implemented | Build plus summary/error unit tests; packaged shell check | Not yet recorded |
| PostgreSQL persistence and fixture reload | Implemented | PostgreSQL round trip and container restart | Not yet recorded |
| Combined UI/API image | Implemented | Compose build and startup | Not yet recorded |
| Generative models and configuration assistance | Planned | None | None |
| Bounded energy inputs and new-equipment generation | Implemented | Complete reference configuration set and boundary tests | None |
| Portfolio optimization and reusable components | Planned | None | None |

## Maintenance

Use product documents for outcomes and scope, architecture documents for system design, ADRs for decisions and alternatives, development documents for sequencing and evidence, and operations documents for executable procedures. Update an ADR before changing an accepted architectural choice. Do not describe proposed or source-inspected behavior as operationally verified.
