# Persistence and Deployment Architecture

Status: food-service population storage, verified energy publications, and durable
owned optimization runs are implemented. The owner confirmed the production
energy workflow on October 19, 2026; full deployed regression and recovery
acceptance remain open. See the [verification record](../04-development/energy-contract-kit-verification.md).

The food-service MVP uses PostgreSQL with a normalized relational schema, applied by Flyway at startup. `analysis_population` is keyed by immutable population ID and version. Related tables store artifact provenance, dimension definitions, records, canonical element usages, dimension states and values, and rejected source records. This preserves `KNOWN` versus `UNKNOWN` without serializing an opaque population blob.

The application imports the checked-in fixture on startup and inserts it only when that ID/version is absent. It then reloads the persisted population for analysis. Analyses are calculated on demand; baseline and scenario results are not stored. PostgreSQL 17 round-trip tests verify the reference fixture’s population semantics and workload metrics.

Flyway V2 adds exact verified ODIP manifest/artifact bytes keyed by dataset ID
and publication version. Import is atomic, changed bytes for an existing
version are rejected, and cache reads reverify the publication. V3 adds owned
optimization runs, principal-scoped idempotency, queue claims, terminal result
or error JSON, and ordered candidate rows. A single scheduled worker claims
queued runs and commits each terminal result and its candidates atomically.
See the [database model](system-model/database-model.md) for tables and invariants
and the [runtime model](system-model/runtime-model.md) for execution and recovery.

The UI and API are packaged in one container image. The Developer Platform supplies
database credentials through an explicit named PostgreSQL storage binding, HTTP
routing, and workload operation. Both descriptors request 256 MiB memory and limit
it to 512 MiB. Production CI renders a versioned image from a Git tag for `vareng`;
main deploys `latest` to `vareng-test`. Application bootstrap provisions storage,
saves its binding ID in the descriptor, and deploys `latest`. The
[deployment guide](../05-operations/deployment.md) describes image, application,
ODIP access, and browser-policy bootstrap. Domain objects do not contain
infrastructure configuration.

## Runtime constraints and verification targets

- One application replica.
- Bounded enumeration with understandable termination behavior.
- Startup recovery requeues interrupted runs; deployed restart and backup/restore evidence remain open.
- Runtime and peak-memory measurements for representative fixtures remain acceptance work.

Immutable IDs, versions, and artifact provenance are implemented for food-service
populations and energy publications; runs retain pinned publication digests and
calculation versions. General-purpose generative-model storage, dedicated model
snapshots and security-audit tables, retention/purge operations, multi-replica
recovery, and actual backup restoration/reproduction remain open. Additional data
stores require a defined workload and reproducible comparison.
