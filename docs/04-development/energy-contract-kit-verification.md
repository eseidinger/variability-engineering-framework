# Energy Contract Kit Consumer Verification

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
