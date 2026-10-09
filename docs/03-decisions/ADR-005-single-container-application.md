# ADR-005 – UI and API in a Single Container Image

Status: **Implemented locally; platform verification pending**.

## Context

The prototype needs one public HTTP origin and does not require independent UI and API scaling or release cycles. The Developer Platform supports a service component and a separately managed PostgreSQL resource for this shape.

## Decision

Package the first prototype UI and API in one image.

## Consequences

UI and API are not independently deployed in the prototype. PostgreSQL remains a separate platform service.

## Validation

Deploy the image through the platform and verify its HTTP entry point, database connection, and readiness behavior.
