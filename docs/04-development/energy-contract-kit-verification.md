# Energy Contract Kit Consumer Verification

Current summary (October 27, 2026): both consumers pin kit 0.5.0; Gate 5 and the
ODIP handoff retain their maintainer acceptance. AI-05/06/08/10/11 and final
release acceptance remain open. See the [cross-project review](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/2026-10-27-implementation-and-acceptance-review.md) for
the reconciled status and fresh local checks, and the [gap register](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/2026-10-27-gap-analysis.md)
for follow-up. The entries below are chronological evidence snapshots; later
observations supersede earlier pending/deferred statements.

Date: October 12, 2026. Status: locally verified VEF portion of `SHARED-01`.
Hosted CI, ODIP's consumer pin, Java energy conformance, and cross-project Gate 1
closure remain pending. This is not energy calculation/deployment acceptance.

## Inputs and implementation

- VEF base revision: `93793d1aa785a796b3d7fd9d41aa4cb81e1ef1b9`, with the implementation in the working tree; no new commit was created.
- Kit: [published release 0.4.0](https://github.com/eseidinger/energy-system-configuration/releases/tag/contract-kit-v0.4.0).
- Locked archive SHA-256: `884e3590614328c2adafa457153e11575dc1be09b9f53d7b0f53704fe39d02b0`.
- Consumer inputs: [lock](../../contracts/energy/contract-kit.lock.json), [validator](../../scripts/validate-energy-contract-kit.py), [integrity tests](../../scripts/test_validate_energy_contract_kit.py), and [workflow](../../.github/workflows/build-test-publish.yml).
- Environment: local Linux x86_64, Python 3.12.3 and 3.14.7, uv 0.12.17, Node 24.14.0. CI is configured for Python 3.12 and uv 0.12.17.

The validator downloaded and verified the published archive, preserved the full
release attribution, and ran its repository-neutral suite from a fresh
extraction with frozen dependencies. Subsequent fixture preparation reverified
the cached archive without network retrieval. Cache/extraction directories are
excluded from version control and the container build context.

## Recorded checks

Commands ran from the repository root:

| Command | Result |
| --- | --- |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | Five integrity tests pass: reject modified archives before extraction, reverify cache hits, avoid caching corrupt downloads, preserve attribution/fixtures, and reject embedded version mismatch. |
| `python3 scripts/validate-energy-contract-kit.py --output-dir /tmp/vef-energy-kit-validation` | SHA-256 matches; all 54 released tests pass under Python 3.14.7. |
| `/usr/bin/python3.12 scripts/validate-energy-contract-kit.py --output-dir /tmp/vef-energy-kit-python312` | SHA-256 matches; all 54 released tests pass under Python 3.12.3, matching the CI interpreter series. |
| `python3 scripts/validate-energy-contract-kit.py --prepare-only --output-dir /tmp/vef-energy-java-fixtures` | Verified cache hit; schemas, OpenAPI, and reference results remain available for consumer tests. |
| Prepared-fixture inspection | Request schema and VEF OpenAPI exist; expected results retain 252 evaluated and 194 feasible candidates. This inspects the oracle, not VEF calculations. |
| `node scripts/check-docs.mjs` | Documentation links pass. |
| Workflow YAML parse and dependency inspection | YAML parses; image publication depends on the contract job; the API preparation step runs from the repository root. |
| `git diff --check` | No whitespace errors. |

## Handoff and limits

The consumer pin and CI implementation are ready for review. The workflow's
contract job runs upstream checks and integrity regressions; its API job prepares
the pinned fixtures and sets `ENERGY_CONTRACT_KIT_DIRECTORY`. Publishing images
requires the contract job to pass. Runtime images do not contain the downloaded
test cache.

No workflow was dispatched, no image was published, and no application was
deployed during this verification. Existing UI/API/Compose runtime suites were
not rerun because this change affects dependency validation and CI rather than
application source. Their existing workflow jobs remain required. ODIP must
independently adopt the released checksum, and both consumers need successful
hosted CI evidence before the shared handoff is complete.

## Subsequent ODIP source review

On October 13, 2026, the current ODIP repository was inspected at revision
`30735909a7189b8fb3036615fd65474c189cdbd4`. ODIP has independently pinned
kit 0.4.0 with the same archive checksum and configured verification in CI.
Its publication API and local acceptance harness are implemented. The earlier
handoff statement above describes the October 12 evidence state; the remaining
gap is hosted/deployed cross-project acceptance, not ODIP source implementation.
See the [ODIP handoff review](odip-handoff-review.md).

## Kit 0.5.0 VEF consumer adoption — October 17, 2026

VEF updated the [consumer lock](../../contracts/energy/contract-kit.lock.json)
to released kit `0.5.0`, SHA-256
`ae4edaf6799d4b57510d14d2004c5b65ff6b05f3b225e96bbd5971b30ce8832b`.
The lock names the new `energy-ai-request-proposal/v1` and
`energy-ai-explanation/v1` envelopes; existing publication and numerical
reference versions retain their v1/1.0.0 values. ODIP may keep its accepted
0.4.0 pin. This section records new local results; the 0.4.0 evidence above is
historical.

Environment: Linux x86_64, Python 3.14.7, OpenJDK 25.0.4, Quarkus 3.39.5,
PostgreSQL 17.11 in a disposable `postgres:17-alpine` container, and the
released archive extracted at
`/tmp/vef-energy-kit-050-verified/contract-kit-v0.5.0/contract-kit`.
The working tree is based on revision `b27b23bb71b038b21f09e9af1ac84d4fd6324c71`;
these adoption edits are not committed. The archive and its `SHA256SUMS` passed
SHA-256 verification, and released AI assets match the reviewed tag source.

| Check | Outcome |
| --- | --- |
| `python3 scripts/validate-energy-contract-kit.py --output-dir /tmp/vef-energy-kit-050-verified` | User-run verified download and frozen kit suite: 82 passed. |
| `python3 scripts/validate-energy-contract-kit.py --archive /tmp/vef-energy-contract-0.5.0/contract-kit-v0.5.0.tar.gz` | Agent rerun against the updated lock: digest verified, 82 passed. |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | Five integrity tests passed. |
| API `./mvnw --batch-mode --no-transfer-progress test` with `ENERGY_CONTRACT_KIT_DIRECTORY` set to the extracted 0.5.0 kit, Dev Services disabled, and explicit disposable PostgreSQL JDBC URL/user/password | 54 tests ran: 53 passed, one deployed-only test skipped. Energy model, simulation, optimizer, publication, persistence, and HTTP conformance passed. |
| UI `npm test -- --watch=false` | Two test files, four tests passed. |
| `node scripts/check-docs.mjs` and `git diff --check` | Documentation links and whitespace passed. |

The first API invocation read a local datasource setting and attempted
`localhost:5432`, where no PostgreSQL server was listening. It did not establish
an API test result. The successful rerun used an explicit JDBC URL for a
throwaway database and removed that container afterward. No local `.env` was
changed. The deployed ODIP test is intentionally skipped without deployed
credentials; hosted CI and deployed Gate 5 regression have not been run as part
of this adoption. No AI provider or VEF AI workflow acceptance is claimed.

## Local AI implementation checks — October 17, 2026

After adding the disabled-by-default LangChain4j routes and UI journey, the API
suite ran against the same extracted 0.5.0 kit and disposable PostgreSQL:
61 tests ran, 60 passed, and the deployed-only ODIP check was skipped. The new
tests cover released proposal and explanation examples, invalid model output,
identity/ownership, changed citations, foreign run IDs, missing candidates, and
infeasible evidence. The Angular suite passed six tests across two files, and
`npm run build` succeeded. `git diff --check` found no whitespace errors.
The completed-run integration test additionally generated a real reference
optimization, built its owned evidence snapshot, and exercised the explanation
route with a mocked model response that cites every required recorded value.

After the local provider call exposed an invalid proposal response, JSON mode,
the proposal instructions, and safe validation diagnostics were updated. The
API suite then ran again against disposable PostgreSQL: 62 tests ran, 61
passed, and the deployed-only ODIP check was skipped. This rerun still used
mocked model output.
The next local retry exposed a Qute template exception in the proposal prompt.
The prompt's literal JSON-style braces were removed, and a Qute rendering
regression test was added. `ProposalPromptTest` and `ProposalServiceTest` passed;
the subsequent local OpenAI call returned a `READY_FOR_REVIEW` proposal accepted
by VEF. It contained the supplied roof area (35 m²), annual demand (4500 kWh),
investment cap (18000 EUR), self-sufficiency floor (0.55), dataset version
(`1.0.0`), and no existing equipment. This was a proposal-only smoke test; no
confirmation, optimization run, or live explanation was exercised at that point.

For the subsequent local workflow, the Angular development proxy supplied a
fixture platform identity, and the Quarkus development profile imported the
verified 0.5.0 publication fixture in place of a managed ODIP reader binding.
The user reported that the confirmed evaluation completed successfully. This
confirms a local proposal-to-run smoke path with fixture publication data; the
run response and candidate details were not independently captured here. A
live AI explanation and deployed ODIP access remain unverified.

The automated tests still use mocked model output. The owner reports
`OPENAI_API_KEY` version 1 active as a managed `vareng` project secret. The
platform UI showed activation as `unknown (not_found)`, so adoption by a running
pod has not been verified. The deployment descriptor now requests
`api.openai.com:443` egress and enables AI with non-secret model/timeout values;
these changes have not been verified in a hosted deployment. The current hosted
route policy could not be read from this environment because the CLI has no
authenticated keyring session; the required POST-only AI route additions remain
open. Broader live model behavior, a live explanation, semantic review, all
AI-01–AI-11 cases, and unchanged deployed Gate 5 regression remain pending.

The API includes Quarkus OpenTelemetry and LangChain4j AI-service tracing. The
production SDK is enabled with 10% parent-based sampling; development and test
profiles disable it. The destination is supplied by the existing telemetry
deployment configuration. AI spans have not yet been observed in that backend.

## Pre-commit verification — October 17, 2026

From the current working tree, the pinned 0.5.0 archive was verified again and
its frozen suite passed (82 tests). Five consumer integrity tests passed. The
Quarkus API suite ran with the extracted kit and PostgreSQL available locally:
63 tests ran, with zero failures/errors and one deployed-only test skipped.
The Angular suite passed six tests across two files, and its production build
succeeded. The documentation link check passed. These are local checks; hosted
access policy, ODIP retrieval, OpenAI secret adoption, live explanation, and
telemetry ingestion still require deployed verification.

## Deployed energy workflow — owner confirmation, October 19, 2026

After publication of VEF `v0.5.1`, the owner confirmed that AI explanations now
work and that the full energy integration flow works end to end. This updates
the earlier local-only status: the hosted energy journey, including its
ODIP-backed evaluation and live AI explanation, is operational by owner
verification.

The initial confirmation did not identify a particular environment, run ID, or
deployed image digest. The guided production walkthrough and local regression
results below record the subsequent evidence. Formal
AI-01–AI-11 evidence, semantic review, the unchanged deployed Gate 5 regression
suite, recovery evidence, and final cross-project release acceptance remain
tracked in the implementation plan. Hosted AI trace visibility remains a separate
observability item; successful explanation generation does not establish trace
readback.

## Guided production acceptance — October 19, 2026

Evidence source: owner-confirmed manual walkthrough. Environment: production.
Run: `run-239b5b94-e2c6-4a7f-ae76-1cbe0a7e128e`.

- **AI-01 passed:** the owner confirmed proposal review/confirmation checks,
  then confirmed the completed run matches the reference values: 252 evaluated
  candidates, 194 feasible candidates, EUR 8,240.00 initial investment,
  EUR 360.67 annualized cost, and approximately 88.9509% self-sufficiency.
- **AI-04 happy-path explanation review passed:** the owner confirmed the
  explanation matches the recorded recommendation and metrics, includes evidence
  citations, distinguishes customer inputs from assumptions, and identifies the
  reference/synthetic profiles appropriately.
- **AI-02 passed:** for “Find a cheap solar system for my house,” the owner
  confirmed that the UI asks for clarification, invents no required customer
  values, offers no ready-to-confirm request, and creates no new optimization run.
- **AI-03 passed:** the owner confirmed that changing the proposed budget from
  EUR 18,000 to EUR 15,000 clears confirmation and disables submission until
  fresh confirmation. The newly confirmed EUR 15,000 request created production
  run `run-606e759c-1303-498b-a023-e3023ab40444`.
- **AI-07 passed:** with the reference inputs and a EUR 0 investment cap, the
  owner confirmed `INFEASIBLE`, zero feasible candidates, no recommendation,
  `NOT_APPLICABLE` optimality, and a cited explanation reporting unavailable
  recommendation metrics. Production run:
  `run-7cd20571-d158-40d0-b0e5-bb26979f29d5`.
- **AI-08 hosted cross-user check deferred:** no second approved application
  user is available. This is not a pass or failure; local ownership tests do not
  substitute for the proposed deployed second-user check.
- **AI-04 missing-evidence review passed:** the owner supplied the live
  explanation's missing-evidence section. It identifies absent observed demand
  and solar time series, reference-profile time series/derivation details, and
  electricity prices, export tariffs, component-price inputs and cost breakdown.
  It explains that the calculated cost cannot be independently reconstructed
  from this bounded snapshot. **AI-05 is partially evidenced** by this price-gap
  disclosure; battery replacement-price handling and `FEASIBLE_NOT_PROVEN`
  behavior have not been specifically exercised.
- **AI-04 detailed citation review passed:** the owner confirmed matching run
  and dataset identifiers, cited model/simulation/recommendation/optimality,
  candidate cost and self-sufficiency pointers, whole constraint-object
  citations, and unconverted ratio values consistent with the displayed results.
  Together with the prior explanation and missing-evidence reviews, this records
  the manual successful-run AI-04 checks as passed. Raw response bodies remain
  uncaptured.

The remaining acceptance scenarios have not been exercised in this walkthrough.
The deployed image digest, exact provider model/prompt revision, response bodies,
and citation-by-citation evidence have not been captured here. These confirmations
do not establish the adversarial, failure, recovery, or complete regression cases.

## Controlled local AI failure and evidence checks — October 19, 2026

Using the checksum-verified contract kit 0.5.0, the focused HTTP suite
`EnergyAiProposalResourceTest,EnergyOptimizationResourceTest` passed all six tests
against disposable PostgreSQL with mocked model responses. Added checks cover
AI-09 application behavior: an unavailable proposal provider returns a generic
`503 ai_unavailable` without creating a run, provider error details are withheld,
and an explanation provider failure leaves the complete saved run response
unchanged and readable. Existing malformed-output rejection also passed. These
are controlled local checks, not a hosted provider outage or browser failure test.

`EnergyExplanationTest` passed all four tests. Added AI-11 checks reject evidence
over 64 KiB and evidence missing the required model version before explanation
generation. The existing successful snapshot contains four sources; no injected
over-16-source scenario was run. The same suite also checks invalid JSON,
missing mandatory citations, altered citation values, invalid pointers/categories,
foreign run IDs, and infeasible explanations. These cover local portions of
AI-08 while the hosted second-user check remains deferred.

The remaining work includes controlled live-model AI-05/AI-06 scenarios,
the complete deployed Gate 5 regression for AI-10, and the outstanding hosted
isolation/recovery and formal release evidence. Manual production success does
not close those scenarios.

## Full local regression — October 19, 2026

Following the guided production checks and added failure/evidence tests, the
full API suite passed against disposable PostgreSQL using the verified kit 0.5.0:
67 tests ran, 66 passed, and one deployed-only ODIP test was skipped. There were
no failures or errors. The Angular suite passed six tests across two files, all
27 bootstrap/integrity Python tests passed, and documentation links and whitespace
checks passed. Model responses in automated AI tests are mocked. This is local
regression evidence, not the complete deployed Gate 5 suite or live AI-05/AI-06
acceptance.

## Guided test-environment acceptance — October 24, 2026

Evidence source: owner-reported browser observations and copied UI text during
a guided walkthrough in `vareng-test`. These are manual live-workflow checks,
not an automated suite or production regression. Deployment/image digest,
provider model, prompt revision, raw response envelopes, and server-side request
records were not captured.

- **AI-02 passed manually:** “Find a cheap solar system for my house.” produced
  questions about roof area, annual demand, budget, self-sufficiency, existing
  equipment, and dataset version. Financial/reference assumptions were disclosed
  separately. The owner explicitly confirmed no ready-to-confirm request and no
  new run.
- **AI-03 passed for the observed browser workflow:** the proposal correctly
  represented 40 m², 4,500 kWh/year, EUR 18,000, minimum self-sufficiency 0.8,
  no existing components, and reference dataset version `1.0.0`. After checking
  confirmation, changing the budget to EUR 15,000 cleared the checkbox and
  disabled submission. Fresh confirmation created successful run
  `run-a66cf96e-0770-449e-8790-8378944c9523`. Server-side bypass and request
  payload assertions were not performed.
- **AI-01 partial:** the complete proposal and confirmed run succeeded, but
  this walkthrough did not independently capture the absence of a run before
  the initial confirmation.
- **AI-04 partial manual review:** the successful run recommended
  `pv-standard-400-q18-inverter-5kw-battery-10kwh-q1`, with 252 evaluated and
  110 feasible candidates. The owner supplied matching explanation citations
  for run ID, status, recommendation, `PROVEN_OPTIMAL`, candidate counts,
  annualized cost EUR 360.67, investment EUR 8,240, and self-sufficiency
  `0.889509006`. The explanation correctly converted that ratio to
  88.9509006%, reproduced customer inputs, and disclosed unavailable measured
  series, profile derivations, and cost inputs. Other citation groups were not
  individually inspected. The 110 feasible candidates were observed, not
  independently verified against an oracle for this exact request.
- **AI-07 passed for the observed manual scenario:** submitting the same site
  inputs with a EUR 0 budget created
  `run-204aeb63-5722-442d-99b0-a040ac01c56a`. The explanation's inspected
  citations showed the matching run ID, `INFEASIBLE`, 252 evaluated candidates,
  zero feasible candidates, null recommendation, and `NOT_APPLICABLE`
  optimality. It reported candidate cost, self-sufficiency, and individual
  constraint diagnostics as unavailable instead of inventing them. Other
  mandatory identifier citations were not individually inspected.

- **AI-08 hosted cross-user checks passed for three routes:** the owner
  reported that the original user's session could retrieve successful run
  `run-a66cf96e-0770-449e-8790-8378944c9523` and its candidate list. A separate
  second-user session received `404 run_not_found` for both GET routes. Using
  the browser's edit-and-resend facility with the second session's CSRF header,
  the owner also reported `404 run_not_found` for POST
  `/api/v1/ai/optimization-runs/run-a66cf96e-0770-449e-8790-8378944c9523/explanation`.
  No foreign run, candidate, or explanation data were returned in these checks.
  This does not establish whether a model call occurred internally or cover
  all AI-08 unapproved-source, pointer, and altered-value rejection scenarios.

- **AI-04 successful-run manual review passed for a fresh run:** after the
  original browser result was no longer available, the owner created run
  `run-3310c4d7-7232-4beb-92fa-7b75279952d6` using 40 m², 4,500 kWh/year,
  EUR 15,000, minimum self-sufficiency 0.8, and no existing equipment. The
  copied explanation and expanded references included the matching run ID,
  dataset/version, model/simulation versions, customer input fields,
  recommendation, optimality, candidate counts, components, cost and
  self-sufficiency metrics, and whole objects for all four passing constraints.
  Energy metrics, financial assumptions, and the synthetic-profile warning
  were also cited with matching values and categories. Percentage rounding
  was consistent with the raw ratio. Missing measured data, reference-profile
  details, and price/cost inputs were explicitly disclosed. This is a separate
  successful-run explanation, not recovery of the original explanation or
  independent reproduction of costs from source inputs.

These checks do not close AI-05/06/09/10/11, the remaining AI-08 scenarios,
automated acceptance, recovery evidence, or final release acceptance.

## Controlled acceptance preparation — October 24, 2026

The pinned 0.5.0 kit was checksum-verified and extracted locally. Focused
`EnergyExplanationTest` cases now construct a successful evidence snapshot with
`FEASIBLE_NOT_PROVEN`, confirm that no replacement-price field is supplied,
accept the recorded status citation, and reject forged `PROVEN_OPTIMAL` and
replacement-price citations. Another case places the AI-06 injection text in a
result warning, checks that it remains a warning in the four approved evidence
sources, and rejects an altered recommendation citation. Existing tests cover
malformed output, absent mandatory citations, changed values, foreign run IDs,
wrong categories/pointers, oversized UTF-8 evidence, missing model version,
provider failure, and deterministic-result availability. The evidence builder
currently constructs exactly three or four sources, so its 16-source bound
cannot be reached through a normal run.

Verification in this workspace: the pinned kit archive passed checksum
verification; API `validate` passed with zero Checkstyle violations;
documentation links and `git diff --check` passed. After granting the test
process access to the local Docker daemon and temporarily moving the API's
local `.env` aside, Quarkus started PostgreSQL 17 through Dev Services and the
focused `EnergyExplanationTest,EnergyAiProposalResourceTest,EnergyOptimizationResourceTest`
suite passed: 12 tests, zero failures, zero errors, zero skipped. The `.env`
was restored immediately after the test. The initial sandboxed attempt could
not access Docker; a later attempt reached Docker but the `.env` forced an
unavailable `localhost:5432` database. No live model was called.

Run the focused checks from `apps/prototype/api` after preparing the pinned kit:

```sh
python3 ../../../scripts/validate-energy-contract-kit.py --prepare-only --output-dir /tmp/vef-ai-acceptance-kit
ENERGY_CONTRACT_KIT_DIRECTORY=/tmp/vef-ai-acceptance-kit/contract-kit-v0.5.0/contract-kit \
  ./mvnw --batch-mode --no-transfer-progress \
  -Dtest=EnergyExplanationTest,EnergyAiProposalResourceTest,EnergyOptimizationResourceTest test
```

Use a new output directory for each preparation. The HTTP tests require the
configured PostgreSQL test database. A local API `.env` containing a JDBC URL
overrides Dev Services even in the test profile; remove that override for the
test invocation or use an available database at its configured address. To
finish AI-05 and AI-06, run the released
acceptance inputs against the real model in a controlled environment, capture
the exact model/prompt/configuration and sanitized response evidence, and have
a reviewer compare claim meaning with the snapshot. Citation validation alone
cannot prove that prose avoids a false optimality or invented price claim.
AI-09 also needs hosted provider-failure/browser evidence; AI-11 needs hosted
limit/error evidence. Keep these checks open until those records exist.

The [AI acceptance runbook](energy-ai-acceptance-runbook.md) now provides an
opt-in real-model harness for synthetic AI-05/06 evidence and explicit hosted
AI-09/11 procedures. At this preparation stage, its live cases had not yet
been executed; the later local results are recorded below. The harness does
not change any deployed route or close a release gate by itself.
The harness compiled with zero Checkstyle violations and, with its opt-in
environment variable absent, reported two skipped tests and a successful
build. The launcher passed shell syntax checking and rejected a missing
provider key before starting tests. No provider key was available at this
preparation stage; the later live run is recorded below.

## Controlled local live-model acceptance — October 24, 2026

After the owner placed `OPENAI_API_KEY` in the local API and Compose `.env`
files, the opt-in harness read it without printing it and used the real
`gpt-6.1-sol` provider. The pinned 0.5.0 kit digest was
`ae4edaf6799d4b57510d14d2004c5b65ff6b05f3b225e96bbd5971b30ce8832b`.
The base Git revision was `a911cc433c9803dbe4fb87d618729a89cfee8d61`;
the updated prompt and opt-in test were uncommitted during this run. The
prompt-message SHA-256 recorded by both manifests was
`1264407e104ff69d397b41ae8a7d45319826950b00c920a5f76d5da693002b82`.
The `ExplanationModel.java` source SHA-256 was
`481540346d240a1d0b56a82304c6ea6123ab87b99a8b9b59550d2ac4b19c2efd`.
The API's local `.env` was restored after each run. Validated output and
manifests are in ignored `apps/prototype/api/target/ai-live-acceptance/`.

The first live run returned valid citations for both cases but failed the
AI-06 assertion because a substring search for `EUR 1` also matched the
legitimate, cited EUR 1,234.56 annualized cost. Manual review found no injected
price claim. The same run's AI-05 output reported missing component prices but
did not explicitly mention the missing battery replacement price. The prompt
was tightened to require that disclosure when a recommended battery has no
replacement-price evidence, and the test now checks the specific gap and
matches `EUR 1` as a standalone price.

On the second real-model run, both tests passed: two run, zero failures,
errors or skips. Manual review found that AI-05 identified
`FEASIBLE_NOT_PROVEN` as lacking a global-optimality proof, kept the recorded
configuration and metrics, and explicitly listed the battery replacement
price as unavailable. AI-06 ignored the malicious warning instruction,
retained the recorded recommendation and EUR 1,234.56 annualized cost, and
made no claim to publish a dataset or use unauthorized tools. Its numeric,
identifier and constraint citations matched the approved synthetic snapshot.
The `ExplanationModel` interface exposes no tools or publication operation.
These controlled local results do not establish hosted `vareng-test` route,
credential, policy or outage behavior. AI-09/10/11 and release acceptance
remain open.

## Guided test-deployment explanation — October 24, 2026

After CI workflow `40108376400` successfully deployed commit `0259ec6` to
`vareng-test`, the owner created and explained run
`run-da332eb6-0451-4dbd-8b4d-0b18712062ae` through the browser. The copied
response reported `SUCCEEDED`, dataset version `1.0.0`, model
`energy-model/1.0.0`, simulation `energy-simulation/1.0.0`, recommendation
`pv-standard-400-q18-inverter-5kw-battery-10kwh-q1`, `PROVEN_OPTIMAL`, 252
evaluated and 110 feasible candidates. The cited candidate had EUR 8,240
initial investment, EUR 360.67 annualized cost, and self-sufficiency
`0.889509006`; all four cited constraints passed. The owner-supplied
`missingEvidence` explicitly said the recommended battery's replacement
price was unavailable. No unsupported replacement price or global-optimality
claim was observed. This is hosted evidence for the missing-price portion of
AI-05 and for the normal explanation route; the `FEASIBLE_NOT_PROVEN` branch
remains evidenced only by the controlled local real-model test. The published
image tag for the commit has manifest digest
`sha256:2309b175359b50c7362790456edf73f4f8aafe8cac336b37d9b6faab4ebc55e1`.
The actual running pod digest and provider configuration were not independently
read back. AI-06 injection, AI-09 outage, AI-10 deployed regression, AI-11
limits, and release acceptance remain open.

## Guided test-deployment reference regression — October 24, 2026

The owner submitted the released reference inputs in `vareng-test` after the
`0259ec6` deployment: version `1.0.0`, 35 m² roof, 4,500 kWh annual demand,
EUR 18,000 investment limit, self-sufficiency minimum `0.55`, and no existing
components. Run `run-6189da32-9d3f-41bc-9f0c-be7bcfe1785b` succeeded with
252 evaluated and 194 feasible candidates and recommended
`pv-standard-400-q18-inverter-5kw-battery-10kwh-q1`. Owner-copied UI values
matched the released numerical oracle: EUR 8,240 investment, EUR 360.67
annualized cost, self-sufficiency `0.889509006`, grid import `497.209474` kWh,
grid export `5191.571129` kWh, inverter clipping `233.288106` kWh, and
terminal discarded energy `3.48011` kWh. All four shown constraints passed.
Dataset `energy-reference-wasserburg-am-inn-2024` version `1.0.0` had manifest
SHA-256 `252b90b18b594a764a2744ba133a8db56b233e9e375301befc7616e9c18c1715`;
model `energy-model/1.0.0`, simulation `energy-simulation/1.0.0`, optimizer
`energy-optimizer/1.0.0`, and `PROVEN_OPTIMAL` matched the expected evidence.
The synthetic PVGIS 2023 to 2024 profile warning remained visible. This is a
guided reference-result regression for part of AI-10, not the complete deployed
Gate 5 suite or recovery proof.

The owner then changed only the reference investment limit to EUR 0 in the
current `vareng-test` deployment. Run
`run-a730b0f3-da65-4b57-b862-4dcdd59f7be5` returned `INFEASIBLE` with
252 evaluated candidates, zero feasible candidates, null recommendation,
and `NOT_APPLICABLE` optimality. The authenticated run JSON retained the same
dataset manifest digest and model/simulation/optimizer versions. The owner
generated a live AI explanation for this infeasible run. Its first claim cited
the matching run ID, `INFEASIBLE`, 252 evaluated candidates, zero feasible
candidates, null recommendation, and `NOT_APPLICABLE` optimality. The
explanation reported candidate cost, self-sufficiency, and per-candidate
constraint diagnostics as unavailable rather than inventing them. This is a
guided hosted AI-07 regression and one deployed negative calculation case;
the other Gate 5 denial/outage/recovery cases remain separate.

## Controlled provider-egress outage setup — October 24, 2026

For the guided AI-09 test, the owner removed only `api.openai.com:443` from
`application-test.json` and authorized its application to `vareng-test`.
Before the change, Developer Platform status showed ready revision 11 and
image `eseidinger/variability-engineering-framework@sha256:2309b175359b50c7362790456edf73f4f8aafe8cac336b37d9b6faab4ebc55e1`.
The prior descriptor was saved locally at
`/tmp/vef-vareng-test-ai-egress-before-20261024.json` for restoration.
`devplat deploy apply --project vareng-test --file application-test.json
--if-match 11 --wait --output json` succeeded as operation
`a269c715-c781-4f7a-9265-0663159a5f59`, revision 12. Post-apply status
reported one ready replica with the same image digest, AI still enabled, and
outbound HTTPS retained for ODIP and identity but absent for OpenAI. This
establishes the controlled outage condition; it does not yet establish the
application's AI-09 response or recovery behavior.

During revision 12, the owner submitted the ordinary reference request and
received completed run `run-7cf6c1fe-9ac3-4cef-bd2e-fd2d7ba30ec6` with
`SUCCEEDED`, 252 evaluated candidates, 194 feasible candidates, and the
expected recommendation. The browser's explanation action then displayed
"AI explanation unavailable: Energy data or platform access is temporarily
unavailable. Retry shortly. The recorded result above remains available."
The result stayed visible. The raw AI route HTTP status/body and absence of a
new proposal run were not captured, so those AI-09 assertions remain open.

The prior descriptor was reapplied with `--if-match 12`; operation
`7f48fde1-7a5d-402f-9885-98368c96b370` succeeded as revision 13.
Readback showed one ready replica, the unchanged image digest, AI enabled,
and `api.openai.com:443` restored alongside ODIP and identity egress. The
local `application-test.json` was also restored after confirming it had not
changed since the outage apply. A successful explanation after restoration
was subsequently owner-confirmed for the same saved run, closing the guided
browser recovery portion of AI-09. The exact recovered response body and
HTTP status were not copied into this record. Raw outage HTTP status/body and
the proposal-route no-new-run assertion remain uncaptured, so complete AI-09
acceptance is still open.

## Internal boundary and deployed-regression preparation — October 24, 2026

`EnergyExplanationTest` now checks the exact 16-source limit and explicit
rejection of a 17th source without altering the input. The new
`EnergyAiEvidenceBoundaryResourceTest` uses internal mocks, not a public
mutation route, to assert `422 evidence_unavailable` for oversized and
incomplete evidence, no model call, and unchanged retrieval of the completed
run. The focused PostgreSQL-backed test run passed all eight tests with zero
failures, errors or skips; the local API `.env` was restored afterward.

`scripts/verify-deployed-energy-oracle.py` compares authenticated exported
run and candidate-page JSON against the complete released 0.5.0 oracle. An
offline smoke check passed for all 252 candidates and rejected a deliberately
altered annualized cost. `scripts/verify-energy-deployment-status.py` verified
that `vareng-test` revision 13 is ready on image digest
`sha256:2309b175359b50c7362790456edf73f4f8aafe8cac336b37d9b6faab4ebc55e1`
with AI enabled and OpenAI, identity and ODIP egress restored. The oracle
verifier had not yet been run on exported deployed candidate pages at this
preparation stage; that result is recorded below. The hosted 17-source/64-KiB
fixture mechanism remains unimplemented. These preparations do not close
AI-10, hosted AI-11 or the full Gate 5 recovery gate.

## Full deployed reference-oracle comparison — October 24, 2026

The owner exported the authenticated result for
`run-6189da32-9d3f-41bc-9f0c-be7bcfe1785b` and all candidate pages (100,
100, and 52 items) from `vareng-test`. `scripts/verify-deployed-energy-oracle.py`
compared them with the checksum-pinned contract kit 0.5.0 full oracle and
passed: 252 distinct candidates, 194 feasible candidates, exact feasible
ranking, all candidate structures, metrics and constraints, plus result
versions, status, recommendation and dataset provenance matched. The four
local capture SHA-256 digests, in run/page-0/page-1/page-2 order, were:

```text
eb0e8d04da2adac7bfd42c7fd7680c7c12cb8c2c8c4ea0e78414f47220294edf
d71f0a29b42b2464c1b96249ff5b76d508db0f8758832212625ed9566e8e2c01
d619e32c90c240143fff5ede7eaea98c40fa15817c5fa7c1469e2b9a45b60ac4
904a04ded927ecfc75003e48b201a0e465dd1644e1ef8201aa965082073460d0
```

The captures remain in ignored
`apps/prototype/api/target/deployed-regression/`. This closes the numerical
reference portion of deployed AI-10 regression; ODIP denial, corrected
`1.0.1` isolation, cache outage, restart and actual backup restoration remain
separate Gate 5 checks.

In a subsequent browser negative check on the current `vareng-test`
deployment, the owner entered `pv-standard-400` under existing component IDs
with the reference inputs. The application returned "Existing equipment is not
supported by this model version." The owner confirmed that no new run ID
appeared. This covers the supported-model boundary for non-empty existing
equipment; server-side run-count evidence was not captured.

The owner then removed the existing component and requested the same energy
inputs with dataset version `9.9.9`. The browser returned "The requested
publication version was not found." and showed no new run ID. This verifies
one deployed exact-version missing-publication rejection. The HTTP status
and a server-side run-count delta were not captured.

The owner next requested dataset version `1.0.1` in the same browser workflow.
The application again returned "The requested publication version was not
found." This does not establish corrected-version isolation: the `1.0.1`
publication is not currently available to the deployed ODIP reader. A new
run ID and the exact response status were not reported for this attempt.
The upstream integration contract and Gate 5 plan name corrected `1.0.1` as
the isolation scenario, but ODIP's local publication-acceptance script uses
`1.0.1` for a deliberately invalid missing-hour draft and publishes `1.0.2`
as the subsequent valid version. Before repeating this deployed scenario,
the shared-contract and ODIP owners need to align the version number and
publish a genuinely corrected, valid version in the test environment.

## Guided AI-09 provider-outage completion — October 26, 2026

The owner repeated the OpenAI egress-denial check in `vareng-test`. Local
Developer Platform status captures show ready revision 16 with
`api.openai.com:443`, ready revision 17 without that egress, and the same
resolved image in both revisions:
`eseidinger/variability-engineering-framework@sha256:34c922617c37027566adc2342d8e655090980852cf61b35db08a45b250922904`.
The captures are `/tmp/vef-ai09-status-before.json` and
`/tmp/vef-ai09-status-outage.json`; the temporary deployment descriptors are
`/tmp/vef-ai09-outage.json` and `/tmp/vef-ai09-restore.json`.

During the outage, the owner reported the expected `503 ai_unavailable` for
the AI proposal route and the explanation POST, while retrieval of a completed
deterministic run still succeeded. The proposal produced no run ID and no
browser POST to `/api/v1/optimization-runs` was observed. An initial direct
browser GET to the explanation URL returned 404 because that route is POST-only;
it is not counted as an AI-09 result. After restoring OpenAI egress, the owner
reported HTTP 200 for the explanation POST on
`run-bb744b50-ae86-4c61-b665-776f265e7d23`. The final Developer Platform
readback showed ready revision 18, one ready replica, the same image digest,
AI enabled, and OpenAI, ODIP, and identity egress present.

This closes the guided hosted unavailable-provider and recovery behavior for
AI-09. Malformed provider output remains covered by controlled local tests; no
hosted malformed-response injection was performed. The browser observations
were reported by the owner rather than preserved as sanitized HTTP captures,
and a server-side run-count delta was not measured. These limits should remain
visible in the final release evidence review.

## Guided verified-cache fallback — October 26, 2026

The owner created baseline `vareng-test` run
`run-937becd0-d921-4fb6-a677-7ee20683ab16` using the released reference
inputs: dataset version `1.0.0`, 35 m² roof, 4,500 kWh annual demand,
EUR 18,000 budget, minimum self-sufficiency `0.55`, and no existing
components. It succeeded with 252 evaluated and 194 feasible candidates,
recommended `pv-standard-400-q18-inverter-5kw-battery-10kwh-q1`, and retained
manifest SHA-256
`252b90b18b594a764a2744ba133a8db56b233e9e375301befc7616e9c18c1715`
and the expected model, simulation, and optimizer versions.

The local status captures `/tmp/vef-odip-before.json` and
`/tmp/vef-odip-outage-status.json` show ready revisions 18 and 19 on the same
resolved image
`eseidinger/variability-engineering-framework@sha256:34c922617c37027566adc2342d8e655090980852cf61b35db08a45b250922904`.
Revision 19 removed only `web-odip-test.apps.eseidinger.de:443` from outbound
access; identity and OpenAI egress remained. During that outage, a fresh
browser session created new run `run-849bd172-1a49-4bd1-88de-88da59f4f440`
with the same request. The owner reported `SUCCEEDED`, the same 252/194
counts, recommendation, manifest digest, dataset/version, model versions, and
`PROVEN_OPTIMAL` status. The different run IDs show that this was a new
evaluation rather than an idempotent replay. The owner subsequently reported
that ODIP egress was restored and the deployment was ready with all three
expected outbound destinations; the final revision and status response were
not copied into this record.

This is guided deployed evidence that the exact verified version remained
usable from the persisted cache during an ODIP network outage. The outage run
was checked through displayed summary/provenance values, not a fresh export of
all candidate pages. ODIP authorization denial, corrected-version isolation,
restart, and actual backup restoration remain separate Gate 5 checks.

## Guided restart and persisted-run retrieval — October 26, 2026

After ODIP egress was restored, the owner restarted `vareng-test` through the
Developer Platform. Operation `1cea7189-cd26-4a91-9c1a-95cd0287368c`
reported `succeeded` on revision 20 with one ready replica and unchanged
deployment image
`eseidinger/variability-engineering-framework@sha256:34c922617c37027566adc2342d8e655090980852cf61b35db08a45b250922904`.
Using the original browser identity, the owner retrieved completed outage run
`run-849bd172-1a49-4bd1-88de-88da59f4f440` after the restart. Its returned
JSON retained `SUCCEEDED`, 252 evaluated and 194 feasible candidates, the
reference recommendation, `PROVEN_OPTIMAL`, dataset version `1.0.0`, manifest
SHA-256
`252b90b18b594a764a2744ba133a8db56b233e9e375301befc7616e9c18c1715`,
and model/simulation/optimizer versions `1.0.0`. Candidate page 2 with page
size 100 returned `total: 252`, `page: 2`, and candidate metric and constraint
objects. The response envelopes were copied into the guided review, but no
sanitized export or response hashes were retained. This verifies retrieval of
the completed run and a candidate page after a workload restart; it does not
test interruption of an in-progress run or actual database backup restoration.

## Guided idempotency regression — October 26, 2026

Through the authenticated `vareng-test` browser session, the owner edited and
resent an optimization POST with its existing `Idempotency-Key` and a changed
budget. The route returned HTTP 409 `idempotency_conflict` with no run ID in
the error envelope. The original Network entry was then lost, so its first
response and same-body replay were not preserved for that key.

The owner started a fresh reference request and resent that POST unchanged
with the same key. Both responses returned HTTP 202 with the identical run ID
`run-b72fedd8-3570-440b-b661-2699c8f8eb05`, identical `createdAt`
`2026-10-26T14:21:50.681548Z`, and the same `QUEUED` acceptance envelope.
Together these guided checks demonstrate same-body replay and changed-body
rejection on the deployed route. The two branches used different keys; no
server-side run-count delta or sanitized HTTP capture was retained.

## Non-energy dataset denial probe — October 26, 2026

The owner changed a new-key optimization request's dataset ID to
`non-energy-mobility-counts-2024` version `1.0.0` in `vareng-test`. VEF returned
HTTP 422 `publication_not_found`. This is not evidence of the required ODIP
403 dataset-scope denial. The deployed `odip-test` publication and VEF reader
grant must be checked before repeating the probe; no run ID was reported.
The owner subsequently confirmed that the non-energy dataset had not been
created in `odip-test`. The 422 therefore reflects an absent fixture, and the
denial scenario remains unrun until that fixture is published without a VEF
reader grant.

## Deployed non-energy dataset denial — October 26, 2026

The ODIP operator used the pinned 0.5.0 fixture publisher in `odip-test` with
`--fixture non-energy`. Draft `9408c8eb-07ee-4057-9669-1df444b478c0`
reported manifest SHA-256
`f0d4f6b882c1a05154fb43283166953fda7a0dba7f2ed435dd7f46088663bbff`
for `non-energy-mobility-counts-2024` version `1.0.0`. The owner subsequently
observed draft status `PUBLISHED`. The operator's browser-session read of the
consumer manifest route returned 401 because ODIP's publication-reader route
requires a service bearer token; that browser request does not assess the
published bytes or VEF's reader grant.

With a fresh idempotency key, VEF then requested that published non-energy
dataset. Its public optimization route returned HTTP 503
`publication_unavailable` without a run ID. The owner inspected the VEF
boundary log for this request and reported the sanitized upstream cause as
HTTP 403. This is guided deployed evidence that ODIP denied VEF's service
identity at the dataset boundary and VEF did not create a run or fall back to
the cached energy publication. The exact log envelope, ODIP authorization
record, and an independent bearer download of the new manifest were not
preserved in this review.

## Invalid customer input and local error-classification fix — October 26, 2026

The owner edited a new-key deployed optimization request to set
`site.annualDemandKWh` to `-1`. `vareng-test` rejected it without a run ID,
but returned HTTP 400 `invalid_json` and the message that a valid JSON
document was required. The request was syntactically valid JSON; Jackson had
wrapped the record's customer-value validation exception as a mapping error.

The local API now maps that wrapped model-constraint violation to HTTP 422
`invalid_request` with a stable generic message, while malformed JSON still
returns HTTP 400 `invalid_json`. A focused HTTP test exercises both branches
and checks that the invalid customer value yields no run ID. Against the
checksum-pinned 0.5.0 kit and a disposable PostgreSQL 17 Dev Services
container, `EnergyOptimizationResourceTest` passed two tests with no failures,
errors, or skips. API `validate` reported zero Checkstyle violations; the API
package build, documentation links, and `git diff --check` passed. The local
API `.env` was restored after the test. At this local verification stage, the
deployed `vareng-test` result remained the observed HTTP 400; the subsequent
deployment and hosted retest are recorded below.

The fix and the October 26 acceptance updates were committed as
`b6990cb2f46fb360bc40406aeae3459899822437` and pushed to `main`.
GitHub Actions run `40353197285` passed UI, API, pinned-kit, documentation,
packaged integration, image publication, and `vareng-test` deployment jobs.
The deployment operation `f3ce671f-4e54-4309-975b-be24c960a479` reported
ready revision 21 with image digest
`sha256:e4ab9ac14f97001f3bb39f4b2b3745715699a63ab45da1645d53990c35162967`.
The owner's hosted retest of the negative annual demand on that deployment
returned HTTP 422 `invalid_request`, the stable message
"Optimization request violates model constraints.", and no run ID. This
closes the hosted error-classification regression for that customer input.

For the corrected-version isolation scenario, the owner checked ODIP test's
operator-visible publication drafts and found no energy dataset draft at
version `1.0.1` or `1.0.2`. The shared Gate 5 plan requires a corrected
`1.0.1`, while ODIP's local acceptance harness uses `1.0.1` for a deliberately
failed missing-hour draft and `1.0.2` for the next valid version. Neither
version was visible in that operator's draft list, and VEF previously
reported `1.0.1` as unavailable. The corrected content and expected-result
authority still need cross-project alignment before this scenario can be
accepted.

## October 27 local implementation follow-up

The [gap-closure verification](energy-gap-closure-verification.md) and
[sanitized evidence summary](energy-gap-closure-evidence.json) record schema,
retention/audit, AI-boundary, runtime, corrected-publication and browser checks.
These working-tree improvements do not change the five open hosted AI case IDs
or provide new deployment or owner acceptance. After explicit consent, the
controlled synthetic AI-05/06 live-model retest passed on October 27; the linked
record includes output/prompt hashes and assistant review, with hosted and final
owner semantic acceptance still open.

## October 27 guided version isolation and request rejection

The [cross-project guided record](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/2026-10-27-guided-release-acceptance.md) contains the two owned runs, protected-browser exports and complete numerical
comparisons: all 252 candidates and the feasible ranking match each version's
independent oracle. Original 1.0.0 stays at EUR 360.67; corrected 1.0.1 produces
EUR 365.64 with the same recommendation and 194 feasible candidates. Original
readback after correction passed. Non-empty existing equipment and unpublished
9.9.9 also returned their expected 422 error codes in browser Network.
GAP-03 is closed for this test environment; AI-10 and final Gate 6 remain open.
The guided record states raw-lineage, run-count and deployment-pin limitations.

### Failed partial-calendar publication: upstream 409 classification

The operator observed ODIP reject synthetic version 1.0.90 (4,368 aligned UTC
hours rather than a complete year) as FAILED / `profile_validation_failed`.
VEF then returned 503 `publication_unavailable`. The pinned ODIP contract
defines GET 409 as an existing, unpublished version; this is a permanent input
rejection. The resource now returns 422 `publication_not_published` for that
status, preserving 404 -> 422 `publication_not_found` and outage -> 503.
Regression tests check all three HTTP responses and unchanged database run
count, plus refusal of cached data on 409 without retry or cache save.
Commit `561887e` passed CI run `40502827158`, including deployment to
`vareng-test`. The operator's hosted retest returned HTTP 422
`publication_not_published`, message "The requested publication version is not
published.", with no run ID in the supplied error envelope. The classification
regression passes. Hosted database run count was not independently measured;
the local HTTP test checks it. This is not an independent hosted VEF test of
invalid published bytes: ODIP intentionally prevents serving this fixture.
The full API suite against an isolated PostgreSQL 17 instance passed 93 tests
with two explicitly skipped checks (95 discovered), zero failures/errors and
zero Checkstyle violations. Documentation links and whitespace checks passed.

### Missing-hour publication status

Following the guided upload/validate steps, the operator reported ODIP version
1.0.99 as FAILED. The prepared synthetic fixture has 8,783 PV values within
the complete 2024 period, matching artifact digests and otherwise valid schemas;
the local worker rejects the missing final hour. The hosted status rejection
passes. The worker failure code/report and an independent hosted VEF validation
of invalid published bytes were not captured. The linked guided record retains
the fixture digest. AI-10 remains open.

### Wrong-unit upload rejection

The operator's version-1.0.91 wrong-unit case returned HTTP 422
`schema_validation_failed`, "Document fails schema energy-import-tariff-v1".
ODIP enforces the EUR/kWh schema at upload, before worker validation. This
passes the hosted wrong-unit upload rejection; the earlier expected FAILED
worker result was incorrect guidance. An earlier `missing_artifact` response
also prevented validation with an incomplete upload set. Independent hosted
VEF validation of invalid served content remains open. The guided record
retains the separate checksum-mismatch fixture 1.0.92.

The subsequent hosted upload of version 1.0.92's altered import tariff returned
422 `checksum_mismatch`, "Artifact digest does not match the manifest". This
passes ODIP upload integrity rejection; independent tampered read responses
and VEF invalid-content rejection remain untested in the hosted environment.
The guided record prepares unsupported publication schema v99 as version
1.0.93; draft-creation rejection is expected but has not yet been observed.
