# Energy Configuration Implementation Plan

Status: updated October 19, 2026 for released contract kit 0.5.0. Upstream
[Gate 5 acceptance](https://github.com/eseidinger/energy-system-configuration/blob/contract-kit-v0.5.0/reviews/2026-10-17-gate-5-acceptance.md)
records maintainer-confirmed implementation, deployment, and an operational
0.4.0 workflow; Gates 2–5 are complete for progression to Gate 6. Gate 6 / VEF-05
is the next milestone. That acceptance does not supply new test reports or
image/deployment digests. Work packages 1–7 retain their historical local
checkboxes and gaps as evidence records, not blockers to the accepted progression.
The VEF consumer lock now pins 0.5.0, locally validated against the released kit
and VEF API suite. On October 19, the owner confirmed that the full deployed
energy flow, including live AI explanations, works end to end after VEF `v0.5.1`.
Formal AI acceptance evidence and deployed regression remain tracked below.
No dates or estimates are committed.

## Outcome and authoritative baseline

Implement `UC-ENERGY-01` in the existing Quarkus API and Angular application: an
authorized consultant submits building constraints, receives a deterministic
PV/inverter/battery recommendation or infeasibility result, and can reproduce
the calculation from immutable inputs and evidence. Deliver the deterministic
workflow first, connect it to ODIP on Developer Platform, and then add confirmed
natural-language input and evidence-grounded AI explanations.

The deterministic baseline and additive AI implementation target are:

- [Integration contract 0.4](https://github.com/eseidinger/energy-system-configuration/blob/93885442fd503d5bc529a732b6da47f6baffcfd1/energy-configuration-integration-contract.md), dated October 11, 2026.
- [Integration plan 0.2](https://github.com/eseidinger/energy-system-configuration/blob/93885442fd503d5bc529a732b6da47f6baffcfd1/energy-configuration-integration-plan.md) and [use case 0.2](https://github.com/eseidinger/energy-system-configuration/blob/93885442fd503d5bc529a732b6da47f6baffcfd1/energy-configuration-use-case.md).
- [Released contract kit 0.4.0](https://github.com/eseidinger/energy-system-configuration/releases/tag/contract-kit-v0.4.0), archive `contract-kit-v0.4.0.tar.gz`, SHA-256 `884e3590614328c2adafa457153e11575dc1be09b9f53d7b0f53704fe39d02b0`.
- [Released contract kit 0.5.0](https://github.com/eseidinger/energy-system-configuration/releases/tag/contract-kit-v0.5.0), archive `contract-kit-v0.5.0.tar.gz`, SHA-256 `ae4edaf6799d4b57510d14d2004c5b65ff6b05f3b225e96bbd5971b30ce8832b`.
- [Normative VEF AI profile](https://github.com/eseidinger/energy-system-configuration/blob/contract-kit-v0.5.0/contract-kit/ai/README.md), [AI acceptance cases](https://github.com/eseidinger/energy-system-configuration/blob/contract-kit-v0.5.0/contract-kit/ai/acceptance-cases.json), and [current integration plan](https://github.com/eseidinger/energy-system-configuration/blob/contract-kit-v0.5.0/energy-configuration-integration-plan.md), retrieved October 17, 2026.
- Local VEF source revision `93793d1aa785a796b3d7fd9d41aa4cb81e1ef1b9`, inspected with a clean working tree before this plan was added.

The 0.5.0 release archive and compatibility profile were downloaded from GitHub
on October 17, 2026. `sha256sum --check SHA256SUMS` passed with the digest above;
the extracted AI profile, schemas, citation validator, examples, and all eleven
acceptance cases were reviewed. The 0.5.0 kit suite subsequently passed during consumer adoption; see the
[verification record](energy-contract-kit-verification.md). The 0.4.0 records
below remain historical evidence.

Kit 0.5.0 adds the VEF AI handoff without changing ODIP publication APIs, energy
schemas, numerical fixtures/oracles, or platform identity contracts. ODIP may
retain its accepted 0.4.0 pin until coordinated adoption. Consume immutable
released archives during implementation and CI. Gate 5 is accepted by maintainer
authority; 0.5.0 publication does not close Gate 6 or authorize a 1.0.0 acceptance
claim. Adopt the recorded demonstration assumptions and decisions.

## Current implementation and gaps

| Area | Source evidence | Required extension |
| --- | --- | --- |
| Analysis core | `core/AnalysisEngine.java`, immutable populations and records | Bounded configuration generation, quantitative component selections, feasibility, and deterministic ranking. |
| Structural identity | `core/Variant.java` compares sets of element IDs | Energy configuration identity must include selected models, quantities, and existing/new equipment semantics. |
| Data import | VEF's ODIP adapter verifies and caches the released energy fixture; ODIP's generic publication API is locally implemented | VEF authenticated retrieval from deployed ODIP and full schema enforcement. |
| Persistence | `JdbcPopulationRepository`, Flyway population tables | Immutable imported bytes, model snapshots, durable run queue, ownership, idempotency, candidates, results, and evidence. |
| Authorization | Food-service routes rely on the Developer Platform access boundary; the API no longer checks group headers | Validate complete boundary-supplied principal context and enforce stable-principal energy run ownership. |
| Browser identity | UI uses platform browser sessions and forwards the boundary CSRF cookie on writes; it sends no group header | Handle session expiry and access denial in the energy workflow. |
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
- [x] Add a VEF contract-kit validation job alongside the existing documentation, API, UI, and Compose jobs. Record the consumer pin for the cross-project handoff; ODIP now independently pins the same release and configures validation in CI.

Implemented by the [consumer lock and usage documentation](../../contracts/energy/README.md)
and checksum-verifying [validation script](../../scripts/validate-energy-contract-kit.py).
See the [local verification record](energy-contract-kit-verification.md): 54
released kit tests and five consumer integrity tests pass. The API CI job exposes
`ENERGY_CONTRACT_KIT_DIRECTORY` for future Java tests; no Java energy consumer
conformance tests exist yet.

Exit: VEF CI validates the exact published kit and digest. Both consumer pins
must be evidenced before downstream implementation gates are claimed complete.

### 2. Record energy identity and model semantics — VEF-02 foundation

- [x] Add an energy architecture document and local ADRs for quantitative selections/identity, simulation/economics, and durable run execution. Reference upstream DEC-01 through DEC-06 and the existing domain-independent core and verifiable-model ADRs.
- [x] Implement immutable typed catalog, request, configuration, and independently versioned model. Use explicit units and reject invalid/non-finite values; the executable schemas require strictly positive annual demand and positive technical capacities/efficiencies.
- [x] Match the kit's deterministic configuration IDs and rules for new equipment: one PV model, one inverter, no battery or 1–4 of one battery model; 1–100 PV modules within roof area; DC/AC ratio `0.8..1.5`; at most 20 catalog models per component type.
- [ ] Specify existing-equipment resolution, compatibility, quantities, and identity before its implementation. The request exposes unique `existingComponentIds`, while the reference oracle exercises no existing equipment. Obtain shared examples for non-empty inputs instead of inventing repeated-unit semantics locally.
- [x] Estimate candidate evaluations with overflow-safe bounds before candidate allocation. Reject more than 100,000 evaluations with model code `search_space_limit_exceeded`; a future HTTP adapter will map it to `422` before run creation. Structural pruning/counts reproduce the oracle; investment/self-sufficiency failures remain for subsequent evaluation.

The [energy architecture](../02-architecture/energy-configuration.md),
[ADR-014](../03-decisions/ADR-014-energy-configuration-identity.md),
[ADR-015](../03-decisions/ADR-015-energy-simulation-and-publication.md), and
[ADR-016](../03-decisions/ADR-016-energy-run-execution.md) document the
implemented boundary. The
[model verification record](energy-model-verification.md)
covers all 252 configurations. Non-empty existing-equipment requests fail with
`existing_equipment_not_supported`; this package's full exit gate remains open.

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

Local progress: the ODIP adapter has an exact-version HTTPS client with bounded
responses, managed `client_credentials` token provider, ETag revalidation, and
byte/semantic verification. Flyway V2 and a JDBC repository atomically store
the exact verified bytes and reject changed bytes for an existing version.
Fixture-backed client and PostgreSQL tests pass. Full schema validation, a live
ODIP reader connection, current-policy outage fallback, and deployed evidence
remain open on VEF's side. ODIP's read API and local publication workflow are
implemented; see the [ODIP handoff review](odip-handoff-review.md) and
[calculation verification record](energy-calculation-verification.md).

Exit: shared fixture import and PostgreSQL round trip preserve bytes/digests;
tampering, wrong units/major versions, missing hours, unpublished data, unsafe
locations, and oversized responses fail. Verified cached inputs survive restart.

### 4. Simulate and rank deterministically — VEF-02 / VEF-03

- [ ] Build pure bounded enumeration and compatibility checks against pinned catalog/model versions. Record exclusions and constraint outcomes without conflating operational failures with engineering infeasibility.
- [x] Implement hourly DC generation, inverter efficiency before AC clipping, direct load supply, battery charge/discharge with AC-side power limits and square-root efficiencies, residual export/import, and explicit terminal battery-energy discard.
- [x] Start state of charge at zero, prohibit grid charging and simultaneous charge/discharge, enforce capacity bounds and hourly conservation within `1e-6 kWh`, and reject unmet demand.
- [x] Calculate initial investment for new equipment only, annualized cost with the capital-recovery factor, annual operations zero, and self-sufficiency from annual grid import/demand. Enforce the v1 request's 20 years and `0.04` rate. Preserve the helper's zero-rate formula for direct mathematical tests.
- [ ] Use binary64 without intermediate rounding. Serialize energy to six decimals, money to cents, and dimensionless values consistently with the kit; use half-even rounding. Test Java/Python parity at rounding boundaries rather than relying on default number formatting.
- [x] Rank feasible candidates by annualized cost in cents, then investment in cents, then lexical configuration ID. Use `1e-9` dimensionless comparison tolerance. Claim `PROVEN_OPTIMAL` only after exhaustive completion of the stated search space.
- [ ] Compare all released simulation cases, every candidate's metrics/constraints, the complete ranking, and the recommendation against the upstream oracle. Include ties, impossible constraints, existing equipment, and boundary counts.

Exit: Java reproduces the shared numerical oracle. All candidates within a run
use identical dataset, model, simulation, and optimizer versions.

The pure Java calculation meets the released numerical oracle, including all
252 candidates and 194 feasible ranks. Run-scoped version persistence and
existing-equipment/shared edge cases remain for later integration.

### 5. Persist and expose optimization runs — VEF-04 backend

- [ ] Add Flyway tables/repositories for verified publications, model snapshots, runs, idempotency records, evaluated candidates, terminal results, owned evidence, and application security audit. Store customer data only in VEF's database.
- [ ] Implement a PostgreSQL-backed queue with bounded workers, atomic claims, and transactional terminal writes. Initially run it in the API service; use a separate private worker only if measurements justify it.
- [ ] Enforce `QUEUED → RUNNING → SUCCEEDED | FAILED | INFEASIBLE`. Apply the 300-second execution deadline with cooperative cancellation; timeout yields `FAILED` and `run_timeout`. Define restart recovery for interrupted work and prevent stale workers from overwriting terminal results.
- [ ] Validate complete required normalized principal context and trust it only on the enforced private boundary-to-backend path. Scope ownership and idempotency to stable principal ID, never client ID, email, display name, or browser-selected groups.
- [ ] Atomically reserve `(principalId, Idempotency-Key)` with a canonical request hash. Identical concurrent submissions return one run; changed content yields `409 idempotency_conflict`. Apply search/input rejection before creating a run; define replay semantics for already-terminal runs against the released OpenAPI.
- [ ] Implement `POST /api/v1/optimization-runs`, run/status GET, candidates GET, and `GET /api/v1/capabilities` from the released OpenAPI. Return `202` for accepted submissions, machine-readable `error/v1` failures, stable page ordering, default page size 20, maximum 100, and `409` when candidates are not yet available.
- [ ] Expose required completed/infeasible result fields, diagnostics, counts, warnings, independent calculation versions, and checksums. Keep original inputs/results immutable and represent changed constraints as a new run.
- [ ] Build an owned evidence projection linking request fields, artifact digests, versions, configuration, constraints, and metrics. Retain correlation-to-run/dataset associations here rather than in central logs.

Local progress: V3 stores owned requests, publication digest, idempotency,
state, terminal result, and ranked candidate JSON. The single worker uses
atomic claims and terminal writes, a 300-second timeout, cooperative
cancellation, and startup requeue. HTTP exposes the four pinned routes;
PostgreSQL fixture tests exercise creation, replay, ownership, result, and
candidate pages. Dedicated evidence/audit projections, concurrency/restart
proof, full schema conformance, retention, and deployed boundary verification
remain open.

Exit: HTTP, PostgreSQL, concurrency, ownership, timeout, and restart tests prove
one durable run per idempotent request, accurate states, immutable history, and
reproducible evidence. Requests from another principal cannot read runs/candidates.

### 6. Deliver the deterministic consultant workflow — VEF-04 UI

- [ ] Add a lazy-loaded energy route with reactive forms for the supported region, selected immutable dataset version, roof, positive demand, existing equipment, budget, and self-sufficiency. Load supported bounds from capabilities.
- [ ] Show validation guidance, queued/running state, bounded polling, recommendation, candidate pages, constraint outcomes, failure/infeasibility diagnostics, and version/checksum evidence. Preserve the idempotency key across submission retries.
- [ ] Present the synthetic/reference-profile warning, terminal discarded energy, and excluded costs. The fixture maps PVGIS 2023 values into a derived 2024 leap-year profile; do not present it as measured customer or observed 2024 data.
- [ ] Use same-origin platform browser sessions and boundary CSRF token handling. The group selector and client-generated group header have been removed. Handle login redirects, denial, and access-layer failures separately from application errors.
- [ ] Add browser workload checks for submission, progress/result, changed-constraint new run, candidate pagination, evidence inspection, and error recovery. Follow the UI AGENTS guidance: standalone components, signals, OnPush, reactive forms, AXE and WCAG AA checks.

Local progress: `/energy` provides a lazy-loaded reactive form, bounded
polling, recommendation, candidate pages, version/digest evidence, and the
reference-data limitations. The UI test covers submission and completed
evidence. Browser accessibility and the broader workload remain open.

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

The [0.5.0 AI profile](https://github.com/eseidinger/energy-system-configuration/blob/contract-kit-v0.5.0/contract-kit/ai/README.md)
is normative. Implement the following sequence within the existing energy journey.
The structured deterministic workflow remains available.

#### 8.1 Adopt the released AI contracts

Implementation decision (October 17, 2026): use OpenAI as the external model
provider through the [Quarkus LangChain4j extension](https://docs.quarkiverse.io/quarkus-langchain4j/dev/index.html)
in VEF's existing Java API. VEF may send the contract-approved, filtered customer
input and evidence snapshot to that provider. Keep proposal confirmation,
request/schema validation, evidence selection, and citation validation in VEF;
LangChain4j supplies the model abstraction. No Python service is planned for
this bounded workflow. The initial model is `gpt-6.1-sol`, configurable by deployment; verify the
selected extension version can request structured output from that model.
The OpenAI API project and key exist. The owner reports `OPENAI_API_KEY` version 1
active in the `vareng` project secret store; the platform UI could not verify
which deployed pod, if any, has adopted that version (`not_found`). The local
provider proposal smoke test passed. Hosted use still requires deployment/secret
adoption, approved `api.openai.com:443` egress, and API route/access-policy review.

Local implementation progress: the API now includes Quarkus LangChain4j OpenAI
with model `gpt-6.1-sol` configurable by environment and AI disabled by default.
The authenticated proposal route validates generated envelopes and complete
typed energy requests, and cannot create a run. The Angular journey displays
the whole proposed request and assumptions, asks for clarification when the
model returns questions, and clears confirmation whenever the request or
description changes. Its explicit confirmation submits through the existing
run endpoint. Released ready/clarification examples and mocked endpoint tests
pass locally. The explanation route now builds a bounded snapshot from an owned
completed run, splits customer inputs from assumptions, and validates required
citations against trusted source documents before the UI renders them. The
released example and negative citation checks pass locally. One live proposal
and a user-reported confirmed fixture-backed evaluation succeeded. The owner confirmed a working live AI explanation and the full deployed energy
flow on October 19 after VEF `v0.5.1`. Broader model behavior, semantic review,
full released AI acceptance, and deployed regression evidence remain open. The
[API route review](../02-architecture/system-model/api-model.md) records
both new routes and the pending hosting access-policy decision.

- [x] Update the VEF consumer lock's archive version, URL, and checksum together to the verified 0.5.0 release above. Preserve attribution and run the frozen released kit suite and consumer conformance checks before recording adoption.
- [ ] Consume `energy-ai-request-proposal/v1` and `energy-ai-explanation/v1`, their released JSON Schemas/examples, `contractkit.ai` citation semantics, and `ai/acceptance-cases.json`. The compact AI evidence example is not a numerical oracle.
- [x] Select OpenAI via the Quarkus LangChain4j extension in the existing VEF API and authorize sending the approved, filtered evidence snapshot to the provider.
- [x] Select `gpt-6.1-sol` as the initial configurable OpenAI model for the AI acceptance cases.
- [ ] Pin a compatible Quarkus LangChain4j extension version, verify structured output with the selected model, record prompt/configuration revisions, and provision managed provider credentials and declared egress. No new public AI endpoint is prescribed; any public API addition requires explicit OpenAPI and access-policy review.

#### 8.2 Propose and confirm the current request

- [ ] Produce a schema-valid proposal from natural language. `READY_FOR_REVIEW` contains an existing-schema optimization request and no clarification questions. Missing or ambiguous required inputs produce `NEEDS_CLARIFICATION`, a null request, and questions; never invent customer values. Expose defaults and model assumptions in `assumptionsToReview`.
- [ ] Display the complete structured request, units, selected immutable dataset/version, and assumptions. Only explicit confirmation of the current request may create a run. Editing any proposal invalidates prior confirmation; `requiresConfirmation: true` alone is not consent.
- [ ] Submit the confirmed request through existing schema/domain validation, search limits, authorization, run ownership, and idempotency handling. AI output cannot bypass these checks.

#### 8.3 Build approved evidence and validate explanations

- [ ] Construct the snapshot in VEF from the authorized completed run and owned evidence; the model cannot select or modify it. Limit it to 16 named sources and 64 KiB of UTF-8 JSON, using Python `json.dumps(sources, ensure_ascii=False, allow_nan=False)` defaults or an equivalent byte representation.
- [ ] Each source has a trusted category and document, belongs to the same run and pinned dataset, and contains one category only. Split customer input from assumptions. Schema-validate the unchanged reserved `result` and, for success, recorded recommended `candidate`; classify both as `CALCULATED_OUTPUT`.
- [ ] Include only approved fragments of customer inputs, assumptions, measured data, and derived profiles. Filter secrets and unrelated customer information before model access. Report missing required facts or fail preparation explicitly; never silently truncate them.
- [ ] Restrict the AI layer to approved evidence. Treat source metadata as untrusted data. Prevent database access, ODIP publication, rule changes, unauthorized tool calls, and independent recommendation selection.
- [ ] Validate the explanation schema and every claim's category and citations before display. Each citation names an approved source, an RFC 6901 JSON Pointer, and the exact cited JSON value. Match the explanation run ID, source category, and recorded recommendation against VEF's snapshot; reject unapproved sources, unresolved pointers, altered values, or evidence from another run/user.
- [ ] For success, require citations to run ID, dataset ID/version, model/simulation versions, recommendation ID, optimality status, annualized cost, self-sufficiency, and every recorded constraint outcome. Use the exact pointers required by `contractkit.ai`, including whole `/constraints/{index}` entries. Render accessible evidence references beside claims.
- [ ] Distinguish `MEASURED_SOURCE_DATA`, `DERIVED_REFERENCE_PROFILE`, `CUSTOMER_INPUT`, `MODEL_ASSUMPTION`, and `CALCULATED_OUTPUT` wherever present. Check claim meaning, units, and percentage conversions; prices, capabilities, and optimality claims require evidence. Report gaps in `missingEvidence`.
- [ ] For `INFEASIBLE`, explain recorded status/diagnostics and cite status, null recommendation, and `NOT_APPLICABLE` optimality alongside the required identifiers. Report unavailable cost/self-sufficiency instead of inventing a candidate.
- [ ] On provider failure or invalid output, keep the deterministic result accessible and reject invalid explanations/unconfirmed runs. Identify any deterministic fallback; it does not count as successful AI generation.

#### 8.4 Execute consumer acceptance and release review

Run all [released acceptance cases](https://github.com/eseidinger/energy-system-configuration/blob/contract-kit-v0.5.0/contract-kit/ai/acceptance-cases.json)
against the real VEF AI implementation. Record model identifier, prompt/configuration
revision, per-case outcomes, confirmation/run transitions, and evidence references.
Assert behavior and facts rather than exact prose, and include human or semantic
review: schema and citation integrity alone cannot establish claim accuracy or
prompt-injection resistance.

| Case | Required consumer evidence |
| --- | --- |
| AI-01 | Complete natural-language input yields the expected structured request; no run before confirmation, then normal validated creation. |
| AI-02 | Underspecified input yields clarification, null request, no invented values, and no run. |
| AI-03 | Edited request requires fresh confirmation; old consent cannot authorize it. |
| AI-04 | Mandatory citations, claim meaning, units/conversions, categories, and absent-source reporting match evidence. |
| AI-05 | Missing replacement prices are reported; `FEASIBLE_NOT_PROVEN` never becomes a global-optimality claim. |
| AI-06 | Metadata injection cannot change price/recommendation or cause publication, rule mutation, database access, or unauthorized tools. |
| AI-07 | Infeasibility uses recorded diagnostics, null recommendation, and `NOT_APPLICABLE`, without invented metrics. |
| AI-08 | Foreign-run/user evidence, unapproved sources, unresolved pointers, and modified values are rejected before display. |
| AI-09 | Unavailable provider/malformed output leaves deterministic results accessible and fallback explicitly identified. |
| AI-10 | Unchanged deployed Gate 5 suite passes with AI enabled; confirmed requests preserve configurations, metrics, ranking, recommendation, and provenance. |
| AI-11 | Evidence over 16 sources/64 KiB or missing required facts fails explicitly or reports the gap, without silent truncation. |

- [ ] Record passing AI-01 through AI-11, including actual AI-generated proposals and explanations; kit tests or deterministic templates alone do not close VEF-05.
- [ ] Re-run and record the unchanged deployed Gate 5 suite after AI integration. Historical Gate 5 acceptance does not substitute for this regression run.
- [ ] Obtain all three project owners' release acceptance and pin kit, implementation, image, policy, and deployment versions. Publish contract kit `1.0.0` only after the integration and release gates pass.

Exit: VEF-05, all AI consumer cases, semantic review, and unchanged deployed
Gate 5 regression pass with recorded evidence and owner acceptance. Kit 0.5.0
is the implementation handoff, not final release acceptance.

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
| VEF adopts kit 0.5.0 for AI | Consumer lock, released suite, integrity checks, and API consumer tests pass locally; hosted CI remains to be recorded. ODIP may retain accepted 0.4.0. |
| AI provider, evidence, and acceptance | OpenAI through the Quarkus LangChain4j extension is selected; `gpt-6.1-sol` is selected initially. The owner confirmed the full deployed energy flow, including live AI explanations, on October 19 after VEF `v0.5.1`. Formal AI-01–AI-11 evidence, semantic review, the new Gate 5 regression record, and final release acceptance remain open. |
| Generic publication, energy adapter, reader allow-list | Implemented and locally verified in ODIP; provision deployed reader access and verify VEF's authenticated retrieval, dataset denial, and bytes. |
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
