# Prototype Deployment

Status: the earlier food-service revision `2ae2a171a93c52fc57f9b4577398ab43d6a8550e`
was built, published, and deployed by the
[October 12, 2026 GitHub Actions run](https://github.com/eseidinger/variability-engineering-framework/actions/runs/38292671777).
The protected public status route returned HTTP `302` on October 13, 2026.
The current energy code and ODIP binding configuration have not yet been deployed
or verified end to end.

The prototype deploys through the existing Developer Platform as one container
image containing the UI and API. PostgreSQL is an independently managed named
store, attached through an approved component binding.

## Developer Platform declaration and CI

[`application.json`](../../application.json) declares the public `web` service in
the `vareng` project. With the standard platform domain convention, the public
endpoint is `https://web-vareng.apps.eseidinger.de`. Its saved image version may be concrete or use the
`${VARENG_IMAGE_TAG}` placeholder. [`application-test.json`](../../application-test.json)
declares the equivalent service in `vareng-test` using `latest`, at
`https://web-vareng-test.apps.eseidinger.de`.

The build workflow publishes the image and deploys main pushes to `vareng-test`
using `latest`. Release tags such as `v1.0.0` deploy to `vareng` using the
corresponding versioned image tag (`1.0.0`). CI renders the release version into
the production descriptor with [`scripts/render-application.py`](../../scripts/render-application.py)
before deploying, changing only the image tag. Test CI deploys
`application-test.json` directly. Both descriptors retain the storage binding IDs
and ODIP prefixes saved by bootstrap; commit those updates before running CI.
Automatic `latest` tagging for
releases is disabled so it continues to track main. Both deployments check that
their public entry point is protected.

Render the production descriptor locally with a release tag (the leading `v`
is removed from the image version):

```shell
python3 scripts/render-application.py --tag v1.2.3 > /tmp/deployment-application.json
```

The renderer also accepts prerelease tags such as `v1.2.3-rc.1` and rejects
invalid release tags. It preserves image repositories, storage binding IDs,
ODIP settings, and every other descriptor value. The source file is unchanged.

The platform deployment wait verifies workload readiness. Reading the protected
population requires an approved browser session or service credential; an
anonymous deployment job must not attempt to supply platform identity headers.
The food-service API does not perform group or role checks. The public access
boundary must protect its routes and prevent direct backend access. Local Compose
tests call the API directly and do not test that boundary.
Deployments are serialized separately for each project. Before enabling deployment, configure the
protected GitHub `production` environment with:

- repository variable `PLATFORM_API_URL`;
- repository variable `PLATFORM_TOKEN_ENDPOINT`;
- environment secrets `PLATFORM_CLIENT_ID` and `PLATFORM_CLIENT_SECRET`; and
- repository secrets `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`, as described in
  the [deployment assembly](../../apps/prototype/deployment/README.md#github-actions-and-docker-hub).

For test deployments, configure the GitHub `test` environment with
`PLATFORM_CLIENT_ID_TEST` and `PLATFORM_CLIENT_SECRET_TEST` secrets. Both
environments use the same `PLATFORM_API_URL` and `PLATFORM_TOKEN_ENDPOINT`
repository variables. Provision database resources, access policies, managed
secrets, and ODIP bindings for each project.

CI pins `developer-platform-cli==0.3.0`. Before deployment, a resource owner or
project administrator must create the named PostgreSQL store and request a
read-write binding for the exact project and `web` component. Cross-project
bindings require resource-owner approval. Wait for the store and binding to be
active, then save the returned binding UUID in the consuming component descriptor. CI
deployment credentials cannot provision or approve these bindings. The platform
rejects the former `spec.resources` / `shared-dev` declaration and no longer
provides an implicit project database. For existing data, arrange migration into
the named store before switching the binding.

With an explicit binding and empty `envPrefix`, the platform injects PostgreSQL's
`PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, and
`PGPASSWORD` values. The production Quarkus configuration maps them to the
datasource; no database credential is committed to the repository.

## Bootstrap scripts

Install the pinned CLI and authenticate with a human administrator profile:

```shell
python3 -m pip install developer-platform-cli==0.3.0
devplat profiles create lab --api-url https://platform.example \
  --issuer https://identity.example/realms/platform
devplat --profile lab auth login
```

Use your actual platform URL and issuer. If the profile already exists, reuse it.
Clear environment authentication variables when using a human profile, as
described by the platform CLI. Project creation requires platform administrator
authority; subsequent provisioning can use a project administrator. CI deployment
credentials cannot bootstrap stores or access policies.

Bootstrap each environment with an initial deployment:

```shell
docker login
python3 scripts/bootstrap-image.py
python3 scripts/bootstrap-application.py --environment test --profile lab
python3 scripts/bootstrap-application.py --environment production --profile lab
```

`bootstrap-image.py` builds the combined UI/API image using
`apps/prototype/deployment/Dockerfile` and the repository root as its build
context, then pushes `latest` to the web image repository in `application.json`.
It uses the existing Docker login and prints the published image reference on
success. Build/push logs go to stderr. A failed build stops before pushing.
The descriptor is preserved and no deployment is performed. Use
`--file application-test.json` to select the image repository from the test
descriptor instead. Docker must be running with push access to that repository.

These commands create missing projects (`vareng-test` and `vareng`), create a
same-project PostgreSQL store named `primary` with a 1 GiB quota, request a
read-write binding for component `web`, and wait for both observed states to
become active. Bootstrap then updates the storage binding ID in the selected
descriptor **in place** (`application.json` for production,
`application-test.json` for test). All other descriptor values are preserved.

Both environments always deploy the image repository from their descriptor with
the `latest` tag, regardless of the saved image version or digest. This override
exists only in the deployment payload; the image reference in the saved descriptor
is preserved. Bootstrap waits for application readiness. The `latest` image must
already have been published. No release tag is required, and bootstrap has no
`--tag` or `--image` options. CI release deployments continue using versioned images.

Reruns reuse the existing store and binding and preserve existing quotas.
Use `--resource-name` to select an existing same-project PostgreSQL store,
or `--quota-bytes` to size a new one. Retired projects/stores, incompatible
bindings, provisioning failures and timeouts stop the script. Concurrent bootstrap
runs for the same project are unsupported; after an interrupted request, inspect
platform state and rerun rather than deleting resources.

On success, stdout contains only the PostgreSQL binding UUID; progress goes to
stderr. Bootstrap saves it in the descriptor; commit that change for CI.
You can also capture the result locally:

```shell
POSTGRES_BINDING_ID_TEST=$(python3 scripts/bootstrap-application.py --environment test --profile lab)
```

Bootstrap sends the deployment payload to `devplat deploy apply` through stdin.
The descriptor's binding ID is saved before deployment and remains available if
the deployment fails. No additional deployment or CI-variable files are written.
Bootstrap does not request ODIP access, configure browser access, or create CI
credentials; use the separate scripts below. Existing data is never copied from
a legacy database.

The test descriptor uses `https://web-odip-test.apps.eseidinger.de` and declares
HTTPS egress to that host; production uses `https://web-odip.apps.eseidinger.de`.
The platform operator must approve the declared HTTPS egress destinations.
ODIP access still requires provider approval and a dataset grant. The standalone
access script reports the request ID, state, and binding prefix, and writes the
prefix into `spec.configuration.values.ENERGY_ODIP_BINDING_PREFIX` in the selected
descriptor in place. All other descriptor values are preserved. Before approval,
the prefix is derived from the platform's request-ID convention and credentials
are not yet usable. Commit the descriptor update so CI uses the saved prefix.

Set up ODIP access and update the descriptor for an existing project:

```shell
python3 scripts/bootstrap-access.py --environment test --profile lab
```

ODIP setup is the default; `--odip` selects it explicitly. The script selects
`odip-test` for test and `odip` for production. It updates `application-test.json`
or `application.json` respectively, without deploying. Rerun application bootstrap
after access setup to deploy the updated prefix using `latest`. The request is
reused on subsequent runs. `--browser` alone performs only browser policy setup;
use `--browser --odip` for both.
`--provider-project` and `--provider-application` override those defaults for
standalone access setup.

After deploying the public `web` component, initialize browser access:

```shell
python3 scripts/bootstrap-access.py --environment test --profile lab --browser
```

This creates an `application-users` group and an initial browser-session policy
covering GET routes and the application's food analysis, energy run and AI POST
routes. The platform enforces its browser session and CSRF checks. Existing active
policies are preserved; inspect and extend them separately if routes are missing.
Users must still complete first login and receive administrator approval/group
membership. Repeat the access commands with `--environment production` for `vareng`.

The descriptors enable AI. Provision its key using the platform secret command
before using AI routes; the value file must be readable only by its owner:

```shell
devplat --profile lab secrets set OPENAI_API_KEY --project vareng-test \
  --value-file /private/path/openai-api-key
```

Create CI credentials separately, saving each one-time response to a new private
file (mode `0600`):

```shell
mkdir -p .local/deployment/vareng-test .local/deployment/vareng
python3 scripts/create-deployment-credential.py --environment test --profile lab \
  --output-file .local/deployment/vareng-test/ci-credential.json
python3 scripts/create-deployment-credential.py --environment production --profile lab \
  --output-file .local/deployment/vareng/ci-credential.json
```

The scripts never print credential values or overwrite an existing file. Transfer
`client_id` and `client_secret` into `PLATFORM_CLIENT_ID_TEST` and
`PLATFORM_CLIENT_SECRET_TEST` for test, or the unsuffixed secrets for production.
Use `token_endpoint` for the shared `PLATFORM_TOKEN_ENDPOINT` variable. The default
credential lifetime is 90 days; `--expires-in-days` accepts 1–90. Rotate credentials
before expiry through the platform CLI. Credential creation is intentionally a
separate operation each time: inspect an uncertain/failed creation before retrying,
and do not remove a saved credential file until its secret is stored securely.

All bootstrap scripts support `--help`. They invoke the installed `devplat` and
do not install software, change GitHub settings or perform provider approvals.

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
