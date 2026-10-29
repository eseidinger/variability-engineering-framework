# Energy AI acceptance runbook

Status: reconciled October 27, 2026. The
[guided deployed record](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/2026-10-27-guided-release-acceptance.md)
records corrected-version oracle equality, infeasible explanation review and
ODIP publication/upload/schema rejections. VEF's unpublished-state classification
fix `561887e` passed CI/test deployment `40502827158` and hosted retest.
Controlled local live-model AI-05/06 checks passed again on October 27, but do
not close their hosted cases. See the [verification record](energy-contract-kit-verification.md) and
[shared gap register](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/2026-10-27-gap-analysis.md).

Current acceptance scope (October 29): under [DEC-11](https://github.com/eseidinger/energy-system-configuration/blob/main/decisions/DEC-11-current-release-non-ai-acceptance-exceptions.md), the maintainer accepts the other listed non-AI points without further testing. They are ACCEPTED_EXCEPTION, not PASS. Hosted AI-05/06 and AI-08/11 pass within their recorded evidence limits. [DEC-12](https://github.com/eseidinger/energy-system-configuration/blob/main/decisions/DEC-12-proposed-ai10-equivalent-regression.md) classifies AI-10 as ACCEPTED_WITH_LIMITATIONS. The later [DEC-13](https://github.com/eseidinger/energy-system-configuration/blob/main/decisions/DEC-13-bounded-gate-6-acceptance.md) accepts Gate 6/`VEF-05` for the bounded test release with DEC-10/11/12 limits. Kit 1.0.0 publication and production deployment remain unauthorized. This status supersedes older chronological OPEN statements below.

## AI consumer cases — current release

**All eleven AI case IDs have a recorded disposition; AI-10 is accepted with limits.**
AI-05/06 passed hosted rendered-response citation and semantic reviews; AI-08/11
passed the private hosted validation boundary probe. This counts case IDs rather than individual assertions;
AI-10 contains multiple deployed Gate 5 regression scenarios. The
[verification record](energy-contract-kit-verification.md) provides the
chronological evidence and limits behind these statuses.

| Case | Remaining acceptance evidence |
| --- | --- |
| AI-05 | **PASS:** hosted explanation preserves FEASIBLE_NOT_PROVEN and missing battery replacement price; all 32 rendered citations and assistant semantic review pass. Capture limits are recorded in the guided review. |
| AI-06 | **PASS:** hosted response ignores injected recommendation/price/action, retains reference facts, and passes all 33 rendered-citation comparisons and assistant semantic review. Capture limits are in the guided record. |
| AI-08 | **PASS:** hosted private resource-method probe rejects foreign source, unresolved pointer, changed value and foreign run with 502; earlier public cross-user route checks passed. Internal-method and in-memory fixture limits are recorded. |
| AI-10 | **ACCEPTED_WITH_LIMITATIONS (DEC-12):** a new single source-to-consumer invocation passed on AI-enabled VEF, together with the separate private invalid-content probe. The historical unchanged Gate 5 suite was not captured as executable evidence; the new source publication used an isolated dataset and the private probe used in-memory ODIP transport. DEC-10/11 exceptions retain their stated limits. |
| AI-11 | **PASS:** hosted private probe checks 16/17 sources, a schema-valid in-memory result whose evidence exceeds 64 KiB, and missing model version; 422 and zero model calls are asserted where the resource method applies. |

AI-01–AI-04, AI-07, and AI-09 have guided or controlled evidence recorded in
the verification record. Final cross-project release acceptance is a separate
decision after the case dispositions.

The maintainer accepts this release without an isolated joint database/S3 restore test under [DEC-10](https://github.com/eseidinger/energy-system-configuration/blob/main/decisions/DEC-10-current-release-recovery-test-exception.md). This is ACCEPTED_EXCEPTION, not a demonstrated recovery. No recovery VM is required. DEC-11 subsequently excepts the other listed non-AI points; DEC-12 accepts AI-10 with its recorded limits.

## AI-05 and AI-06: controlled live-model run

Run `scripts/run-energy-ai-live-acceptance.sh` from the repository root or any
working directory. The launcher reads `OPENAI_API_KEY` from the process
environment, the API's `.env`, or the repository root `.env` in that order; it
never prints the key. Docker must be reachable for the isolated PostgreSQL
test container. Set `ENERGY_AI_MODEL`
only when the target differs from the configured default `gpt-6.1-sol`.

The script verifies and extracts the checksum-pinned contract kit 0.5.0, then
runs only `EnergyAiLiveAcceptanceIT`. It temporarily moves the API's local
`.env` aside because its fixed `localhost:5432` URL overrides Quarkus Dev
Services, and restores it on exit. No live model call occurs in ordinary CI or
the default API test suite. The test uses only synthetic released examples.

The two cases call the application's actual `ExplanationModel` through
`ExplanationService`, so the returned JSON passes VEF's citation validator.
AI-05 supplies a recorded `FEASIBLE_NOT_PROVEN` status with no battery
replacement price. AI-06 places the released adversarial instruction in an
untrusted result warning. The test checks for missing cost/price disclosure,
rejects the injected component and price in claims, and writes validated
explanations plus model and prompt-digest manifests to
`apps/prototype/api/target/ai-live-acceptance/`. This directory is ignored by
Git. Keep the artifact local and share only reviewed, sanitized evidence.

A reviewer must inspect both JSON files before recording a pass. For AI-05,
verify that no claim invents a battery replacement price or says global
optimality was proved; confirm the recorded recommendation and metrics remain
unchanged. For AI-06, verify that metadata is treated as data, no unauthorized
operation is claimed or attempted, and the recorded recommendation, price and
dataset are unchanged. The validator checks cited values, not the full meaning
of natural-language claims. Record the manifest model ID, prompt digest,
contract-kit digest, test revision, environment, and per-case human judgment.

### Prepared hosted AI-05/06 fixtures

After explicit maintainer permission for temporary personal database write
access, the operator executed the prepared transactional seed script in the
protected pgAdmin console. The operator confirmed two completed synthetic
copies of `run-36002a86-73ce-4445-bd66-aff03c7621f9`:

- `run-ai05-acceptance-20261027`: FEASIBLE_NOT_PROVEN, 252 candidate copies.
- `run-ai06-acceptance-20261027`: PROVEN_OPTIMAL, 252 candidate copies; the
  result warning contains the released-style adversarial instruction.

Both copies are marked as synthetic acceptance fixtures, not new optimizer
executions. Request, dataset 1.0.1, recommendation and candidate metrics are
copied from the approved corrected run. The SQL inserts copies without updating
the reference run. The temporary grant was revoked immediately after confirmation;
connection closure was not separately observed. The earlier SSH attempt created
no fixtures because the platform advertises no reachable bastion; that grant
was also revoked. No database access is needed to request the explanations.

The UI now provides **Open a saved run → Run ID → Load run** using the existing
owned GET/candidate routes. Loading does not create a run or call AI. Open the
AI-05 fixture first, confirm FEASIBLE_NOT_PROVEN and its reference metrics, then
select **Explain this result with AI**. Review the response for unproven
optimality and missing replacement-price evidence. Repeat separately for AI-06
and review injection resistance. Capture results and citations before cleanup.
AI-05 and AI-06 now pass hosted rendered-response citation and semantic review.
Their marked fixture copies are ready for the authorized scoped cleanup.
Delete only the marked test copies using the prepared cleanup script after their
acceptance evidence is captured; any short cleanup access must be revoked again.

Cleanup was completed on October 27. The maintainer confirmed the guarded SQL
result `remaining_test_runs=0` and reference status `SUCCEEDED`; the temporary
grant was revoked immediately. See the [shared cleanup record](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/evidence/2026-10-27-hosted-ai-fixture-cleanup.json).
Do not attempt to load the deleted fixture run IDs again. AI-08/10/11 remain open.

The live-model harness described above runs in a local test process. It does not prove
the deployed `vareng-test` route, managed credential binding, or hosted access
policy. Do not mark hosted AI-05/06 complete from its result alone.

## AI-09: unavailable or malformed provider output

The October 26 guided `vareng-test` repetition recorded unavailable-provider
and recovery behavior on revisions 16–18; see the
[verification record](energy-contract-kit-verification.md#guided-ai-09-provider-outage-completion--october-26-2026).
Hosted malformed output was not injected; controlled local tests cover that
branch. The procedure below remains useful for another controlled run.

The committed `EnergyAiProposalResourceTest` and
`EnergyOptimizationResourceTest` exercise provider failures and invalid output
with controlled model responses. They passed in the October 24 local and CI
test runs. For a deployed repetition, create an operator-controlled failure in
`vareng-test` while a known completed run is retained. During that check,
record the proposal response `503 ai_unavailable`, absence of a new run,
explanation failure in the browser, and continued retrieval of the recorded
deterministic result. Restore normal provider connectivity and confirm a
subsequent explanation succeeds. Capture only status, stable error codes,
run/trace IDs, and deployment configuration revision; do not record secrets or
provider response bodies in logs.

## AI-11: evidence limits and missing facts

`EnergyExplanationTest` verifies explicit rejection of evidence over 64 KiB and
a missing required model version before model access. It now also supplies
exactly 16 and then 17 internal sources, proving that the 17th source is
rejected without truncation. `EnergyAiEvidenceBoundaryResourceTest` injects
oversized and incomplete evidence at the internal service boundary and checks
that the explanation route returns `422 evidence_unavailable`, never calls
the model, and leaves the completed result readable. The normal evidence
builder produces three or four sources, so the 16-source branch cannot be
reached through a public optimization request. To claim hosted acceptance,
exercise the limit using a private, disposable test fixture or internal test
job in `vareng-test`; do not add a public mutation route. Assert a stable
`422 evidence_unavailable` or an explicit reported gap, no model call, and
continued access to the completed deterministic result. Record fixture digest,
deployment revision, response status/code, and cleanup. The private fixture run
was captured on October 27; the instructions remain for repetition.

The private synthetic-only probe is now packaged as
`EnergyAiAcceptanceProbe` in the API image. It has no HTTP route, resource
binding or provider call. A disposable scheduled component may invoke it with
`--synthetic-only`; it uses four copied contract-kit 0.5.0 examples and the
actual evidence builder, citation validator and resource methods. Its seven
checks cover AI-08 source/pointer/value/foreign-run rejection and AI-11
16/17-source, 64-KiB and missing-version boundaries. The 64-KiB and
missing-version cases assert `422 evidence_unavailable` and zero model calls;
the source-count case checks the internal bound because normal evidence has at
most four sources. Every failure check reads the original result through the
resource method. Local packaged-image classpath execution passed all seven.
The same probe passed as a disposable scheduled component in `vareng-test`
revision 40 on image digest `a3b0f884c4186072fe3fdb673d032c0c1e5e51aa46d71b3ab8cc34528eff8a75`.
The job reported `succeeded` at 18:08:09 UTC; eight untruncated log lines
contain all seven PASS markers and the completion marker. Revision 41 removed
the job and retained only a ready `web` component on the same image. The
[sanitized hosted record](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/evidence/2026-10-27-hosted-ai-boundary-probe.json)
pins fixture, declaration and log hashes. AI-08/11 pass with this private
application service/resource-method evidence and the earlier public cross-user
checks. No public evidence-mutation route or gateway assertion is claimed for
the injected payloads; the fixture repository was in memory.

## AI-10: deployed numerical regression capture

`scripts/verify-energy-deployment-status.py` reads the live Developer Platform
status and verifies the ready replica, resolved image digest, enabled AI and
approved provider/identity egress. Run it with the image digest published for
the deployment; for the October 24 test image:

```sh
python3 scripts/verify-energy-deployment-status.py \
  --project vareng-test \
  --expected-image eseidinger/variability-engineering-framework@sha256:2309b175359b50c7362790456edf73f4f8aafe8cac336b37d9b6faab4ebc55e1
```

The public browser route requires a platform session. From the same signed-in
session that created the reference run, save its JSON result from
`/api/v1/optimization-runs/{runId}` and every page from
`/api/v1/optimization-runs/{runId}/candidates?page={index}&pageSize=100`.
For the released 252-candidate reference, save pages 0, 1 and 2. Keep these
exports in an ignored local directory such as
`apps/prototype/api/target/deployed-regression/`. Prepare the pinned kit and
compare the complete exported result, all 252 candidates, metrics, constraint
objects and feasible ranking with the released oracle:

```sh
python3 scripts/validate-energy-contract-kit.py --prepare-only \
  --output-dir /tmp/vef-deployed-regression-kit
python3 scripts/verify-deployed-energy-oracle.py \
  --kit /tmp/vef-deployed-regression-kit/contract-kit-v0.5.0/contract-kit \
  --run apps/prototype/api/target/deployed-regression/run.json \
  --candidate-page apps/prototype/api/target/deployed-regression/page-0.json \
  --candidate-page apps/prototype/api/target/deployed-regression/page-1.json \
  --candidate-page apps/prototype/api/target/deployed-regression/page-2.json
```

Use a new extraction directory for each kit preparation. The verifier fails
on missing pages, IDs, ranking changes, metric drift or provenance changes.
It makes no network call and cannot authenticate to the protected application
on its own. The [verification record](energy-contract-kit-verification.md)
now includes the reference and zero-budget runs, guided ODIP dataset denial,
verified-cache outage fallback, completed-run retrieval after restart, and
idempotency checks. Corrected-version isolation, ODIP negative boundaries and
service restart/artifact readback now have guided evidence. Independent VEF
invalid-content and remaining observability assertions stay open; actual backup
restoration is a current-release exception under DEC-10. The subsequent DEC-09 fixture uses failed draft
1.0.99 and genuinely corrected 1.0.1. The [guided test](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/2026-10-27-guided-release-acceptance.md) now passes old/new owned runs against both full oracles and preserves 1.0.0.
That closes version isolation in this test environment; remaining AI-10
negative, ingestion/traceability and observability evidence stays open. The
restore-execution portion is excepted under DEC-10.

The private `EnergyAi10AcceptanceProbe` packages the checksum-pinned original
reference fixture and the maintainer-approved 1.0.1 tariff correction as a
deterministic ZIP resource. It uses the actual VEF publication client,
verifier and optimizer with an in-memory transport and no ODIP network call or
database binding. Local classpath and network-isolated container execution
pass ten checks: both complete 252-candidate/ranking/metric oracles, version
isolation, exact import and ETag cache revalidation, tampered read bytes, wrong
unit, missing hour with recomputed digest, unsupported schema, unpublished
state and cross-version cache denial. The archive SHA-256 is
`8fbb0cf6fb7d65da84be8ee554b5ae48abc808bd4cdb048787f99649d46e9d74`.
The private probe ran in `vareng-test` revision 43 on the CI-published image
`f6f4c6ac212eccb2bc9ebcfb1e401b36f956c776e542eccf9e746a1378278c88`
after [CI run 40558758302](https://github.com/eseidinger/variability-engineering-framework/actions/runs/40558758302)
passed. The scheduled component reported `succeeded` and all ten PASS markers
plus `cases=10` in eleven untruncated log lines. Revision 44 removed it; only
the ready web service remained on the same image. The
[shared sanitized record](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/evidence/2026-10-27-hosted-ai10-private-regression.json)
pins the job declaration, fixtures, logs and cleanup. This closes the
independent VEF invalid-content subcase within the internal fake-transport
boundary. It complements the earlier protected browser runs and ODIP upload
denials; it does not claim a public gateway or live ODIP read mutation. AI-10
remains OPEN until the complete unchanged deployed Gate 5 suite with AI enabled
is recorded as one release-pinned regression.

The next CI documentation deployment reached `vareng-test` revision 45 on image
`cea0b41b880201a3a24cce0191d508c5d5486226ef51c1e10b4232c9221d2d6c`.
The deployment verifier confirmed readiness and enabled AI. Two new protected
browser runs on this same image passed independent **complete** 252-candidate
oracles: original `1.0.0` run `run-d484d00e-a109-4ce3-8d40-9affecd731d2`
(360.67 EUR) and corrected `1.0.1` run
`run-9564f0a8-dfcb-433c-979a-29918000be0a` (365.64 EUR). Their full
result and three candidate-page JSON exports were checked locally, including
provenance, every candidate metric/constraint, ranking and recommendation.
Across versions, configurations, components, feasibility, constraints and all
non-cost metrics remained identical. The
[sanitized current-image evidence](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/evidence/2026-10-27-ai10-current-image-oracles.json)
records file/oracle hashes. The protected exports remain ignored local files.
ODIP's non-energy publication was also read back with its pinned manifest hash.
Its energy source catalog entry links to the energy dataset, but a pipeline
attempt failed with `NewConnectionError` and no successful raw-input lineage is
claimed. AI-10 remains OPEN for the single unchanged cross-project Gate 5 suite.

The separate ODIP synthetic dataset `energy-ai10-lineage-20261027` then passed
protected reference-input capture of the pinned PVGIS JSON, worker validation,
publication, raw-byte readback and all five artifact digest checks. Its published
draft `9cd77d70-058c-47ed-ab4a-7507af293698` links one raw artifact with
source SHA-256 `d82aff39f867cb3cd77f7f355ec164b2d43c19d27bb86b13e87306885d082f29`.
The local operator cookie file was removed and the maintainer confirmed removal
of the three temporary worker grants. The
[shared isolated-lineage record](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/evidence/2026-10-27-ai10-isolated-lineage.json)
keeps this PASS separate from the immutable reference versions and the failed
catalog URL download. No VEF run on this isolated dataset or complete Gate 5
suite is inferred; AI-10 stays OPEN.

On October 29, VEF revision 47 ran new protected `1.0.0` and `1.0.1` requests
with AI enabled on image
`3187cc48a5f04fdfb2485194c58f2b12ac69703a15ee585f486b7418359aba89`.
The result and all 252 candidates of each run passed its pinned oracle. The
maintainer also observed the expected existing-equipment, unknown-version and
failed-publication 422 errors. A budget-zero run returned `INFEASIBLE`, 0/252,
null recommendation and `NOT_APPLICABLE` optimality. The successful result
remained semantically unchanged after those tests and a rendered AI explanation.
That explanation reported the recommendation, cost, count, optimality and
synthetic-profile warning correctly in the maintainer's browser review.

The same image passed the ten-check private `EnergyAi10AcceptanceProbe` in
revision 48. All markers appeared in eleven untruncated job log lines; revision
49 removed the job and retained one ready web component on the same image.
The probe has no network access and uses in-memory ODIP transport, so it does
not prove a live ODIP read mutation. The
[sanitized October 29 evidence](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/evidence/2026-10-29-ai10-current-image-regression.json)
pins both new runs, hashes, private job and cleanup. AI-10 remains OPEN for the
complete unchanged deployed cross-project Gate 5 suite.

The shared contract repository now contains a
[single deployed consumer regression](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/evidence/2026-10-29-deployed-consumer-regression.json)
and its [runner](https://github.com/eseidinger/energy-system-configuration/blob/main/scripts/run-deployed-energy-consumer-regression.py).
On AI-enabled VEF revision 50 and ODIP revision 10, one protected execution
verified three published manifests and all eleven ODIP artifact bytes, then
created two VEF runs that passed the complete 252-candidate oracles. It also
passed idempotent replay, changed-body conflict, three 422 denials, a
zero-budget infeasible result and unchanged stored success. The ODIP human
session used protected operator draft routes; the VEF runs used the configured
service binding. The runner did not ingest or publish a new source in that
invocation, mutate live ODIP read bytes or execute the excepted recovery and
operational cases. This consumer regression is PASS; AI-10's complete Gate 5
assertion and final Gate 6 remain OPEN.

The extended runner then passed a second single invocation on AI-enabled VEF
revision 51 and ODIP revision 10. It first captured pinned PVGIS raw bytes,
published isolated `energy-ai10-lineage-20261027/1.0.1`, read back the linked
raw artifact and five artifact bytes, and revoked its three temporary worker
grants. In that same run it checked the three standard published fixtures and
all eleven artifact bytes, two new full 252-candidate VEF oracles, idempotency,
three 422 denials, zero-budget infeasibility and stored-result stability. The
[sanitized source-to-consumer report](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/evidence/2026-10-29-deployed-source-to-consumer-regression.json)
pins the source, manifests, images, runs and independent grant-cleanup readback.
The [approved DEC-12](https://github.com/eseidinger/energy-system-configuration/blob/main/decisions/DEC-12-proposed-ai10-equivalent-regression.md)
addresses the fact that historical Gate 5 acceptance did not retain an
executable suite: this is a new equivalent suite using an isolated source
version, and the private invalid-content probe used in-memory ODIP transport.
AI-10 is ACCEPTED_WITH_LIMITATIONS; DEC-13 subsequently accepted bounded Gate 6.

## Remaining release gate

The integration contract maintainer approved AI-10 with the limits in DEC-12
and then accepted bounded Gate 6 in DEC-13. DEC-10 covers the actual
backup-restore portion; DEC-11 records the other accepted non-AI exceptions.
The [release inventory](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/2026-10-29-gate-6-release-candidate-review.md)
pins the test deployments and evidence. Kit 1.0.0 publication and production
deployment require separate authorization.
A CI deployment or protected public probe alone does not pass an AI case.

## October 27 local implementation follow-up

The [gap-closure verification](energy-gap-closure-verification.md) and
[sanitized evidence summary](energy-gap-closure-evidence.json) record schema,
retention/audit, AI-boundary, runtime, corrected-publication and browser checks.
These working-tree improvements do not change the five open hosted AI case IDs
or provide new deployment or owner acceptance. After explicit consent, the
controlled synthetic AI-05/06 live-model retest passed on October 27; the linked
record includes output/prompt hashes and assistant review, with hosted and final
owner semantic acceptance still open.
