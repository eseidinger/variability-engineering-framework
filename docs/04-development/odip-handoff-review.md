# ODIP Publication Handoff Review

Date: October 13, 2026. Source inspected: public ODIP revision
[`30735909a7189b8fb3036615fd65474c189cdbd4`](https://github.com/eseidinger/open-data-intelligence-platform/tree/30735909a7189b8fb3036615fd65474c189cdbd4).
This is source inspection of ODIP's current repository, not a VEF connection
test against a deployed ODIP instance.

## Available ODIP implementation

ODIP's [integration plan](https://github.com/eseidinger/open-data-intelligence-platform/blob/30735909a7189b8fb3036615fd65474c189cdbd4/docs/energy-configuration-integration-plan.md)
reports its publication milestone implemented and verified locally. The
[publication controller](https://github.com/eseidinger/open-data-intelligence-platform/blob/30735909a7189b8fb3036615fd65474c189cdbd4/services/platform-api/src/main/kotlin/de/eseidinger/odip/platform/publication/PublicationController.kt)
implements the shared `GET /api/v1/datasets`, exact-version manifest, and
artifact routes. It returns stored bytes with SHA-256 ETags, accepts
`If-None-Match`, and returns `304` for an unchanged version. The
[publication service](https://github.com/eseidinger/open-data-intelligence-platform/blob/30735909a7189b8fb3036615fd65474c189cdbd4/services/platform-api/src/main/kotlin/de/eseidinger/odip/platform/publication/PublicationService.kt)
requires a dataset `READ` grant before returning bytes and distinguishes
missing from unpublished versions.

ODIP pins the same contract kit `0.4.0` and SHA-256
`884e3590614328c2adafa457153e11575dc1be09b9f53d7b0f53704fe39d02b0`
in its [consumer lock](https://github.com/eseidinger/open-data-intelligence-platform/blob/30735909a7189b8fb3036615fd65474c189cdbd4/contracts/contract-kit.lock.json).
Its [CI workflow](https://github.com/eseidinger/open-data-intelligence-platform/blob/30735909a7189b8fb3036615fd65474c189cdbd4/.github/workflows/ci.yml)
configures contract verification. The
[local acceptance harness](https://github.com/eseidinger/open-data-intelligence-platform/blob/30735909a7189b8fb3036615fd65474c189cdbd4/scripts/local-publication-acceptance.py)
publishes energy and non-energy fixtures, verifies bytes and conditional reads,
rejects invalid intervals, checks version isolation, restarts the API, and
restores a PostgreSQL backup. ODIP's
[runbook](https://github.com/eseidinger/open-data-intelligence-platform/blob/30735909a7189b8fb3036615fd65474c189cdbd4/docs/energy-configuration-integration-runbook.md)
records this as local evidence and leaves deployed cross-project acceptance open.

## VEF boundary

VEF's current `EnergyPublicationClient` constructs the implemented exact-version
routes, sends bearer tokens, handles SHA-256 ETags and `304`, and verifies the
returned manifest and five artifact bytes before caching. The
`EnergyPublicationImportService` now connects that client to the immutable
PostgreSQL cache using VEF's managed binding. It rechecks access before each
import and propagates denial even when bytes were cached previously. The
deployment descriptor declares the supplied ODIP origin and HTTPS egress to
ODIP and the installation's identity host. Local tests use a fixture transport;
the optional `EnergyPublicationDeployedTest` has not yet exercised ODIP's deployed
access boundary because the VEF binding is not available in this workspace.
The user reports that the energy dataset `READ` grant has been issued and supplied
the non-secret managed binding prefix `ACCESS_209A1DDA331C` and stable principal
ID `prn_0a3b0952ce0e4c2689bcbdb9331bc185`. The current `vareng`
deployment descriptor configures the prefix. The binding-to-principal association,
active service-access resource, and dataset grant have not been independently
queried in this workspace. VEF still needs authenticated
retrieval, denial of the non-energy
dataset, verified-cache outage behavior under current authorization, and the
deployed recovery suite. ODIP's local implementation is available for this
handoff; no ODIP publication feature needs to be invented by VEF.

The ODIP runbook identifies the first public energy dataset as
`energy-reference-wasserburg-am-inn-2024`, version `1.0.0`, with manifest digest
`252b90b18b594a764a2744ba133a8db56b233e9e375301befc7616e9c18c1715`.
Record the actual deployed revision, image digests, service access resource,
principal ID, binding prefix, endpoint, and published bytes before claiming
the deployed handoff.
