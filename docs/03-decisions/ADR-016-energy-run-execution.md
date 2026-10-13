# ADR-016: Durable energy run execution

Status: Accepted for the deterministic new-equipment slice (2026-10-13)

## Context

The pinned `contract-kit-v0.4.0` defines owned optimization runs, bounded
execution, immutable results, candidate pages, and idempotent submission. The
energy calculation and verified ODIP publication cache existed without a
caller-facing workflow.

## Decision

Store each request, stable principal ID, canonical request hash, publication
digest, lifecycle state, terminal result, and candidate page rows in PostgreSQL.
The pair `(principal_id, idempotency_key)` is unique. Changed content with the
same key returns `409`; a repeat returns the original run ID in the OpenAPI's
`202` acceptance envelope. A platform-supplied, complete normalized identity
context is required for every energy route. Group IDs are validated as context
shape but never used for application role decisions. The platform must strip
client-supplied identity headers and isolate the backend.

A single scheduled worker claims queued rows with `FOR UPDATE SKIP LOCKED`.
Only the matching claim token can commit a terminal result and all candidate
rows. The worker uses the request's exact verified publication digest and
current pinned calculation versions. It times out after 300 seconds and
cooperatively cancels the simulation. On startup, the declared one-replica
service requeues interrupted runs; an old claim cannot overwrite a new one.

The importer attempts a fresh ODIP read. For transport outages, HTTP 429, or
server errors, it may reuse locally stored bytes only after reverification.
HTTP 4xx denials, including 401 and 403, never fall back. Results and candidate
rows remain immutable after completion.

## Consequences

- The deterministic reference workflow is available through the pinned API and
  lazy-loaded `/energy` UI after the platform route policy permits it.
- Multi-replica execution needs lease-based stale claim recovery before
  increasing `replicas` above one.
- Dedicated owned evidence and security-audit projections, retention, deployed
  ODIP access tests, and platform policy validation remain separate acceptance
  work. Existing-equipment semantics remain unsupported by this model version.
