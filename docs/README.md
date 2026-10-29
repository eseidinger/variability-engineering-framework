# Variability Engineering Framework Documentation

Updated on October 27, 2026 to reconcile the energy implementation and acceptance
status across VEF, ODIP and the shared contract repository.
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

The food-service MVP is implemented across the Angular UI, Quarkus API, PostgreSQL persistence, and combined local container image. The API contains a domain-independent observed-population analysis core with unit and conformance tests, a verified food-service fixture adapter, and a public HTTP contract. The UI exposes summary metrics, filters, reorderable grouping, ingredient leverage, and ingredient-unavailability impact. The energy layer verifies publication bytes, generates new-equipment configurations, simulates hourly dispatch, ranks the released reference candidates, and exposes durable owned runs through an API and UI. The owner confirmed the full deployed energy flow, including AI explanations,
on October 19, 2026; formal AI acceptance and regression evidence remain open.

- **Requirement:** intended project scope, not implementation evidence.
- **Documented direction:** an objective recorded in the current project description.
- **Proposal:** a design requiring confirmation.
- **Open:** unresolved or insufficiently specified.
- **Accepted:** a binding design choice; acceptance does not by itself prove implementation.
- **Implemented:** reserved for behavior present in source.
- **Verified:** reserved for a recorded, reproducible check.

The repository evidences food-service UI behavior, local container assembly, a domain-independent analysis core, verified fixture import, PostgreSQL round trips, public API behavior, a Compose-backed black-box workload check, bounded energy calculation, durable owned runs, and AI proposal/explanation validation. The [verification record](04-development/energy-contract-kit-verification.md) retains chronological checks. The current bounded energy release pins kit 1.0.0; VEF v1.0.0 is ready in production and [Gate 6 is closed with limitations](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/2026-10-29-gate-6-closure.md). The [October 27 review](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/2026-10-27-implementation-and-acceptance-review.md) and [gap analysis](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/2026-10-27-gap-analysis.md) remain historical baselines and follow-up registers; general-purpose generative-model evaluation remains separate.

### Capability status

| Capability | Source | Local verification | Platform verification |
|---|---|---|---|
| Observed-population normalization and analysis | Implemented | Unit, conformance, HTTP, persistence, and Compose checks | Not yet recorded |
| Element-unavailability impact | Implemented | Unit, conformance, HTTP, and Compose checks | Not yet recorded |
| Angular analysis dashboard | Implemented | Build plus summary/error unit tests; packaged shell check | Not yet recorded |
| PostgreSQL persistence and fixture reload | Implemented | PostgreSQL round trip and container restart | Not yet recorded |
| Combined UI/API image | Implemented | Compose build and startup | CI deployment recorded; production energy journey confirmed by owner |
| General-purpose generative models and configuration completion | Planned | None | None |
| Bounded energy inputs and new-equipment generation | Implemented | Complete reference configuration set and boundary tests | Owner-confirmed production reference result |
| Verified energy publication and deterministic optimization | Implemented | Exact reference simulation/ranking and PostgreSQL byte round trip | Full deployed 252-candidate oracle comparison and guided cache fallback; remaining regression/recovery open |
| Durable owned energy runs and candidate pages | Implemented | HTTP and PostgreSQL creation, replay, ownership, result, and page tests | Guided cross-user denial, idempotency and completed-run retrieval after restart; integrated restore open |
| Energy AI proposals, confirmation, and explanations | Implemented | Mocked provider, confirmation, citation, evidence-bound, and failure checks | Guided/controlled AI-01–04/07/09 evidence; AI-05/06/08/10/11 incomplete |
| Portfolio optimization and reusable components | Planned | None | None |

## Maintenance

Use product documents for outcomes and scope, architecture documents for system design, ADRs for decisions and alternatives, development documents for sequencing and evidence, and operations documents for executable procedures. Update an ADR before changing an accepted architectural choice. Do not describe proposed or source-inspected behavior as operationally verified.

The [October 27 local gap-closure record](04-development/energy-gap-closure-verification.md) records the follow-up implementation and tests; it does not close hosted Gate 6 acceptance.
