# Energy contract-kit dependency

VEF pins the [energy-system-configuration release 0.4.0](https://github.com/eseidinger/energy-system-configuration/releases/tag/contract-kit-v0.4.0)
in [contract-kit.lock.json](contract-kit.lock.json). The lock records the immutable
archive checksum, target API/profile versions, and numerical reference versions.
These are implementation targets, not claims that the energy workflow exists.

## Validate locally

Requires Python 3.12–3.14 and `uv`. From the repository root:

```sh
python3 scripts/validate-energy-contract-kit.py
```

The script downloads the release into `.cache/energy-contract-kit/`, keyed by
the committed SHA-256. It verifies downloads and cache hits before extraction,
extracts into a fresh temporary directory, and runs the upstream suite with
`uv run --frozen`. Extraction preserves the complete release, including licenses,
source notices, schemas, fixtures, and reviewed expected results. A missing
release, checksum mismatch, or failing upstream test fails the command.

To use an already downloaded archive, add `--archive /path/to/contract-kit-v0.4.0.tar.gz`.
This skips archive retrieval, but frozen Python dependencies must still be
available locally or retrievable by `uv`.

To keep fixtures for consumer tests, use a destination that does not yet exist:

```sh
python3 scripts/validate-energy-contract-kit.py --output-dir /tmp/vef-energy-kit
```

The command prints `ENERGY_CONTRACT_KIT_DIRECTORY`, the path containing
`schemas/`, `fixtures/`, and `expected-results/`. `--prepare-only --output-dir`
verifies and extracts without requiring `uv` or running the upstream tests.
The API CI job prepares the same checksum-pinned release and supplies this path
as an environment variable for Java model conformance tests. These compare every
released configuration and quantity, investment, roof use, and electrical ratio
with the pure Java generator. Extraction alone does not prove consumer conformance.

## CI and handoff

The build workflow validates the lock's released kit independently of application
tests. Image publication depends on this contract job. Integrity regression
tests also exercise corrupt downloads/cache entries and rejected extraction.

The VEF consumer pin implements its portion of `SHARED-01`. Closing cross-project
Gate 1 still requires ODIP's independent pin and successful hosted CI runs.
Local evidence is recorded in the [implementation plan](../../docs/04-development/energy-configuration-implementation-plan.md)
and [model verification](../../docs/04-development/energy-model-verification.md).

## Attribution and updates

The kit is authored by Emanuel Seidinger in the Energy System Configuration
project. Software, schemas, and OpenAPI use Apache-2.0; repository-authored
documentation, examples, fixtures, and expected results use CC BY 4.0. PVGIS
material retains its source terms and the kit's source acknowledgement:
PVGIS © European Communities, 2001–2026. This consumer does not modify the archive.
Keep its `LICENSE`, `LICENSES/`, `NOTICE`, and artifact provenance when using or
redistributing extracted content. The existing food-service fixture's CC0
dedication does not apply to these external fixtures.

For an update, review the upstream compatibility notes and expected results,
change version and checksum together, and validate the new release before
updating implementation claims. Never regenerate upstream expected answers
from the implementation under test or consume a mutable branch.
