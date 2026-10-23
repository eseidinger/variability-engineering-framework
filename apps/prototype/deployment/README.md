# Prototype Deployment Assembly

This directory assembles the independently built UI and API into one container image. Quarkus serves the compiled Angular files at `/` and the API at `/api` on the same port.

The application requires PostgreSQL. Build and run the combined image with the
repository Compose environment:

```shell
docker compose --profile app up --build
```

Open <http://localhost:8080>. The UI calls the APIs on the same origin; the Angular development proxy is not involved in the container. The `/energy` workflow requires a platform browser session with normalized principal headers and an ODIP managed reader binding. The standalone Compose setup provides neither, so it supports the food-service workflow and API packaging checks only.

For API and UI hot-reload development, start only the database:

```shell
# Start only PostgreSQL for API and UI hot-reload development.
docker compose up -d db

# Remove the development database volume when a clean seed is needed.
docker compose down -v
```

The `db` service exposes PostgreSQL on `localhost:5432` with development-only credentials (`variability`/`variability`). The `app` profile waits for its health check and receives its datasource settings through Compose.

The multi-stage build:

1. installs the locked UI dependencies and builds Angular;
2. copies the Angular browser output into Quarkus' `META-INF/resources` directory;
3. packages the Quarkus application; and
4. copies only the Quarkus runtime into the final non-root image.

Deployment configuration:

- preserve the UI/API project boundary;
- run with the platform-required non-root identity;
- expose HTTP port 8080;
- obtain PostgreSQL settings through an approved named-store binding on `web`;
- include the immutable food-service fixture for first-start database seeding;
- expose `/api/status` readiness that verifies the persisted population and energy run store are available.

## GitHub Actions and Docker Hub

The [build and publish workflow](../../../.github/workflows/build-test-publish.yml) runs the UI tests and build, API tests, and Compose black-box verification for every pull request and push. It publishes the combined image only for `main` and version tags beginning with `v`.

Before enabling publishing, add these GitHub Actions repository secrets:

- `DOCKERHUB_USERNAME`: the Docker Hub namespace that will own `variability-engineering-framework`.
- `DOCKERHUB_TOKEN`: a Docker Hub access token with permission to push that repository.

The resulting image names are `<username>/variability-engineering-framework:latest` for `main`, semantic-version tags for releases such as `v1.0.0`, and immutable `sha-...` tags.

Deployment uses `developer-platform-cli==0.3.0` with Python 3.12 or newer (latest
published CLI checked October 23, 2026). The
[bootstrap prerequisites](../../../docs/05-operations/deployment.md#bootstrap-scripts)
cover upgrading the CLI, checking its version and contract, and authenticating an
administrator profile, including environment overrides and keyring requirements.
Production CI renders only the
image tag from the Git release tag; test CI deploys `application-test.json`
directly with `latest`. Both use the image repository, PostgreSQL binding UUID,
and ODIP binding prefix saved in the descriptors. Commit bootstrap descriptor
updates before deploying through CI. Named stores must already exist with active
bindings for component `web`; application deployment no longer creates a database
through `spec.resources`. See the [deployment guide](../../../docs/05-operations/deployment.md)
for rendering and setup.

The [bootstrap scripts](../../../docs/05-operations/deployment.md#bootstrap-scripts)
provision PostgreSQL, update the descriptor's storage binding in place, and deploy
`latest` in either environment. Access bootstrap requests ODIP access and writes
its binding prefix into the descriptor in place. `bootstrap-policy.py` initializes
the browser application policy and user group. The credential script captures CI
credentials: `bootstrap-application.py`, `bootstrap-access.py`, `bootstrap-policy.py`, and
`create-deployment-credential.py` under `scripts/`. Run each with `--help` for options.

To deploy published `latest` images to the hosted test project from your machine,
run `python3 scripts/bootstrap-application.py --environment test --profile lab --deploy-only`.
This reuses the saved bindings, waits for readiness, and preserves descriptors.
For first-time provisioning, omit `--deploy-only`. See the
[local-shell test deployment instructions](../../../docs/05-operations/deployment.md#deploy-latest-to-the-test-platform-from-a-local-shell)
for prerequisites and optional local image publishing.

For deployment to the local Developer Platform, use `--environment test-local` with
[`application-test-local.json`](../../../application-test-local.json). See the
[test-local setup](../../../docs/05-operations/deployment.md#separate-test-local-descriptor)
for the localhost endpoints, required local CA bundle, and provisioning its own
bindings and access to the local `odip-test` provider.

To publish the initial `latest` image locally, run `docker login` followed by
`python3 scripts/bootstrap-image.py`. It builds the combined UI/API Dockerfile
and pushes to the image repository in `application.json`, preserving the
descriptor. `--file application-test.json` selects the test descriptor's repository.

## Enabling energy AI in the hosted application

The production profile does not activate the development-only fixture importer,
and the Angular identity proxy is not part of the built UI. The hosted workflow
needs the active ODIP reader binding and dataset grant, plus the platform's
browser-session identity and CSRF boundary.
Before enabling AI, provision `OPENAI_API_KEY` as a managed secret in each
project (`vareng` and `vareng-test`), injected into the `web` container. Keep the
key out of both deployment descriptors.
The declaration includes HTTPS egress to `api.openai.com`; the platform operator
must approve its resolved address pins.

Inspect the active route policy with
`devplat access policy get --project vareng --application vareng --output json`.
Extend that complete policy with browser-session, application-user rules for
the exact `POST /api/v1/ai/request-proposals` path and a POST-only
`/api/v1/ai/optimization-runs/` path prefix for run explanations. The API
validates the full explanation path and run ownership. Include the platform's
same-origin and CSRF checks. Preserve existing run, capabilities, UI, asset,
and health routes. `devplat access policy apply` replaces the entire policy;
use the current ETag and review the full resulting policy before applying it.

After the secret, egress, and route policy are active, set the non-secret
`ENERGY_AI_ENABLED` deployment value to `true`. `ENERGY_AI_MODEL` and
`ENERGY_AI_TIMEOUT` may be set explicitly; the application defaults are
`gpt-6.1-sol` and `180s`. A push to `main` runs CI, publishes `latest`, and deploys
[`application-test.json`](../../../application-test.json) to `vareng-test` with
`PLATFORM_CLIENT_ID_TEST` and `PLATFORM_CLIENT_SECRET_TEST`. A `v*` release tag
deploys [`application.json`](../../../application.json) to `vareng` using the
versioned image and the unsuffixed deployment credentials. Each project needs
its own secret, egress approval, route policy, and ODIP reader binding. Verify an authenticated proposal, confirmation,
ODIP-backed run, and evidence-cited explanation on the hosted route. Run the
released AI acceptance cases and unchanged deployed Gate 5 regression before
claiming Gate 6 release acceptance.
