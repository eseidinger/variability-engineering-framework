# Energy release-scope verification — October 27, 2026

The maintainer selected new installations for the current release and then
complete UTC calendar years during guided gap closure. Shared
[DEC-07](https://github.com/eseidinger/energy-system-configuration/blob/main/decisions/DEC-07-new-equipment-release-scope.md)
and [DEC-08](https://github.com/eseidinger/energy-system-configuration/blob/main/decisions/DEC-08-complete-calendar-year.md)
record these decisions. Use-case prose 0.3, integration-contract prose 0.5 and
plan prose 0.3 now describe the accepted release scope. The immutable kit 0.5.0,
both consumer pins, and the original 2024 numerical oracle are unchanged.

## Implementation

The existing non-empty `existingComponentIds` rejection remains the required
release behavior: HTTP 422 `existing_equipment_not_supported`, before import or
run reservation. Existing-equipment support is deferred. Existing model and HTTP
validation tests cover this path; the deployed 422/no-run check remains part of
the release regression.

`EnergyProfile.calendarYearHours` requires January 1 at 00:00:00 UTC through
January 1 at 00:00:00 UTC of the next Gregorian year. Both the publication
verifier and the typed profile enforce the rule, and the profile verifies the
expected count and every timestamp. Counts are 8,760 in ordinary years and
8,784 in leap years, including century exceptions. Short, rolling, shifted,
fractional-boundary and multiple-year periods fail before optimization. A
missing leap day cannot be disguised as a valid ordinary-year count.

ODIP's energy adapter independently enforces the same annual boundaries before
passing publication validation. Generic ODIP publication semantics and low-level
short simulation examples remain separate from annual energy optimization.
Existing immutable publications and saved historical run records are not changed.
Any historical short profile is unsupported for new annual optimization.

## Executed checks

- Full VEF API suite against a newly created isolated PostgreSQL 17.11 database:
  **93 discovered, 91 passed, 2 skipped, zero failures/errors**. The skipped cases
  are the configured-deployment test and the optional runtime benchmark.
  Checkstyle passed. The original reference oracle and the exported corrected
  1.0.1 comparison passed again.
- New Java checks cover ordinary/leap/century-year complete profiles, partial,
  rolling, shifted and fractional periods, multiple years and missing annual
  intervals; publication import rejects non-calendar metadata explicitly.
- ODIP worker locked Ruff and full unit suite: **89 passed**. New tests cover
  century rules and invalid annual metadata through `validate_profile`.
- Shared immutable contract-kit suite: **82 passed**. ODIP's archive and vendored
  file verification passed. No immutable vendor content was edited.
- Documentation links and whitespace are checked across all three repositories.
  No browser code, provider prompt or generated numerical oracle changed in this
  step; prior browser/live-AI evidence is retained with its original capture.

The sanitized [scope evidence summary](energy-release-scope-evidence.json)
contains only current-run API suite counts and source hashes. The local bundle
is `apps/prototype/api/target/energy-release-scope-20261027/`:

| Artifact | SHA-256 |
| --- | --- |
| `evidence.json` | `344f76e6d699cb5b143735cd67c666b23b6610503a8d3b192d2620a36a4feb3f` |
| `model-source.tar.gz` | `558a8352862c8aa903701f19a274f42a47876a7d028293a3abb629a86b8b780f` |

The source archive captures current VEF production sources/build pins and ODIP
energy-adapter sources/worker pins. The summary records base commits and working
tree changes. Copy the ignored bundle to controlled release storage before
cleaning `target/`; the tracked sanitized summary preserves the test record.

## Remaining acceptance

GAP-06 is resolved by the maintainer's explicit new-installation release scope.
GAP-07's schema and annual-period implementation now pass locally. Carry both
scope decisions and compatibility notes in the next immutable kit release
under GAP-10; do not replace the published 0.5.0 archive. Execute a hosted
non-empty-list and invalid-period rejection case against pinned images.
Remaining AI-05/06/08/10/11, integrated restore, runtime/security evidence and
final owner acceptance are still open. These local passes do not grant Gate 6.
