# Variability Engineering Framework

A domain-independent framework for modeling, analyzing, configuring, and optimizing systems with many possible variants. Implemented observed-population analysis provides the semantic foundation for the broader framework.

## Status

The food-service analysis MVP and combined container image have been validated locally against PostgreSQL. The energy workflow passes API/PostgreSQL, UI and packaged integration tests. On October 29, 2026, [CI run 40881986516](https://github.com/eseidinger/variability-engineering-framework/actions/runs/40881986516) passed against published contract kit 1.0.0 and deployed `vareng-test` revision 56. Bounded Gate 6 was accepted under DEC-13 with DEC-10/11/12 limits. The maintainer subsequently authorized production deployment; [VEF 1.0.0](https://github.com/eseidinger/variability-engineering-framework/releases/tag/v1.0.0) passed tagged CI and is ready on `vareng` revision 17. Exact image pins and limits are in the [production deployment record](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/2026-10-29-production-deployment.md). Earlier production walkthroughs and the [acceptance record](docs/04-development/energy-contract-kit-verification.md) remain historical evidence.

The API contains a domain-independent analysis core for versioned observed populations, a verified food-service fixture adapter, an HTTP analysis contract, and PostgreSQL-backed population storage. The Angular Material dashboard exposes summary metrics, filtering, reorderable grouping, ingredient leverage, and ingredient-unavailability impact.

The API also includes an energy workflow against contract kit
1.0.0: typed new-equipment generation, checked publication bytes, hourly
simulation, exhaustive ranking, durable owned runs, and paged candidates. The
lazy-loaded `/energy` UI supports natural-language proposals, requires explicit
confirmation of the current structured request, and displays recommendations,
evidence, and validated AI explanations. ODIP's publication service is maintained
in its own repository. General-purpose generative-model evaluation, portable
configuration completion, portfolio optimization, and reusable framework
components remain later work.

## Implemented MVP

The first application:

- imports and verifies a versioned synthetic food-service population;
- calculates record, structural-variant, ingredient, grouping, leverage, and unavailability results;
- saves and reloads the immutable population through PostgreSQL;
- presents the workload through an Angular UI and Quarkus API; and
- packages and verifies the UI and API as one local container image.

The stack-neutral workspace is documented in [`apps/prototype/`](apps/prototype/README.md).

The normative observed-data example is the [food-service MVP v1 fixture](examples/food-service/mvp-v1/README.md).

General-purpose generative model authoring and configuration completion, portfolio optimization, an external food-service dataset, additional language implementations, reusable UI components, and broader performance or storage experiments follow in later stages. The implemented bounded [energy configuration slice](docs/02-architecture/energy-configuration.md) is tracked separately.

## Run locally

Run the packaged UI, API, and PostgreSQL together from the repository root:

```shell
docker compose --profile app up --build
```

Open <http://localhost:8080>. The energy workflow needs the platform's browser
identity boundary and ODIP reader binding; standalone Compose does not provide
these. For a clean database, stop the stack with
`docker compose down -v`. Component-specific development commands are documented in
[`apps/prototype/`](apps/prototype/README.md).

## Documentation

| Area | Documents |
|---|---|
| Product | [Vision](docs/01-product/vision.md), [use cases](docs/01-product/use-cases.md), [requirements](docs/01-product/requirements.md), [roadmap](docs/01-product/roadmap.md) |
| Architecture | [Overview](docs/02-architecture/overview.md), [domain model](docs/02-architecture/domain-model.md), [analysis view](docs/02-architecture/analysis-view.md), [food-service MVP semantics](docs/02-architecture/food-service-mvp-semantics.md), [food-service HTTP contract](docs/02-architecture/food-service-http-contract.md), [persistence and deployment](docs/02-architecture/persistence-and-deployment.md) |
| Decisions | [Architecture decision records](docs/03-decisions/README.md) |
| Development | [Development plan](docs/04-development/development-plan.md), [MVP implementation plan](docs/04-development/mvp-implementation-plan.md), [energy configuration implementation plan](docs/04-development/energy-configuration-implementation-plan.md), [quality assurance](docs/04-development/quality-assurance.md), [open decisions](docs/04-development/open-decisions.md) |
| Operations | [Prototype deployment](docs/05-operations/deployment.md) |
| Traceability | [Sources and maintenance](docs/sources.md) |

See the [documentation index](docs/README.md) for status conventions and maintenance guidance.

## Core distinction

A **configuration** is a complete assignment of feature values. A structural **variant** is the resulting collection of present element usages. A **variant record** is an identified generated or imported candidate with dimensions and provenance. Different configurations or records can produce the same structural variant, so configuration count, record count, and distinct-variant count are separate metrics. A **portfolio** is a selected collection of variant records; in the food-service domain, a dish is a variant record and a menu is a portfolio.

## License

Source code and documentation are licensed under the [Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for attribution. The original synthetic [food-service MVP fixture](examples/food-service/mvp-v1/README.md) is separately dedicated under CC0-1.0. External datasets and third-party materials remain subject to their own licenses.

The [October 27 local gap-closure record](docs/04-development/energy-gap-closure-verification.md) records the follow-up implementation and tests; it does not close hosted Gate 6 acceptance.
