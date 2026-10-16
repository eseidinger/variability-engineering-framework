# Prototype Deployment

Status: the earlier food-service revision `2ae2a171a93c52fc57f9b4577398ab43d6a8550e`
was built, published, and deployed by the
[October 12, 2026 GitHub Actions run](https://github.com/eseidinger/variability-engineering-framework/actions/runs/38292671777).
The protected public status route returned HTTP `302` on October 13, 2026.
The current energy code and ODIP binding configuration have not yet been deployed
or verified end to end.

The prototype deploys through the existing Developer Platform as one container
image containing the UI and API. PostgreSQL is provided separately by the platform.

## Developer Platform declaration and CI

[`application.json`](../../application.json) declares the public `web` service in
the `vareng` project. With the standard platform domain convention, the public
endpoint is `https://web-vareng.apps.eseidinger.de`.

The build workflow publishes the image, deploys the corresponding immutable
`sha-<commit>` Docker Hub tag, and checks that the public entry point is protected.
The platform deployment wait verifies workload readiness. Reading the protected
population requires an approved browser session or service credential; an
anonymous deployment job must not attempt to supply platform identity headers.
The food-service API does not perform group or role checks. The public access
boundary must protect its routes and prevent direct backend access. Local Compose
tests call the API directly and do not test that boundary.
Production deployments are serialized. Before enabling deployment, configure the
protected GitHub `production` environment with:

- repository variable `PLATFORM_API_URL`;
- repository variable `PLATFORM_TOKEN_ENDPOINT`;
- environment secrets `PLATFORM_CLIENT_ID` and `PLATFORM_CLIENT_SECRET`; and
- repository secrets `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`, as described in
  the [deployment assembly](../../apps/prototype/deployment/README.md#github-actions-and-docker-hub).

The platform injects PostgreSQL's `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, and
`PGPASSWORD` values. The production Quarkus configuration maps them to the
datasource; no database credential is committed to the repository.

The `web` component opts in to platform application metrics and tracing. Quarkus
serves Prometheus HTTP request metrics at `/q/metrics` on management port 9000;
the platform scrapes that declared port privately. Public traffic stays on port
8080. The descriptor opts in to platform tracing without setting a reserved `OTEL_`
configuration value. Quarkus enables its OpenTelemetry SDK in production, and the
platform injects the local OTLP/HTTP endpoint, service name, and trace sampling
rate. Its Pod-local forwarder reads the rotating telemetry token for each export
batch. Development, tests, and repository Compose runs disable trace export;
other standalone production runs need a reachable OTLP collector. After
deployment, verify the platform's application metrics view and a sampled trace
before treating telemetry as operationally confirmed.

## Local image

Build and run the combined image with its required PostgreSQL service from the
repository root:

```shell
docker compose --profile app up --build
```

Quarkus serves the compiled Angular UI at <http://localhost:8080> and the API below `/api`. The image runs as a non-root user.

## Application contract

- Run as the non-root identity required by the Developer Platform.
- Listen on the configured unprivileged HTTP port.
- Obtain PostgreSQL connection parameters through the platform contract.
- Keep domain models independent of deployment configuration.
- Run as one application replica until multi-replica behavior is explicitly verified.
- Bound enumeration and report rejected work clearly.

## Deployment acceptance

1. Build a revision-identifiable image.
2. Deploy it through the Developer Platform.
3. Verify the intended HTTP entry point is protected and that platform deployment
   reports the workload ready. Use an approved browser or service identity to
   inspect the persisted population.
4. Save and reload the reference population through PostgreSQL.
5. Recreate the workload and verify persisted data.
6. Run the reference analysis and compare expected results.
7. Record runtime, peak memory, revision, environment, and limitations.

Backup, restore, schema migration, rollback, and incident procedures must be added after their application behavior is implemented and tested. Platform-level monitoring or backup capabilities do not by themselves prove recovery of this application.
