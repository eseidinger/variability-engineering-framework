# Energy Configuration Implementation Plan

Status: VEF implementation sequence, reviewed against source on October 12,
2026. Work package 1 is implemented and verified locally; hosted CI and the ODIP
consumer handoff remain pending. Other implementation items are pending. No
dates or estimates are committed, and no energy runtime/deployment acceptance
is claimed. Checked items indicate source and local evidence, not hosted CI.

## Outcome and authoritative baseline

Implement `UC-ENERGY-01` in the existing Quarkus API and Angular application: an
authorized consultant submits building constraints, receives a deterministic
PV/inverter/battery recommendation or infeasibility result, and can reproduce
the calculation from immutable inputs and evidence. Deliver the deterministic
workflow first, connect it to ODIP on Developer Platform, and then add confirmed
natural-language input and evidence-grounded AI explanations.

The authoritative cross-project baseline is:

- [Integration contract 0.4](https://github.com/eseidinger/energy-system-configuration/blob/93885442fd503d5bc529a732b6da47f6baffcfd1/energy-configuration-integration-contract.md), dated October 11, 2026.
- [Integration plan 0.2](https://github.com/eseidinger/energy-system-configuration/blob/93885442fd503d5bc529a732b6da47f6baffcfd1/energy-configuration-integration-plan.md) and [use case 0.2](https://github.com/eseidinger/energy-system-configuration/blob/93885442fd503d5bc529a732b6da47f6baffcfd1/energy-configuration-use-case.md).
- [Released contract kit 0.4.0](https://github.com/eseidinger/energy-system-configuration/releases/tag/contract-kit-v0.4.0), archive `contract-kit-v0.4.0.tar.gz`, SHA-256 `884e3590614328c2adafa457153e11575dc1be09b9f53d7b0f53704fe39d02b0`.
- Local VEF source revision `93793d1aa785a796b3d7fd9d41aa4cb81e1ef1b9`, inspected with a clean working tree before this plan was added.

The released archive was downloaded and its checksum verified during planning.
Its schemas, OpenAPI, reference implementation, and expected results were
inspected; the kit test suite was not executed during planning. Consume the
released archive in implementation and CI, not the mutable default branch.

Upstream Gate 0 decisions and maintainer review are complete. The kit is
published, but Gate 1 remains open until both ODIP and VEF pin it in CI. Contract
`1.0.0` acceptance follows the deployed and AI release gates. Adopt the recorded
decisions rather than reopening the approved demonstration assumptions.

## Current implementation and gaps

| Area | Source evidence | Required extension |
| --- | --- | --- |
| Analysis core | `core/AnalysisEngine.java`, immutable populations and records | Bounded configuration generation, quantitative component selections, feasibility, and deterministic ranking. |
| Structural identity | `core/Variant.java` compares sets of element IDs | Energy configuration identity must include selected models, quantities, and existing/new equipment semantics. |
| Data import | Verified food-service fixture adapter | Generic ODIP publication retrieval and independent energy-profile validation. |
| Persistence | `JdbcPopulationRepository`, Flyway population tables | Immutable imported bytes, model snapshots, durable run queue, ownership, idempotency, candidates, results, and evidence. |
| Authorization | `PopulationAuthorizationFilter` checks group IDs only | Validate complete boundary-supplied principal context and enforce stable-principal run ownership. |
| Browser identity | UI group selector and interceptor send identity headers | Use platform browser sessions; remove production client-generated identity headers and retain boundary CSRF handling. |
| Readiness | `/api/status` depends on the food-service store | Energy API/database/model readiness and separate ODIP dependency reporting. |
| Packaging and CI | Combined UI/API image, PostgreSQL tests, Compose verification | Shared-kit conformance, energy browser/API workload, restricted runtime, deployed integration, and recovery evidence. |

Paths in this table are relative to
`apps/prototype/api/src/main/java/de/eseidinger/variabilityengineering/` unless
otherwise stated. The existing food-service suite remains the regression
baseline. Its binary structural identity must keep its accepted meaning.

## Implementation boundaries

Use the existing API, UI, deployment assembly, and PostgreSQL baseline. Keep
domain-neutral configuration and run abstractions independent of HTTP and SQL;
put energy catalog interpretation, compatibility, units, and simulation in an
energy module. Introduce only abstractions exercised by this workflow.

Proposed source areas, created as their work starts:

| Location | Responsibility |
| --- | --- |
| `scripts/` and `.github/workflows/` | Pin, download, verify, and validate the released kit; expose its fixtures to consumer tests. |
| API `core/configuration/` | Immutable selections, quantity validation, canonical identity inputs, and bounded enumeration interfaces. |
| API `energy/` | Typed energy inputs, compatibility rules, dispatch simulation, economics, and candidate evaluation. |
| API `adapter/odip/` | Authenticated HTTPS client, integrity verification, publication validation, and verified-cache repository boundary. |
| API `application/optimization/` | Durable orchestration, run lifecycle, timeout, ownership, and evidence projection. |
| API `http/` and `persistence/` | Contract DTOs/resources, trusted principal middleware, JDBC repositories, and Flyway migrations. |
| UI `src/app/energy/` | Lazy-loaded request, run status, candidates, evidence, and later AI assistance. |
| `apps/prototype/integration-tests/` | Fixture-backed local workload and deployed cross-project acceptance harness. |

Energy configurations describe one system. Selecting the best configuration is
distinct from the future portfolio-selection capability in ADR-012. Do not make
a general expression language, portfolio solver, SDK extraction, or a second
backend a prerequisite. Record local ADRs before changing accepted semantics;
energy quantitative identity can initially be a separate type from `Variant`.

## Ordered work packages

### 1. Pin the shared contract kit — SHARED-01 / upstream Gate 1

- [x] Add a small dependency lock record containing release version, archive URL, checksum, and target contract/model versions.
- [x] Add reproducible download/extraction and CI validation. Verify the locked digest before extraction; cache by digest and fail on missing or mismatched content. Keep upstream licenses and attribution with any vendored fixture or schema.
- [x] Run the released repository-neutral suite using its frozen environment. Make schemas and fixture paths available to Java consumer tests without regenerating expected answers from the Java implementation.
- [x] Add a VEF contract-kit validation job alongside the existing documentation, API, UI, and Compose jobs. Record the consumer pin for the cross-project handoff; ODIP's independent pin is an external prerequisite for Gate 1 closure.

Implemented by the [consumer lock and usage documentation](../../contracts/energy/README.md)
and checksum-verifying [validation script](../../scripts/validate-energy-contract-kit.py).
See the [local verification record](energy-contract-kit-verification.md): 54
released kit tests and five consumer integrity tests pass. The API CI job exposes
`ENERGY_CONTRACT_KIT_DIRECTORY` for future Java tests; no Java energy consumer
conformance tests exist yet.

Exit: VEF CI validates the exact published kit and digest. Both consumer pins
must be evidenced before downstream implementation gates are claimed complete.

### 2. Record energy identity and model semantics — VEF-02 foundation

- [ ] Add an energy architecture document and local ADRs for quantitative selections/identity, simulation/economics, and durable run execution. Reference upstream DEC-01 through DEC-06 and the existing domain-independent core and verifiable-model ADRs.
- [ ] Implement immutable typed catalog, request, configuration, and model-version objects. Use explicit units and reject invalid/non-finite values; the executable schemas require strictly positive annual demand and positive technical capacities/efficiencies.
- [ ] Match the kit's deterministic configuration IDs and rules: one PV model, one inverter, no battery or 1–4 of one battery model; 1–100 PV modules within roof area; DC/AC ratio `0.8..1.5`; at most 20 catalog models per component type.
- [ ] Specify existing-equipment resolution, compatibility, quantities, and identity before its implementation. The request exposes unique `existingComponentIds`, while the reference oracle exercises no existing equipment. Obtain shared examples for non-empty inputs instead of inventing repeated-unit semantics locally.
- [ ] Estimate candidate evaluations with overflow-safe bounds before run creation. Reject more than 100,000 evaluations with `422 search_space_limit_exceeded`. Define structural pruning and count semantics to reproduce the oracle: investment/self-sufficiency failures remain evaluated candidates with diagnostics.

Exit: model and identity tests prove quantity/order behavior and the exact
configuration set, including no-battery and incompatible/unavailable cases.
Existing-equipment behavior has shared acceptance examples before that path ships.

### 3. Import and cache verified ODIP publications — VEF-01

- [ ] Implement the generic publication client against a stub serving the released manifest and five artifacts, then connect the same interface to ODIP's real API. Support ETags and conditional retrieval, with bounded timeouts/retries.
- [ ] Use only configured HTTPS ODIP and managed token origins. Obtain short-lived `client_credentials` tokens from the platform binding's `secretPrefix` and `_CLIENT_ID`, `_CLIENT_SECRET`, `_TOKEN_URL` values. Refresh safely and never log tokens or secrets.
- [ ] Reject absolute/cross-origin artifact paths, traversal, and redirects to unapproved locations. Enforce decoded-byte limits: 1 MiB manifest, 10 MiB per artifact, 50 MiB aggregate; abort streaming reads at the bound.
- [ ] Verify exact downloaded-byte SHA-256 values and canonical manifest identity, supported schema majors, `PUBLISHED` status, quality policy/checks, provenance, all five distinct roles, catalog IDs/counts, EUR/units, and technical attributes.
- [ ] Validate complete matching UTC one-hour intervals for the declared period, with no missing/duplicate timestamps. The reference year is leap-year 2024: 8,784 intervals. Validate non-negative profiles/tariffs and load-shape sum within `1e-6`.
- [ ] Atomically persist verified manifest/artifact bytes, checksums, parsed profile version, provenance, warnings, and verification state. A partial or failed import must never become a usable cache entry.
- [ ] Pin one exact publication before candidate evaluation. Reuse only that verified cached version during temporary ODIP outage; never substitute a newer version. Keep cached authorization subject to the current application/service access policy.

Exit: shared fixture import and PostgreSQL round trip preserve bytes/digests;
tampering, wrong units/major versions, missing hours, unpublished data, unsafe
locations, and oversized responses fail. Verified cached inputs survive restart.

### 4. Simulate and rank deterministically — VEF-02 / VEF-03

- [ ] Build pure bounded enumeration and compatibility checks against pinned catalog/model versions. Record exclusions and constraint outcomes without conflating operational failures with engineering infeasibility.
- [ ] Implement hourly DC generation, inverter efficiency before AC clipping, direct load supply, battery charge/discharge with AC-side power limits and square-root efficiencies, residual export/import, and explicit terminal battery-energy discard.
- [ ] Start state of charge at zero, prohibit grid charging and simultaneous charge/discharge, enforce capacity bounds and hourly conservation within `1e-6 kWh`, and reject unmet demand.
- [ ] Calculate initial investment for new equipment only, annualized cost with the capital-recovery factor, annual operations zero, and self-sufficiency from annual grid import/demand. Enforce the v1 request's 20 years and `0.04` rate. Preserve the helper's zero-rate formula for direct mathematical tests.
- [ ] Use binary64 without intermediate rounding. Serialize energy to six decimals, money to cents, and dimensionless values consistently with the kit; use half-even rounding. Test Java/Python parity at rounding boundaries rather than relying on default number formatting.
- [ ] Rank feasible candidates by annualized cost in cents, then investment in cents, then lexical configuration ID. Use `1e-9` dimensionless comparison tolerance. Claim `PROVEN_OPTIMAL` only after exhaustive completion of the stated search space.
- [ ] Compare all released simulation cases, every candidate's metrics/constraints, the complete ranking, and the recommendation against the upstream oracle. Include ties, impossible constraints, existing equipment, and boundary counts.

Exit: Java reproduces the shared numerical oracle. All candidates within a run
use identical dataset, model, simulation, and optimizer versions.

### 5. Persist and expose optimization runs — VEF-04 backend

- [ ] Add Flyway tables/repositories for verified publications, model snapshots, runs, idempotency records, evaluated candidates, terminal results, owned evidence, and application security audit. Store customer data only in VEF's database.
- [ ] Implement a PostgreSQL-backed queue with bounded workers, atomic claims, and transactional terminal writes. Initially run it in the API service; use a separate private worker only if measurements justify it.
- [ ] Enforce `QUEUED → RUNNING → SUCCEEDED | FAILED | INFEASIBLE`. Apply the 300-second execution deadline with cooperative cancellation; timeout yields `FAILED` and `run_timeout`. Define restart recovery for interrupted work and prevent stale workers from overwriting terminal results.
- [ ] Validate complete required normalized principal context and trust it only on the enforced private boundary-to-backend path. Scope ownership and idempotency to stable principal ID, never client ID, email, display name, or browser-selected groups.
- [ ] Atomically reserve `(principalId, Idempotency-Key)` with a canonical request hash. Identical concurrent submissions return one run; changed content yields `409 idempotency_conflict`. Apply search/input rejection before creating a run; define replay semantics for already-terminal runs against the released OpenAPI.
- [ ] Implement `POST /api/v1/optimization-runs`, run/status GET, candidates GET, and `GET /api/v1/capabilities` from the released OpenAPI. Return `202` for accepted submissions, machine-readable `error/v1` failures, stable page ordering, default page size 20, maximum 100, and `409` when candidates are not yet available.
- [ ] Expose required completed/infeasible result fields, diagnostics, counts, warnings, independent calculation versions, and checksums. Keep original inputs/results immutable and represent changed constraints as a new run.
- [ ] Build an owned evidence projection linking request fields, artifact digests, versions, configuration, constraints, and metrics. Retain correlation-to-run/dataset associations here rather than in central logs.

Exit: HTTP, PostgreSQL, concurrency, ownership, timeout, and restart tests prove
one durable run per idempotent request, accurate states, immutable history, and
reproducible evidence. Requests from another principal cannot read runs/candidates.

### 6. Deliver the deterministic consultant workflow — VEF-04 UI

- [ ] Add a lazy-loaded energy route with reactive forms for the supported region, selected immutable dataset version, roof, positive demand, existing equipment, budget, and self-sufficiency. Load supported bounds from capabilities.
- [ ] Show validation guidance, queued/running state, bounded polling, recommendation, candidate pages, constraint outcomes, failure/infeasibility diagnostics, and version/checksum evidence. Preserve the idempotency key across submission retries.
- [ ] Present the synthetic/reference-profile warning, terminal discarded energy, and excluded costs. The fixture maps PVGIS 2023 values into a derived 2024 leap-year profile; do not present it as measured customer or observed 2024 data.
- [ ] Use same-origin platform browser sessions and boundary CSRF token handling. Remove the production group-selector/header mechanism; keep any local identity fixture behind explicit test/dev configuration with a trusted test boundary. Handle login redirects, denial, and access-layer failures separately from application errors.
- [ ] Add browser workload checks for submission, progress/result, changed-constraint new run, candidate pagination, evidence inspection, and error recovery. Follow the UI AGENTS guidance: standalone components, signals, OnPush, reactive forms, AXE and WCAG AA checks.

Exit: the packaged Angular/API/PostgreSQL application completes the fixture
workflow locally and displays evidence and limitations accessibly. Existing
food-service checks still pass. This closes VEF's deterministic local slice.

### 7. Connect and verify deployed applications — DP-01..03 / INT-01

- [ ] Render kit deployment/access/governance examples for real hostnames, group IDs, image digests, managed bindings, and bounded CPU/memory/tmp. Retain `platform.example/v1alpha2`, one public service, at most five components, and VEF's separate `shared-dev` database with `deletionPolicy: retain`.
- [ ] Use the platform's browser-session route policy for VEF and the ODIP reader service approval for publication retrieval. Configure separate non-overlapping policies if machine-facing VEF routes are added later. Verify missing/malformed context, pending membership, revocation, reader write denial, dataset allow-list denial, and direct-backend isolation.
- [ ] Declare only ODIP and token-endpoint HTTPS egress on calling components; verify unrelated egress denial, record fixed DNS/CIDR pins, and reapply on address changes. Exercise credential rotation/adoption and emergency revocation; assign the consumer's 90-day rotation owner/schedule.
- [ ] Make private readiness check API/database/model availability and report ODIP outages separately. Test restricted non-root/read-only runtime. Store durable bytes/results in PostgreSQL; `/tmp` is bounded scratch storage, and the platform profile provides no application PVC.
- [ ] Emit only schema-valid compact `operational-envelope-v1` logs within 4 KB, with approved events/level and opaque correlation. Implement low-cardinality request/import/queue/run/simulation metrics independently. Never put domain IDs, customer fields, tokens, arbitrary messages, or correlation into metric labels.
- [ ] Verify protected public probes separately from Kubernetes readiness and authenticated retrieval. Exercise project-authorized retained-log reads, denied access, backend `state: unavailable`, restarted/retired-pod readback, and actual seven-day expiry with collector/compactor evidence.
- [ ] Enforce application-owned requests/results/evidence and security-audit retention of 180 days, with a named purge/recovery owner. Record ODIP's separate version retention and platform snapshot retention; live-row deletion does not prove removal from retained backups.
- [ ] Extend the deployed harness to run the complete upstream Gate 5 suite: energy and non-energy publication, exact reference result, all negative cases, corrected `1.0.1` isolation, verified-cache outage fallback, restart, and actual backup restoration/reproduction. Restore identity/access state before protected routes reopen.

Exit: ODIP and Developer Platform handoffs pass, authenticated VEF retrieval
works, and `INT-01` passes with recorded revisions/digests/environment and
recovery evidence. A protected public response or three healthy deployments
alone cannot close this gate. PostgreSQL traffic in the accepted single-host
lab profile is not TLS-protected; report that profile accurately.

### 8. Add AI assistance and release — VEF-05 / upstream Gate 6

- [ ] Add a bounded natural-language proposal interface with explicit missing/uncertain fields. Display a structured request for mandatory consultant confirmation before run creation; ordinary structured submission remains available.
- [ ] Feed explanations only the approved bounded evidence view. Cite run, dataset/version, model/simulation, configuration, constraints, annualized cost, and self-sufficiency. Distinguish customer input, source data, derived profiles, assumptions, and calculated metrics.
- [ ] Treat source metadata as untrusted data. Test prompt injection, misleading requests, absent evidence, unsupported numbers, and forbidden publication/rule changes. Record explanation version/evidence links without changing deterministic results.
- [ ] Re-run the deterministic deployed suite unchanged. Record all three owners' acceptance and pin shared-kit, application/image, policy, and deployment versions before contract `1.0.0` release.

Exit: confirmed natural-language input and evidence-grounded AI explanation pass
alongside unchanged deterministic/recovery acceptance. A deterministic template
alone does not complete the AI-assisted release.

## Reference acceptance targets

These values come from the released `expected-results/optimization-reference.json`,
not a new VEF calculation performed during planning. Tests must consume the full
oracle rather than checking only this summary.

| Field | Expected value |
| --- | --- |
| Dataset | `energy-reference-wasserburg-am-inn-2024`, `1.0.0` |
| Region / hourly intervals | `DE-BY-09187182` / 8,784 |
| Manifest SHA-256 | `252b90b18b594a764a2744ba133a8db56b233e9e375301befc7616e9c18c1715` |
| Evaluated / feasible candidates | 252 / 194 |
| Recommended configuration | `pv-standard-400-q18-inverter-5kw-battery-10kwh-q1` |
| Optimality | `PROVEN_OPTIMAL` |
| Initial investment / annualized cost | EUR 8,240.00 / EUR 360.67 |
| Self-sufficiency | `0.889509006` |
| Grid import / export | `497.209474` / `5191.571129` kWh |
| Inverter clipping / terminal discarded energy | `233.288106` / `3.480110` kWh |
| Unmet demand | `0.000000` kWh |

Ignore only run-specific IDs/timestamps when comparing numerical results;
assert their shape and lifecycle separately. Monetary outputs compare exactly,
energy within `1e-6 kWh`, and dimensionless calculations within `1e-9`.

## Dependencies, unresolved details, and validation

| Dependency or question | Owner / completion condition |
| --- | --- |
| Both consumers pin kit 0.4.0 | VEF and ODIP; closes shared Gate 1. |
| Generic publication, energy adapter, reader allow-list | ODIP / ENERGY-01; real authenticated API and fixture publication pass before deployed VEF acceptance. Local client development uses the released fixture stub. |
| Managed identity, routing, governance, secret binding, egress and recovery | Developer Platform plus application owners; use kit-pinned boundary `b360ddee34444748c95705c597aa7b17491cd05b` or document/retest compatibility of a newer deployment. |
| Non-empty existing equipment | Shared-kit owner and VEF; approved quantity, compatibility, and canonical-ID examples precede this path's implementation acceptance. |
| Idempotent replay of a terminal run | Shared-kit owner and VEF; the prose returns the original run, while `RunAccepted` currently fixes status to `QUEUED`. Clarify whether POST returns the original acceptance envelope or amend the schema before claiming conformance. |
| Ordering infeasible candidates in candidate pages | VEF and shared-kit owner; the oracle ranks the feasible subset and lists all evaluated candidates separately. Document a stable diagnostic ordering without changing feasible ranking, or publish a shared clarification. |
| Worker concurrency/resource bounds | VEF; measure the reference year and largest supported catalog within the 300-second deadline, then set conservative deployment limits. |

The shared baseline already resolves the first region/year, calculation
assumptions, security topology, kit ownership, limits, and retention. The details
above refine missing consumer semantics; they do not block the kit pin or
fixture-backed simulator. Any contract/schema amendment belongs upstream and
requires a new immutable consumer pin.

For each work package, record the revision, kit digest, test command,
environment, outcome, and limitations. Run the existing API, UI, documentation,
and Compose checks plus the relevant new conformance tests. Use PostgreSQL tests
for persistence/concurrency, browser tests for the user workflow, and deployed
tests for boundary isolation/recovery; one level cannot substitute for another.
Measure runtime and peak memory before setting operational resource claims.

The first reviewable implementation change contains the consumer lock, kit
validation script/job, attribution, and handoff evidence. Follow with
separate changes for quantitative model identity, publication import,
simulation/ranking, durable APIs, UI, platform acceptance, and AI assistance.
Update this plan's checkboxes only when their stated evidence exists.
