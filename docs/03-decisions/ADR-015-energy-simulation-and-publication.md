# ADR-015 – Verified Energy Inputs and Deterministic Evaluation

Status: **Accepted for the local reference slice** on October 13, 2026.
Implementation and test evidence are in the [calculation verification record](../04-development/energy-calculation-verification.md).

## Context

The released contract kit fixes hourly dispatch, economics, rounding, and ranking.
An energy recommendation depends on five mutually consistent ODIP artifacts and
must remain reproducible after a publication is fetched or the service restarts.
The existing food-service population tables and binary variant identity have
different semantics.

## Decision

Keep the energy feature model, profile, simulation, and exhaustive optimizer as
pure Java types. A separate ODIP adapter retrieves one exact immutable dataset
version through a configured HTTPS origin and a managed client-credentials
binding. It rejects redirects, unsafe artifact IDs/paths, excessive bytes,
missing/duplicate roles, digest mismatches, non-published or failed-quality
manifests, invalid catalog attributes, and misaligned or invalid hourly series.
No artifact becomes usable until the complete publication passes verification.

Persist the original manifest and artifact bytes in PostgreSQL with a unique
dataset ID/version key. Insert them in one transaction, reject changed bytes
under an existing version, and reverify every loaded entry. ETags may reduce
network transfer but never replace digest and semantic verification. A future
application service may use a verified cached version during temporary ODIP
failure after enforcing current access policy; the client and repository do not
make that authorization decision.

The simulator starts battery state of charge at zero. It applies inverter
efficiency before AC clipping, serves direct load, charges only from surplus,
discharges only to residual load, and accounts for grid import/export and
terminal discarded battery energy. It checks hourly conservation and capacity.
The optimizer evaluates all structurally valid candidates against the same
profile, applies investment and self-sufficiency constraints, and ranks feasible
candidates by annualized cost cents, investment cents, and configuration ID.
Binary64 calculations remain unrounded until the output boundary; serialization
uses half-even rounding at the contract's precision.

## Consequences and limits

This ADR established the deterministic calculation and verified input layer.
[ADR-016](ADR-016-energy-run-execution.md) subsequently added the local run
workflow and verified-cache outage fallback. Deployed ODIP access remains open. The verifier
enforces the fields and cross-document invariants used by this profile; full
generic JSON Schema validation remains part of the publication adapter's exit
gate. Existing-equipment requests remain explicitly unsupported under ADR-014.

## Validation

The Java simulator matches every released hourly case. The optimizer matches
all 252 candidate metrics and statuses, the complete 194-candidate feasible
ranking, and the recommendation. Negative tests cover tampering, missing
artifacts/hours, unsafe paths, wrong units/status, invalid series, and byte
limits. A PostgreSQL round trip preserves raw bytes and rejects immutable
version changes. The full API suite passes against a disposable PostgreSQL 17
instance; deployment behavior remains unverified.
