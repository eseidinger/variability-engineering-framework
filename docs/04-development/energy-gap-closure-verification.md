# Energy gap-closure verification — October 27, 2026

This follow-up implements and locally checks independently actionable findings
from the [shared gap register](https://github.com/eseidinger/energy-system-configuration/blob/main/reviews/2026-10-27-gap-analysis.md).
It follows the initial documentation audit on the same date. Gate 5 and the
October 19 ODIP handoff remain accepted; Gate 6 and AI-05/06/08/10/11 remain
incomplete. No deployment, release or new owner acceptance occurred.

## Implemented changes

| Shared gap | Change and local evidence | Remaining boundary |
| --- | --- | --- |
| GAP-07 | Offline Draft 2020-12 validation of manifest, all five publication artifacts, optimization request, AI proposal/explanation, and stored result/candidate evidence. Schema files and index match released kit bytes. Optional provenance type/length/format, extra fields, duplicate keys, trailing JSON and candidate bounds have negative tests. | Shared decision on the accepted 1–8,784-hour range versus complete-year reference scope. |
| GAP-08 | V4 audit table, hourly 180-day terminal-run and audit purge; cascading candidate cleanup; boundary/idempotency/publication-preservation tests. Owned run/candidate/explanation reads record allowed/denied events without payloads. Source collector archives production model/source and dependency pins. | Deployment/monitoring, backlog and backup residual-retention policy, executable upgrade replay and durable release archive. Source capture alone does not prove old-model execution after upgrade. |
| GAP-02 | HTTP negative tests run the real explanation validator for foreign run, source, pointer and value mutations; deterministic result stays accessible. Exact UTF-8 string boundary and 16/17-source checks supplement malformed/incomplete evidence tests. | Private hosted fixture setup and AI-08/11 execution; no public evidence-mutation endpoint was added. |
| GAP-03/05 | ODIP local publication harness now rejects missing-hour draft 1.0.99 and publishes genuinely changed synthetic 1.0.1; restart and actual PostgreSQL restore preserve its bytes. VEF imports those exported bytes, compares all 252 candidates/feasibility/metrics, ranking and recommendation against the independent Python reference model, and reproduces original 1.0.0. | Shared review of the correction for acceptance, hosted pinned owned-run comparisons and integrated ODIP/VEF/platform restoration. VEF's local comparison uses the optimizer directly, not a deployed HTTP run. |
| GAP-11 | Private last-observed ODIP state/time/fallback metrics; concurrent idempotent reservations and distinct claims; interrupted-claim requeue and stale-token rejection; timeout/failure classification with sanitized logs. Full-year exact-limit compute measurement passes. | Hosted metrics collection and operational thresholds, deployment-resource measurements including serialization/database/queue, complete workload restart/restore. |
| GAP-12 | Automated production-bundle Chromium/axe desktop/mobile workflow, keyboard confirmation, validation focus, session expiry/denial, provider failure, safe explanation rendering and infeasibility. Fixed missing banner landmark, long-text overflow and stale run responses; UI regression covers delayed polling from a previous run. | Real platform sessions and manual assistive-technology review. Browser HTTP is mocked; automation does not establish full WCAG conformance. |
| GAP-10 | Sanitized evidence manifest, report/source digests and reproducible model-source archive; a tracked evidence summary preserves local results. CI runs browser checks, transfers count-only API reports between jobs, and collects the source/check bundle with 30-day artifact retention. Publication depends on successful evidence collection; the new CI job was exercised locally, not on hosted CI. | Complete pinned deployment inventory, reviewed long-term artifact storage and final acceptance by all three owners. |

The original shared kit, numerical reference and model versions are unchanged.
Runtime schema resources include the upstream licenses and NOTICE. Public API
routes and authorization policy are unchanged. Audit write failure fails reads
closed. Lifecycle batching and backup semantics are documented in
[deployment operations](../05-operations/deployment.md#energy-lifecycle-and-dependency-observations).

## Executed checks

| Check | Result |
| --- | --- |
| VEF clean full Maven API suite against isolated PostgreSQL 17.11, including restored corrected ODIP fixture | 88 discovered; 87 passed, 1 deployed-only test skipped; zero failures/errors; Checkstyle passed. |
| Tightened corrected-data comparison (money exact, energy 1e-6, fractions 1e-9) | Passed separately after the full suite. |
| Opt-in 100,000-candidate full-year runtime test | Passed separately. Before the live-model run: 89 discovered, 88 passed, 1 skipped. Final combined inventory including the two live cases: 91 discovered, 90 passed, 1 skipped. |
| VEF UI lint, unit tests, production build | Passed; 7 unit tests after adding the stale-poll regression. |
| Chromium production-bundle browser and axe checks | 24 passed, widths 1,280 and 390 pixels; no axe rules disabled. |
| Controlled synthetic AI-05/06 live-model checks | Both passed with the actual ExplanationModel and explicit user consent; outputs reviewed against supplied evidence by the assistant. Hosted cases and owner semantic acceptance remain open. |
| VEF script tests | 32 passed, including 3 evidence-collector tests. |
| Pinned-kit preparation and packaged-schema byte comparison | Passed for kit 0.5.0, SHA-256 `ae4edaf6799d4b57510d14d2004c5b65ff6b05f3b225e96bbd5971b30ce8832b`. |
| ODIP worker locked sync, Ruff and unit tests | Passed; 87 tests. |
| ODIP API offline `ktlintCheck bootJar` | Passed. The initial audit's Gradle cache permission problem was resolved using authorized local execution. No fresh full Kotlin test-suite claim. |
| ODIP local publication/restart/actual PostgreSQL dump/restore | Passed with valid corrected 1.0.1, rejected 1.0.99 and unchanged original/non-energy publications. |

The shared kit's 82 tests and ODIP UI's 13 tests passed in the preceding audit;
those components were not changed in this follow-up. After automatic approval initially blocked external provider traffic, the user
explicitly authorized the synthetic AI-05/06 payloads and API costs. Both tests
then passed with `gpt-6.1-sol`, prompt SHA-256
`1264407e104ff69d397b41ae8a7d45319826950b00c920a5f76d5da693002b82`.
The assistant reviewed both generated outputs: AI-05 explicitly says global
optimality is not established and lists the missing battery replacement price;
AI-06 retains the evidence-backed recommendation and costs despite the injected
metadata. Citations, units and percentage conversions match the supplied compact
examples. This is controlled local real-model evidence, not hosted execution or
final human semantic acceptance. The compact examples are not numerical oracles.

The exact-limit measurement used Java 25.0.4 on a host exposing 32 processors,
with an asserted 536,870,912-byte maximum Java heap. Pure optimization took
**15.399579901 seconds** for 100,000 candidates × 8,784 hours. Sum of heap-pool
peak usage was 414,981,312 bytes; this is not process RSS or a simultaneous peak.
The fixture uses 20 PV, 20 inverter and 6 battery models, producing exactly the
100,000-candidate limit. This is one synthetic supported maximum-count workload,
not an exhaustive worst-case proof for every catalog or the deployed service.

## Reproduction

Use an isolated PostgreSQL database and explicit Quarkus test datasource
properties as described in the API development instructions. Never point these
tests at a retained application database. Prepare the immutable kit, then export
the corrected publication using ODIP's
[local harness](https://github.com/eseidinger/open-data-intelligence-platform/blob/main/docs/energy-configuration-integration-runbook.md#corrected-version-regression-boundary).
Supply both paths when running the API suite:

```sh
ENERGY_CONTRACT_KIT_DIRECTORY=/path/to/contract-kit \
ENERGY_CORRECTED_FIXTURE_DIRECTORY=/path/to/odip-corrected-energy \
./mvnw --batch-mode --no-transfer-progress test
```

From `apps/prototype/api`, explicitly opt into the isolated compute measurement:

```sh
JAVA_TOOL_OPTIONS=-Xmx512m ENERGY_RUN_BENCHMARK=true \
ENERGY_CONTRACT_KIT_DIRECTORY=/path/to/contract-kit \
./mvnw --batch-mode --no-transfer-progress -Dtest=EnergyRuntimeBenchmarkTest test
```

The corrected-publication test skips without its export path; the runtime test
skips without explicit opt-in. The deployed test also requires its own configured
endpoint. Skips are recorded and never treated as passed acceptance cases.

From `apps/prototype/ui`, run `npm run lint`, `npm test -- --watch=false`,
`npm run build`, `npx playwright install chromium`, and `npm run test:energy-browser`.
The browser check deletes a prior report before starting and writes a completed
report only after every case passes.

From the repository root, after the successful test runs:

```sh
python3 scripts/collect-energy-evidence.py \
  --output apps/prototype/api/target/energy-acceptance-20261027-final \
  --runtime-report apps/prototype/api/target/energy-runtime-benchmark.json
```

Use a new output directory for each capture. The collector keeps suite counts
and report hashes, excluding raw test logs and system properties. It captures
tracked and non-ignored new production sources, schema licenses, Maven build
metadata and the immutable kit lock. It records the base commit and dirty-tree
status; the uncommitted changes are identified by individual source hashes.
This capture was supplemented with ODIP's synthetic exported publication archive
and synthetic AI outputs/manifests, plus the explicitly listed UI/script/worker/build results.

## Retained local evidence

The [sanitized evidence summary](energy-gap-closure-evidence.json) is tracked
alongside this record. Base revisions are VEF
`64b9293fedb83d581a060008d1db844bdc710eb0` and ODIP
`60f1b27bf4a8c6392c9e33631a13b508337b0746`, with the changes in the working trees.
The source archive checksum, rather than the base revision alone, identifies the
new implementation. The local bundle is in
`apps/prototype/api/target/energy-acceptance-20261027-final/`:

| Artifact | SHA-256 |
| --- | --- |
| `ai-live-acceptance.tar.gz` | `7af7681acb2c32e0b579e9adf0c527cd75b883de1ef539ccda844899ac2b5e5a` |
| `evidence.json` | `e0aac7aedcb8a78a55a6a9af8be6004ee46bb417f75150d75a39bf47c23fd4c4` |
| `model-source.tar.gz` | `10f3a573a8136b20f462ba8c5ec59988687e34c246f1df0d008c4a1d157555fd` |
| `odip-corrected-publication.tar.gz` | `1825e0651d4f70d38bfc49bb3bb406b3ff94a15c558d9e48650f1b84491fedb3` |

The corrected manifest is
`ebfd2cc7dcdf73cef4ed81a94b693ec23c4448460c8161ca648369ea0a930f93`;
the tested ODIP jar is
`5ed914cc77e062a465c5762ec03b10ff654531ef4b22ada244db9fbda97b46f7`.
The bundle lives in ignored build storage and must be copied to controlled
release storage before cleaning `target/`. Its tracked summary is durable in the
source change; neither is a deployed release approval. Final owner review,
hosted evidence and agreed existing-equipment/annual-profile semantics remain open.
