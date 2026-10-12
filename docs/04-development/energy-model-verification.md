# Energy Model Verification

Date: October 12, 2026. Status: locally verified bounded new-equipment generation.
This is the implemented portion of `VEF-02`; existing-equipment semantics and
the package's complete cross-project exit remain open. No simulation, final
recommendation, run API, or energy deployment is claimed.

## Inputs and environment

- VEF base revision: `2ae2a171a93c52fc57f9b4577398ab43d6a8550e`; this implementation is in the working tree, with no new commit created during verification.
- Released kit: `0.4.0`, archive SHA-256 `884e3590614328c2adafa457153e11575dc1be09b9f53d7b0f53704fe39d02b0`.
- Model: `energy-model/1.0.0`, checked against the committed consumer lock.
- Reference: `energy-reference-wasserburg-am-inn-2024`, dataset/catalog version `1.0.0`, released request and `expected-results/optimization-reference.json`.
- Environment: Linux x86_64, OpenJDK Zulu 25.0.4, Maven 3.9.16, Quarkus 3.39.5, PostgreSQL 17.11 (`postgres:17-alpine`), Node 24.14.0.

Kit files were prepared by the checksum-verifying validator and supplied through
`ENERGY_CONTRACT_KIT_DIRECTORY`. No fixtures or oracle results were regenerated
or copied into production source. The [architecture](../02-architecture/energy-configuration.md)
and [ADR-014](../03-decisions/ADR-014-energy-configuration-identity.md) define
the verified implementation boundary.

## Checks and results

The complete API suite passed **36 tests, zero failures/errors/skips**, including
18 energy tests and all existing food-service core, fixture, HTTP, status, and
PostgreSQL round-trip tests.

Energy conformance compares every released configuration ID in order, component
selection/quantity/ownership, initial investment, roof area, and DC/AC ratio.
Both the estimate and actual population contain **252** configurations. A zero
budget and 100% self-sufficiency request retains the same evaluated population;
those feasibility constraints require later simulation/evaluation.

Boundary tests cover:

- exact 100,000-evaluation acceptance and rejection above the bound;
- roof/rating tolerances, empty roof, the 100-PV cap, and large finite roof values;
- unavailable models, the no-battery case, and 1–4 battery quantities;
- duplicate IDs, missing/excess catalog types, unsupported currency/schema/version;
- invalid/non-finite input and aggregated values, immutable collections, deterministic input-order independence;
- configuration ID collisions/length and explicit existing-equipment rejection.

The initial full-suite invocation encountered a local `.env` pointing to an
unavailable PostgreSQL server. No application code failed its assertions. The
successful rerun used a fresh temporary PostgreSQL container on a randomly
assigned loopback port; explicit process properties overrode the local connection
and disabled Dev Services for that run. The container was removed afterward,
and local `.env`/development data were not changed.

The successful Maven invocation from `apps/prototype/api` was:

```sh
ENERGY_CONTRACT_KIT_DIRECTORY=/path/to/prepared/contract-kit \
./mvnw --batch-mode --no-transfer-progress \
  -Dquarkus.datasource.devservices.enabled=false \
  -Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:TEST_PORT/energy_model_tests \
  -Dquarkus.datasource.username=energy_model_tests \
  -Dquarkus.datasource.password=energy_model_tests test
```

Use a temporary database and replace `TEST_PORT`/the fixture path. Without local
database overrides, CI uses the existing PostgreSQL Dev Services setup.
Documentation links and `git diff --check` also pass.

## Remaining work

Existing-equipment inputs currently fail with `existing_equipment_not_supported`;
shared non-empty quantity/compatibility/identity examples are required. Catalog
IDs that cannot fit or uniquely identify an assignment under the reference ID
format fail explicitly. These limitations are not presented as full v1 acceptance.

Publication ingestion/integrity, hourly simulation, self-sufficiency feasibility,
ranked 194-candidate feasible results, immutable runs, UI, and deployed/AI gates
remain planned. Hosted CI was not dispatched during this local verification.
UI and Compose checks were not repeated because this change adds pure Java
model code and documentation without changing the existing application routes,
frontend, database schema, or container assembly.
