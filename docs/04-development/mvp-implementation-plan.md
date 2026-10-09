# Food-Service Analysis MVP Implementation Plan

Status: checked implementation plan as of October 9, 2026. A checked item has
repository evidence; it does not imply platform deployment or production readiness.

## MVP outcome

Import one versioned recipe population and answer basic questions about recipe counts, structural variants, ingredients, diet and cuisine groupings, ingredient leverage, and ingredient-unavailability impact. The MVP keeps the domain-independent core separate from the food-service adapter, HTTP API, persistence, and UI.

## Completed foundation

- [x] Archive the original framework update as non-canonical source material and distribute its direction into product, architecture, ADR, and development documents.
- [x] Add repository-wide Apache-2.0 licensing, a project notice, and a CC0-1.0 exception for the original fixture.
- [x] Freeze the observed food-service MVP semantics in [ADR-013](../03-decisions/ADR-013-food-service-analysis-mvp-semantics.md) and the binding [MVP contract](../02-architecture/food-service-mvp-semantics.md).
- [x] Define binary structural identity, dimension cardinality, `KNOWN`/`UNKNOWN` behavior, filtering, metrics, version provenance, and whole-record element unavailability.
- [x] Create the original, versioned [food-service MVP v1 fixture](../../examples/food-service/mvp-v1/README.md), including source rows, mappings, derivations, expected results, and SHA-256 manifest.
- [x] Independently verify the fixture’s 13 source records, 12 accepted records, 11 structural variants, 24 elements, expected queries, impact result, and artifact digests.
- [x] Implement the in-memory, domain-independent Java core: populations, records, variants, dimensions, filters, analysis trees, metrics, leverage, and element-unavailability scenarios.
- [x] Cover the core with API unit tests for structural deduplication, multi-valued groups, unknown values, filters, grouping order, leverage, impact, and invalid references.

## Next implementation steps

1. [x] Implement a food-service fixture adapter in the API.
   - Read the source, alias, classification, adapter-contract, and manifest artifacts.
   - Verify input digests before import.
   - Normalize aliases, derive diet and allergen values, create provenance, and report rejected rows.
   - Produce an `AnalysisPopulation` through the domain-independent core types only.
   - Implemented by `FoodServiceFixtureAdapter`, with tests for fixture import, rejection reporting, core analysis metrics, and manifest checksum rejection.

2. [x] Add fixture conformance tests.
   - Compare imported records, signatures, dimensions, rejected rows, query answers, leverage, and garlic-unavailability impact with `expected/results.json`.
   - Verify that altered source, mapping, derivation, or adapter bytes yield a different population version or are rejected against the manifest.
   - Implemented by `FoodServiceFixtureConformanceTest`, which uses `expected/results.json` as the oracle and verifies manifest rejection for each input artifact.

3. [x] Define and implement the HTTP contract.
   - Expose population summary, filtered/grouped analysis, record detail, element leverage, and element-unavailability impact.
   - Return the population version, active filters, metrics, and scenario rule in every result.
   - Keep request/response DTOs outside the core package.
   - Implemented by the fixture-backed `FoodServiceAnalysisResource`; its documented contract and Quarkus HTTP tests cover successful and invalid requests.

4. [x] Add PostgreSQL persistence.
   - Resolve OPEN-05 through OPEN-07 for the MVP’s data shape, query workload, and limits.
   - Persist fixture-derived populations, provenance, records, usages, dimensions, and rejected rows.
   - Verify save/load semantics and workload recreation.
   - Implemented through Flyway and `JdbcPopulationRepository`; a PostgreSQL 17 Dev Services test verifies the fixture round trip and reference workload metrics.

5. [x] Build the food-service UI.
   - Show population provenance and summary metrics.
   - Add filtering and reorderable cuisine/diet/dish-type grouping.
   - Add ingredient leverage and garlic-unavailability views with visible baseline/scenario deltas.
   - Implemented as a lazy-loaded Angular Material dashboard backed by the public food-service HTTP contract, with unit coverage for summary loading and API errors.

6. [x] Add black-box integration and local image verification.
   - Exercise UI, API, and PostgreSQL together against the fixture.
   - Build the combined image, recreate the workload, and record revision, runtime,
     peak memory, and limitations.
   - Implemented by the Compose-backed [black-box verifier](../../apps/prototype/integration-tests/verify-compose.sh) and its [local deployment record](mvp-deployment-verification.md).

7. [ ] Verify the immutable image on the Developer Platform.
   - Confirm public routing, application/database readiness, persisted population
     access, resource behavior, and rollback expectations.
   - Record the exact revision, image digest, environment, checks, and limitations.

## Explicitly deferred

- Generative model authoring and partial configuration.
- Quantities, units, cost, and sustainability semantics.
- Dimension combination and user-defined aggregation.
- Portfolio selection, optimization, and Pareto results.
- An external recipe dataset; this remains [OPEN-21](open-decisions.md).
- Reusable SDKs and UI component packages.

## Completion gate

The MVP is complete only when the fixture can be imported through the application, its expected results are reproduced through the public API and UI, PostgreSQL round trips preserve the population’s semantics and provenance, and the combined image has a recorded successful platform deployment check.
