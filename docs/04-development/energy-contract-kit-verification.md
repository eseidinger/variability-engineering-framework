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
