# Energy AI acceptance runbook

Status: reconciled October 27, 2026; latest recorded hosted observations are
from October 26. CI run `40353197285` for `b6990cb` passed and deployed test
revision 21; the hosted invalid-input retest returned 422. Controlled local
live-model AI-05/06 checks passed on October 24, but do not close their hosted
cases. See the [verification record](energy-contract-kit-verification.md) and
[shared gap register](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/2026-10-27-gap-analysis.md).

## Open AI consumer cases — October 26, 2026

**Five of the eleven AI case IDs remain incomplete:** AI-05, AI-06, AI-08,
AI-10, and AI-11. This counts case IDs rather than individual assertions;
AI-10 contains multiple deployed Gate 5 regression scenarios. The
[verification record](energy-contract-kit-verification.md) provides the
chronological evidence and limits behind these statuses.

| Case | Remaining acceptance evidence |
| --- | --- |
| AI-05 | Hosted `FEASIBLE_NOT_PROVEN` explanation and human review; the replacement-price gap and controlled local branch have evidence. |
| AI-06 | Hosted adversarial metadata case and semantic review; the controlled local real-model case passed. |
| AI-08 | Hosted rejection of unapproved sources, unresolved pointers, and altered values; cross-user route checks passed. |
| AI-10 | Complete unchanged deployed Gate 5 suite, including corrected-version isolation, remaining negative cases, ODIP restart/observability, and actual backup restore. |
| AI-11 | Hosted evidence-limit fixture and explicit failure; local 16/17-source and 64-KiB boundary tests passed. |

AI-01–AI-04, AI-07, and AI-09 have guided or controlled evidence recorded in
the verification record. Final cross-project release acceptance is a separate
decision after the remaining tests.

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

This harness runs in a local test process using a real model. It does not prove
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
deployment revision, response status/code, and cleanup. The private fixture
mechanism has not yet been implemented, so hosted AI-11 remains open.

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
idempotency checks. The full deployed Gate 5 suite still needs corrected
version isolation, remaining negative cases, ODIP restart and observability,
and actual backup restoration. The subsequent DEC-09 fixture uses failed draft
1.0.99 and genuinely corrected 1.0.1. The [guided test](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/2026-10-27-guided-release-acceptance.md) now passes old/new owned runs against both full oracles and preserves 1.0.0.
That closes version isolation in this test environment; remaining AI-10
negative, ingestion/traceability, observability and recovery evidence stays open.

## Remaining release gate

After these cases, run the unchanged deployed Gate 5 regression for AI-10,
including ODIP publication, exact reference result, negative cases, outage,
restart and actual backup restoration. Record image, kit, policy and deployment
digests, then obtain the three project owners' release acceptance. The current
CI deployment and protected public probe do not close those gates.

## October 27 local implementation follow-up

The [gap-closure verification](energy-gap-closure-verification.md) and
[sanitized evidence summary](energy-gap-closure-evidence.json) record schema,
retention/audit, AI-boundary, runtime, corrected-publication and browser checks.
These working-tree improvements do not change the five open hosted AI case IDs
or provide new deployment or owner acceptance. After explicit consent, the
controlled synthetic AI-05/06 live-model retest passed on October 27; the linked
record includes output/prompt hashes and assistant review, with hosted and final
owner semantic acceptance still open.
