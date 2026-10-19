# ADR-004 – PostgreSQL Persistence Baseline

Status: **Implemented for food-service populations, verified energy publications,
and durable owned energy runs** (status updated October 19, 2026).

## Context

The prototype must save and reload models and should use an existing Developer Platform service.

## Decision

Use PostgreSQL for the first application persistence implementation.

## Consequences

The MVP uses normalized relational tables for immutable populations, provenance, dimensions, records, element usages, dimension values, and rejected rows. Analyses are calculated on demand. Other stores require a concrete workload and comparison.

## Validation

Flyway applies the schema to PostgreSQL 17, and an automated fixture round trip preserves population, provenance, dimensions, records, rejected rows, and reference metrics.

Implementation update, October 19, 2026: V2 stores verified publication bytes;
V3 stores owned runs, principal-scoped idempotency, results, and candidate pages.
PostgreSQL tests cover publication and run round trips. This extends the original
food-service implementation under the same PostgreSQL decision; see the
[database model](../02-architecture/system-model/database-model.md). Actual
deployed backup restoration, retention, and complete recovery evidence remain open.
