# Energy Configuration Architecture

Status: typed new-equipment generation, publication verification/cache,
simulation/ranking, durable run API, and consultant UI implemented and verified
locally. Deployed ODIP retrieval and platform policy acceptance remain open.
The dependency is
[contract kit 0.4.0](../../contracts/energy/README.md).

## Implemented boundary

`core.configuration.ElementSelection` is a domain-neutral positive integer
selection that distinguishes new/existing elements. It does not change binary
`Variant` equality or automatically produce an analysis population.
[ADR-014](../03-decisions/ADR-014-energy-configuration-identity.md) defines energy
identity and enumeration.

The pure Java `energy` package owns:

| Type | Responsibility |
| --- | --- |
| `EnergyComponent` | Sealed PV/inverter/battery types, explicit units, availability, price, and source owner/license/reference/retrieval time/version. |
| `EnergyCatalog` | Immutable schema/version/currency, unique model IDs, canonical order, 1–20 models per type, and available-model selection. |
| `EnergyOptimizationRequest` | Immutable dataset reference, site, constraints, objective, and fixed v1 economics assumptions. |
| `EnergyConfiguration` | Complete quantities/components, reference ID, selections, capacity, area, ratio, and initial investment. |
| `EnergyFeatureModel` | Independent model version, structural search estimate, limits, and bounded generation. |
| `EnergyModelException` | Model failure code for a later application/HTTP adapter. |
| `EnergyProfile` | Aligned hourly yield, load fraction, and tariff values for one immutable publication. |
| `EnergySimulation` | Binary64 hourly AC dispatch, balance checks, and raw totals. |
| `EnergyOptimizer` | Exhaustive feasibility and deterministic cost ranking. |

Constructors enforce supported schema majors, region `DE-BY-09187182`, EUR,
positive demand/technical attributes, bounded fractions, non-negative finite
price/roof/budget, unique existing IDs, and 20-year/0.04 assumptions. Aggregated
values must stay finite. Constructing a dataset reference does not prove
publication availability; that belongs to VEF's publication verifier and cache.

The model selects one PV model, one inverter, and either no battery or one
battery model. Available choices satisfy roof area, at most 100 PV units, and
DC/AC ratio `0.8..1.5`. Each valid PV/inverter pair yields one no-battery choice
plus four choices per available battery model. Search estimation uses checked
long arithmetic and the same pruning rules as generation. More than 100,000
evaluations fails before candidate allocation. Immutable output is sorted by ID.

Budget/self-sufficiency do not prune the evaluated population; violated customer
constraints remain available for diagnostics. The generator reproduces 252
reference configurations. The simulator and optimizer reproduce all released
hourly cases, 252 candidate results, the 194-candidate feasible ranking, and
the reference recommendation. Numerical output uses half-even rounding after
raw binary64 calculation.

The `adapter.odip` verifier admits a publication only after checking its
manifest and five artifact bytes, digests, roles, units, period, hourly alignment,
catalog, quality, and provenance. The client builds exact-version URLs from
validated identifiers, uses a configured HTTPS origin and managed bearer token,
refuses redirects, caps response sizes, and supports ETags. The V2 PostgreSQL
cache writes all bytes atomically under dataset ID/version and re-verifies a
loaded version. `EnergyPublicationImportService` uses the managed reader
binding to fetch and verify the exact version before storing it; an ODIP denial
is not replaced with cached bytes. Transient outages can reuse previously
verified cache bytes. The application descriptor declares HTTPS
egress to ODIP and the managed token host.
[ADR-015](../03-decisions/ADR-015-energy-simulation-and-publication.md)
records these choices.

## Limits of this slice

- Non-empty existing-equipment requests fail explicitly pending shared quantity/ownership/identity examples.
- Individually valid component IDs may concatenate into an ID exceeding 128 characters or colliding with another assignment. These cases fail instead of truncating or merging choices.
- The verifier enforces this profile's required fields and cross-document rules; full generic JSON Schema validation remains open.
- The general-purpose Boolean feature-model AST remains planned.
- ODIP's read API exists and has local acceptance evidence. VEF has not yet
  verified authenticated access to its deployed endpoint or deployed outage fallback.

## Durable run flow

The HTTP resource validates normalized principal context and reserves a run
after verifying the exact publication and search bound. V3 tables store the
request, publication digest, state, result, and ranked candidate pages. A
single PostgreSQL-backed worker executes the pure optimizer. The `/energy` UI
submits requests and displays progress, recommendation, candidate pages, and
dataset/calculation evidence. DTOs, repositories, and UI stay outside the pure
model. See [ADR-016](../03-decisions/ADR-016-energy-run-execution.md).

The upstream DEC-02 approves v1 dispatch and economics, adopted in ADR-015.

See the [implementation plan](../04-development/energy-configuration-implementation-plan.md)
and [verification record](../04-development/energy-model-verification.md).
