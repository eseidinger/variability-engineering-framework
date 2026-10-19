# ADR-005 – UI and API in a Single Container Image

Status: **Implemented; production energy journey owner-confirmed October 19, 2026**.

## Context

The prototype needs one public HTTP origin and does not require independent UI and API scaling or release cycles. The Developer Platform supports a service component and a separately managed PostgreSQL resource for this shape.

## Decision

Package the first prototype UI and API in one image.

## Consequences

UI and API are not independently deployed in the prototype. PostgreSQL remains a separate platform service.

## Validation

Deploy the image through the platform and verify its HTTP entry point, database connection, and readiness behavior.

Verification update, October 19, 2026: CI deployment and the owner-confirmed
production ODIP-backed energy journey, including live AI assistance, are recorded
in the [energy verification record](../04-development/energy-contract-kit-verification.md).
The combined deployment is operational by that evidence. Full deployed
security, regression, recovery, resource, and observability acceptance remains open.
