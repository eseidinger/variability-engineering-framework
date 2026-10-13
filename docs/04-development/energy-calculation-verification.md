# Energy Calculation and Publication Verification

Date: October 13, 2026. Status: locally verified pure calculation and verified
publication persistence against released contract kit `0.4.0` (SHA-256
`884e3590614328c2adafa457153e11575dc1be09b9f53d7b0f53704fe39d02b0`).
This record extends the earlier [model verification](energy-model-verification.md).
It does not claim an energy run API, UI, deployed ODIP integration, or full
cross-project acceptance.

## Evidence

The released archive was prepared by `scripts/validate-energy-contract-kit.py`
with `--prepare-only` and supplied through `ENERGY_CONTRACT_KIT_DIRECTORY`.
The Java tests read the released fixtures and expected results directly.

- All three released hourly simulation cases match every hourly and aggregate
  energy field after contract rounding.
- All 252 released optimization candidates match feasibility, four constraint
  outcomes, and every metric. The full 194-candidate feasible ranking and
  recommendation match. An impossible budget/self-sufficiency request yields
  an exhaustive `INFEASIBLE` result.
- The released manifest and all five artifact bytes verify, including the
  reference manifest digest `252b90b18b594a764a2744ba133a8db56b233e9e375301befc7616e9c18c1715`.
  Tampering, missing artifacts or hours, invalid units/status/quality/period,
  unsafe paths, invalid series values/load sum, and byte-limit violations fail.
- A PostgreSQL 17.11 round trip preserves the exact manifest and five artifact
  bytes and ETags. A changed publication under the same ID/version fails and
  leaves the original stored bytes intact. The local fixture transport proves
  exact-version URL construction and ETag revalidation; no live ODIP endpoint
  was contacted.

The full Quarkus API suite passed **49 tests, zero failures/errors/skips**. It
included the existing food-service tests and applied Flyway migrations V1 and
V2 to a disposable PostgreSQL 17.11 container. The command from
`apps/prototype/api` was:

```sh
ENERGY_CONTRACT_KIT_DIRECTORY=/path/to/prepared/contract-kit \
./mvnw --batch-mode --no-transfer-progress \
  -Dquarkus.datasource.devservices.enabled=false \
  -Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:TEST_PORT/vef_energy_tests \
  -Dquarkus.datasource.username=vef_energy_tests \
  -Dquarkus.datasource.password=vef_energy_tests test
```

`TEST_PORT` was a random loopback port. The container was removed afterward.
The first invocation in the restricted sandbox could not open a loopback socket;
the successful invocation used approved network access. No project `.env` was
changed.

## Remaining gates

Full JSON Schema enforcement, a live authenticated ODIP connection, authorized
verified-cache outage fallback, and deployed retrieval remain open. Existing
equipment lacks shared acceptance examples. Durable optimization runs,
idempotency, ownership, HTTP resources, UI, and deployed recovery/AI gates are
not implemented by this slice.

ODIP's publication API is implemented and locally verified in its own
repository. The [handoff review](odip-handoff-review.md) distinguishes that
external progress from VEF's untested authenticated connection.
