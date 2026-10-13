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
- obtain PostgreSQL settings from the platform environment;
- include the immutable food-service fixture for first-start database seeding;
- expose `/api/status` readiness that verifies the persisted population and energy run store are available.

## GitHub Actions and Docker Hub

The [build and publish workflow](../../../.github/workflows/build-test-publish.yml) runs the UI tests and build, API tests, and Compose black-box verification for every pull request and push. It publishes the combined image only for `main` and version tags beginning with `v`.

Before enabling publishing, add these GitHub Actions repository secrets:

- `DOCKERHUB_USERNAME`: the Docker Hub namespace that will own `variability-engineering-framework`.
- `DOCKERHUB_TOKEN`: a Docker Hub access token with permission to push that repository.

The resulting image names are `<username>/variability-engineering-framework:latest` for `main`, semantic-version tags for releases such as `v1.0.0`, and immutable `sha-...` tags.
