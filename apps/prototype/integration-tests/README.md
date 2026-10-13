# Prototype Integration Tests

The Compose-backed black-box verifier exercises the packaged UI shell and selected
food-service API behavior against PostgreSQL and the reference fixture.
It calls the application directly without platform identity headers. The
Developer Platform access boundary is verified separately after deployment.

Current coverage includes:

- API startup and population availability;
- the unfiltered population summary and record count;
- a vegan filter query and garlic-unavailability scenario;
- persistence after workload recreation; and
- delivery of the packaged Angular shell.

The verifier does not render the Angular application in a browser and does not cover
generative-model validation or configuration counts.

Run the Compose-backed black-box verification from the repository root:

```shell
apps/prototype/integration-tests/verify-compose.sh
```

The script builds the combined image, starts PostgreSQL and the packaged application,
verifies the served UI shell and fixture-backed API queries, restarts the application
to verify persistence, reports the application cgroup peak-memory value when available,
and tears down its temporary database volume. It requires Docker Compose and curl. By
default it uses host port `18080`; set `APP_PORT` to choose another available port.
