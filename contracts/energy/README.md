# Energy contract-kit dependency

VEF pins the [energy-system-configuration release 0.5.0](https://github.com/eseidinger/energy-system-configuration/releases/tag/contract-kit-v0.5.0)
in [contract-kit.lock.json](contract-kit.lock.json). The lock records the immutable
archive checksum, target API/profile and AI envelope versions, and numerical reference versions.
Kit 0.5.0 adds AI assistance contracts; it does not change the accepted energy
calculation and ODIP publication baseline. The pin establishes availability of
these contracts, not completion of VEF AI assistance.

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

To use an already downloaded archive, add `--archive /path/to/contract-kit-v0.5.0.tar.gz`.
This skips archive retrieval, but frozen Python dependencies must still be
available locally or retrievable by `uv`.

To keep fixtures for consumer tests, use a destination that does not yet exist:

```sh
python3 scripts/validate-energy-contract-kit.py --output-dir /tmp/vef-energy-kit
```

The command prints `ENERGY_CONTRACT_KIT_DIRECTORY`, the path containing
`schemas/`, `fixtures/`, and `expected-results/`. `--prepare-only --output-dir`
verifies and extracts without requiring `uv` or running the upstream tests. Both modes
also compare the packaged API schemas and index against the immutable release bytes.
Runtime validation resolves references locally and never fetches schema URLs.
The API CI job prepares the same checksum-pinned release and supplies this path
as an environment variable for Java model conformance tests. These compare every
released configuration and quantity, investment, roof use, and electrical ratio
with the pure Java generator. Extraction alone does not prove consumer conformance.

## CI and handoff

The build workflow validates the lock's released kit independently of application
tests. Image publication depends on this contract job. Integrity regression
tests also exercise corrupt downloads/cache entries and rejected extraction.

The VEF consumer pin implements its portion of `SHARED-01`. ODIP independently
pins the same 0.5.0 archive and checksum; its handoff was accepted on October 19. Upstream Gate 5 was accepted by maintainer direction on October 17,
2026. Local 0.5.0 adoption evidence is recorded in the
[consumer verification](../../docs/04-development/energy-contract-kit-verification.md)
and [implementation plan](../../docs/04-development/energy-configuration-implementation-plan.md).
The VEF AI implementation is present, and owner-confirmed production checks
are recorded in the verification record. Gate 6 still requires completion of
all eleven acceptance cases, semantic review, the unchanged deployed Gate 5
regression suite, and final release acceptance.

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
