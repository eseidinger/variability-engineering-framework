# Energy Configuration Architecture

Status: typed new-equipment generation, publication verification/cache,
simulation/ranking, durable owned run API, consultant UI, and AI proposals and
explanations are implemented and tested locally. The owner confirmed the
ODIP-backed production workflow and guided AI checks on October 19, 2026.
Subsequent guided checks through October 26 cover the full reference oracle,
cross-user denial, provider recovery, cache fallback, restart and idempotency.
AI-05/06/08/10/11, remaining regression/recovery and security-boundary evidence
remain open; see the [acceptance record](../04-development/energy-contract-kit-verification.md).
The dependency is [contract kit 0.5.0](../../contracts/energy/README.md), which
adds AI contracts without changing the accepted deterministic numerical baseline.

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
| `EnergyModelException` | Model failure code mapped by the application/HTTP adapter. |
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

- The current release covers new installations under maintainer-approved [DEC-07](https://github.com/eseidinger/energy-system-configuration/blob/main/decisions/DEC-07-new-equipment-release-scope.md). `existingComponentIds` must be empty; non-empty requests fail with `existing_equipment_not_supported`. Existing-equipment quantities, ownership and identity belong to a later version.
- Individually valid component IDs may concatenate into an ID exceeding 128 characters or colliding with another assignment. These cases fail instead of truncating or merging choices.
- Packaged, byte-verified kit 0.5.0 JSON Schemas validate publications, requests and AI envelopes offline, including optional fields and formats. Cross-document rules remain explicit. Maintainer-approved [DEC-08](https://github.com/eseidinger/energy-system-configuration/blob/main/decisions/DEC-08-complete-calendar-year.md) requires one complete UTC calendar year. Import and the typed profile now enforce January-to-January boundaries and the exact 8,760/8,784 aligned hours; partial or rolling years are rejected.
- The general-purpose Boolean feature-model AST remains planned.
- Authenticated ODIP-backed runs, dataset denial, verified-cache outage fallback
  and completed-run retrieval after VEF restart have guided hosted evidence.
  Corrected-version isolation now passes both full deployed oracles; see the
  [guided acceptance](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/2026-10-27-guided-release-acceptance.md).
  Remaining negative cases, ODIP restart/observability and actual integrated
  backup restoration remain open.

## Durable run flow

The HTTP resource validates normalized principal context and reserves a run
after verifying the exact publication and search bound. V3 tables store the
request, publication digest, state, result, and ranked candidate pages. A
single PostgreSQL-backed worker executes the pure optimizer. The `/energy` UI
submits requests and displays progress, recommendation, candidate pages, and
dataset/calculation evidence. DTOs, repositories, and UI stay outside the pure
model. See [ADR-016](../03-decisions/ADR-016-energy-run-execution.md).

V4 adds application security audit records for owned run, candidate and explanation reads,
and hourly 180-day terminal-run/audit cleanup. Candidate rows cascade with the run;
active runs and shared publication cache bytes remain. ODIP dependency metrics expose
the last observed state, observation time and cache fallbacks on the private management
interface without changing readiness. See the
[local gap-closure record](../04-development/energy-gap-closure-verification.md) for
verification, deployment implications and remaining recovery limits.

The upstream DEC-02 approves v1 dispatch and economics, adopted in ADR-015.

## AI assistance boundary

The API uses OpenAI through Quarkus LangChain4j for natural-language request
proposals and explanations. VEF validates the proposal envelope and typed
request; the UI displays the request and assumptions and requires explicit
confirmation before normal run creation. Editing the description or structured
request clears confirmation. Proposals cannot create runs themselves.

For an explanation, VEF reads an owned completed run and builds a snapshot
limited to 16 sources and 64 KiB of UTF-8 JSON. Customer inputs, assumptions,
the recorded result, and the recommended candidate (for success) are separate
sources. VEF validates explanation shape, categories, exact JSON Pointer
citations and values, required result facts, and infeasibility semantics before
display. Draft 2020-12 schema validation supplements these profile checks; neither establishes the semantic accuracy of every claim. The model has no tools and
cannot modify the deterministic result.
Provider failures return `503`; invalid explanations return `502`. Saved results
remain readable. See the [API model](system-model/api-model.md) for route contracts.

AI is disabled by default in the API configuration and enabled in both platform
descriptors. Provider credentials, declared egress, and a browser access policy
are required. Hosted trace readback, adversarial behavior, and remaining AI
acceptance cases require separate evidence; successful generation does not
establish them.

See the [implementation plan](../04-development/energy-configuration-implementation-plan.md)
and [verification record](../04-development/energy-contract-kit-verification.md).
