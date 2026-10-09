# Persistence and Deployment Architecture

Status: implemented food-service MVP persistence; production deployment remains open.

The food-service MVP uses PostgreSQL with a normalized relational schema, applied by Flyway at startup. `analysis_population` is keyed by immutable population ID and version. Related tables store artifact provenance, dimension definitions, records, canonical element usages, dimension states and values, and rejected source records. This preserves `KNOWN` versus `UNKNOWN` without serializing an opaque population blob.

The application imports the checked-in fixture on startup and inserts it only when that ID/version is absent. It then reloads the persisted population for analysis. Analyses are calculated on demand; baseline and scenario results are not stored. PostgreSQL 17 round-trip tests verify the reference fixture’s population semantics and workload metrics.

The UI and API are packaged in one container image. The Developer Platform supplies database credentials, HTTP routing, and workload operation. Domain objects do not contain infrastructure configuration.

## Initial constraints

- One application replica.
- Bounded enumeration with understandable termination behavior.
- Persistence verified after workload recreation.
- Runtime and peak memory recorded for representative fixtures.

Immutable IDs, versions, and artifact provenance are implemented for the food-service
fixture population. Generative-model identity, external-dataset lifecycle, concurrent
changes, migrations beyond the initial schema, and backup and recovery procedures
remain open. Additional data stores require a defined workload and reproducible comparison.
