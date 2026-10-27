# Prototype Deployment

Status: on October 19, 2026, the owner confirmed that AI explanations and the
full deployed energy integration flow work end to end. This records an
owner-verified operational workflow following release `v0.5.1`. The follow-up
production walkthrough records run IDs and acceptance checks. Test-deployment
digests and guided outage/restart checks are recorded through October 26, most
recently revision 21; a complete final-release inventory and deployed suite remain
open. See the [verification record](../04-development/energy-contract-kit-verification.md).
These are dated observations, not a fresh runtime inspection.

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

Use Python 3.12 or newer. The latest published CLI checked on October 23, 2026
is `0.3.0`; local bootstrap and CI use the same pinned version. Install or upgrade
it, then verify the installed version and packaged API command coverage:

```shell
python3 -m pip install --upgrade developer-platform-cli==0.3.0
devplat --version
devplat contract check --output json
```

The version command should print `0.3.0`. The contract check validates the CLI's
packaged command registry; it does not verify the deployed platform or your access.

Authenticate with a human administrator profile. Environment authentication takes
precedence over the profile session, so clear these variables in the bootstrap
shell before signing in:

```shell
unset PLATFORM_ACCESS_TOKEN PLATFORM_TOKEN_ENDPOINT PLATFORM_CLIENT_ID PLATFORM_CLIENT_SECRET
devplat profiles create lab --api-url https://platform.example \
  --issuer https://identity.example/realms/platform
devplat --profile lab auth login
devplat --profile lab auth status --output json
```

Use your actual platform URL and issuer. If the profile already exists, reuse it.
Inspect it with `devplat profiles show --name lab --output json`. The default
OIDC public client is `platform-cli`; profiles contain connection settings, not
administrator grants. `PLATFORM_API_URL` and `PLATFORM_CA_BUNDLE` can override
profile settings; clear unintended overrides or set them for your platform.

Login uses OIDC Device Authorization with PKCE: follow the printed verification
URL and user code. Renewable sessions require a secure OS keyring. If one is
unavailable, use an externally obtained short-lived administrator token through
`PLATFORM_ACCESS_TOKEN`. Check the selected authentication source with `auth status`
before provisioning. Project creation requires platform administrator
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
reused on subsequent runs. For compatibility, `--browser` alone performs only
browser policy setup and `--browser --odip` performs both. New setups can use
the dedicated policy script below.
`--provider-project` and `--provider-application` override those defaults for
standalone access setup.

After deploying the public `web` component, initialize browser access:

```shell
python3 scripts/bootstrap-policy.py --environment test --profile lab
python3 scripts/bootstrap-policy.py --environment production --profile lab
```

`bootstrap-policy.py` creates or reuses an `application-users` group and applies
an initial browser-session policy, waiting for activation. Its JSON output contains
the project and resulting application policy. Use `--group NAME` to choose another
group. The policy defaults to deny and requires membership in that group,
covering GET routes and the application's food analysis, energy run and AI POST
routes. The platform enforces its browser session and CSRF checks. Existing active
policies are preserved; inspect and extend them separately if routes are missing.
Users must still complete first login and receive administrator approval/group
membership. Run this script after deploying the public `web` component; it does
not deploy the application, change descriptors, or request ODIP access.

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

VEF explicitly exports HTTP/protobuf and defaults root sampling to 100% when
`OTEL_TRACES_SAMPLER_ARG` is absent, while respecting the parent sampling decision.
LangChain4j emits AI-service and completion spans without capturing prompt or
completion payloads. The current platform gateway stores application spans under
the generic name `application.span` and strips AI-specific attributes. Inspect the
request trace using its `X-Platform-Trace-Id` response header rather than searching
for AI span names. Dedicated AI trace names/attributes require a platform collector
allowlist change. See the [API tracing notes](../../apps/prototype/api/README.md).

## Deploy latest to the test platform from a local shell

Use the authenticated administrator profile from the bootstrap instructions above.
The target is the hosted Developer Platform project `vareng-test`, with public URL
<https://web-vareng-test.apps.eseidinger.de>. Run from the repository root:

```shell
python3 scripts/bootstrap-application.py --environment test --profile lab --deploy-only
```

This reads `application-test.json`, deploys every component's image repository
with `:latest`, and waits for workload readiness. Saved image tags or digests are
overridden only in the submitted payload. The platform resolves the published
tags to immutable image digests when accepting the deployment. Rerun the command
after a newer `latest` image is published to deploy it.

Deploy-only reuses the saved PostgreSQL binding and ODIP prefix without changing
either descriptor, creating stores or requesting access. It can also use an
existing project-scoped CI deployment credential through the environment instead
of `--profile lab`; do not mix it with a human profile session. Stdout contains the
saved PostgreSQL binding UUID on success, while CLI progress goes to stderr.
`--timeout 600` and `--poll-interval 5` control deployment waiting. A timeout does
not cancel work already accepted by the platform; inspect project status before
retrying.

For a new test project, first run without `--deploy-only` to provision PostgreSQL
and save its binding. Set up ODIP access, browser policy, managed AI secrets, and
provider approvals as described above. Commit the saved binding and prefix for CI.
The local deployment command relies on these existing resources and permissions.

CI publishes `latest` from main. To build and publish your local checkout instead,
authenticate to Docker Hub and run:

```shell
docker login
python3 scripts/bootstrap-image.py --file application-test.json
python3 scripts/bootstrap-application.py --environment test --profile lab --deploy-only
```

Publishing replaces the shared repository's `latest` tag. It does not deploy
production; production CI continues to deploy versioned release images. Local
deployment requires no GitHub Actions run or release tag. After readiness,
verify the test URL using an approved browser session; readiness alone does not
verify ODIP access or the complete energy workflow.

## Separate test-local descriptor

[`application-test-local.json`](../../application-test-local.json) targets project
`vareng-test` on the local Developer Platform. It uses the published
`:latest` application image, its own PostgreSQL binding and browser policy, and
the local `odip-test` provider. CI continues to deploy main to the remote
`vareng-test` project and releases to `vareng`; it does not deploy `test-local`.
Local and remote test deployments share the application/project name but use
separate platform instances, descriptors, and bindings. Use `--profile local`
with `--environment test-local` to target the local platform.

### Prerequisites and addresses

Use Python 3.12 or newer and a running local Developer Platform with local
application HTTPS enabled (`LOCAL_APPLICATION_HTTPS_ENABLED=true`). The local
`odip-test` provider must be deployed. These instructions use the default local
platform domains:

| Service | URL |
|---|---|
| Platform API | `https://platform.localhost` |
| OIDC issuer | `https://identity.localhost/realms/platform` |
| ODIP provider | `https://web-odip-test.apps.localhost` |
| VEF application | `https://web-vareng-test.apps.localhost` |

The descriptor already declares the local ODIP origin and outbound HTTPS to
`web-odip-test.apps.localhost`, `identity.localhost`, and `api.openai.com`.
Names must resolve to loopback on the host. Browser clients must trust the local
platform CA. Inside workloads, local application HTTPS supplies private routing
and a public CA bundle through `SSL_CERT_FILE`. The ODIP publication and managed
token clients load that bundle alongside the JVM's existing trusted roots.
Certificate and hostname verification remain enabled. Deploy an image containing
this support; host CLI trust alone does not configure TLS in an older container.

### CLI installation, CA trust, and login

Run from this repository's root. The sibling platform checkout is assumed to be
`$HOME/dev/developer-platform`; adjust the certificate path if it is elsewhere.
Platform setup exports the public certificate to `.runtime/trust/local-ca.pem`.
If it is missing, export it from the running platform proxy:

```shell
cd "$HOME/dev/developer-platform"
mkdir -p .runtime/trust
docker compose cp proxy:/data/caddy/pki/authorities/local/root.crt .runtime/trust/local-ca.pem
cd "$HOME/dev/variability-engineering-framework"
```

Install the CLI and configure the shell for human authentication:

```shell
python3 -m pip install --upgrade developer-platform-cli==0.3.0
unset PLATFORM_ACCESS_TOKEN PLATFORM_TOKEN_ENDPOINT PLATFORM_CLIENT_ID PLATFORM_CLIENT_SECRET
unset PLATFORM_API_URL
export PLATFORM_CA_BUNDLE="$HOME/dev/developer-platform/.runtime/trust/local-ca.pem"
curl --fail --cacert "$PLATFORM_CA_BUNDLE" https://platform.localhost/healthz
curl --fail --cacert "$PLATFORM_CA_BUNDLE" \
  https://identity.localhost/realms/platform/.well-known/openid-configuration
```

Keep `PLATFORM_CA_BUNDLE` exported for login and every subsequent bootstrap
command. A missing CA can cause the CLI's generic availability error while curl
reports `unable to get local issuer certificate`. Do not unset the bundle for
local HTTPS. Re-export it in each new shell, or save its absolute path using the
profile creation command's `--ca-bundle` option.

Create the profile once, then sign in:

```shell
devplat profiles create local --api-url https://platform.localhost \
  --issuer https://identity.localhost/realms/platform \
  --ca-bundle "$PLATFORM_CA_BUNDLE"
devplat --profile local auth login
devplat --profile local auth status --output json
```

If `local` already exists with the correct API and issuer, reuse it; the exported
bundle overrides a missing profile CA setting. Inspect settings with
`devplat profiles show --name local --output json`. Follow the printed verification
URL and code. Human login requires an available secure OS keyring; on a headless
host without one, use an externally obtained short-lived administrator token via
`PLATFORM_ACCESS_TOKEN` instead. Project creation requires platform administrator
authority.

### Publish an image and bootstrap

Use the existing published `latest` image. To build and publish your current
checkout first, run:

```shell
docker login
python3 scripts/bootstrap-image.py --file application-test-local.json
```

This replaces the shared repository's `latest` tag. Publishing does not itself
deploy an application.

The descriptor stores the local PostgreSQL binding and ODIP prefix populated
by bootstrap. On a fresh platform, bootstrap provisions or reuses that platform’s
resources and replaces these saved values. Provision the project and database,
save its binding, and deploy `latest`:

```shell
python3 scripts/bootstrap-application.py --environment test-local --profile local
```

Request access to the local ODIP provider and save the returned binding prefix:

```shell
python3 scripts/bootstrap-access.py --environment test-local --profile local
```

Have the local provider administrator approve the request and grant the required
dataset access. Redeploy the saved prefix and initialize browser access:

```shell
python3 scripts/bootstrap-application.py --environment test-local --profile local --deploy-only
python3 scripts/bootstrap-policy.py --environment test-local --profile local
```

Complete browser login, then have an administrator approve your user and assign
membership in the project's `application-users` group. Provision the AI key from
an owner-only file and obtain the required outbound approvals:

```shell
devplat --profile local secrets set OPENAI_API_KEY --project vareng-test \
  --value-file /private/path/openai-api-key
```

Open <https://web-vareng-test.apps.localhost> and verify login, an ODIP-backed
run, and AI proposals/explanations. Workload readiness alone does not prove these
integrations. Review and commit the descriptor's populated binding values.

### Subsequent deployments

Keep the CA bundle configured and deploy newly published `latest` images with:

```shell
python3 scripts/bootstrap-application.py --environment test-local --profile local --deploy-only
```

This reuses saved bindings, preserves the descriptor, and waits for readiness.
All environment-aware bootstrap scripts accept `test-local`, including
`create-deployment-credential.py` if a dedicated deployment identity is needed.
Its output names `PLATFORM_CLIENT_ID_TEST_LOCAL` and
`PLATFORM_CLIENT_SECRET_TEST_LOCAL`; no CI deployment job consumes them by default.

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

## Energy lifecycle and dependency observations

Migration V4 adds `energy_security_audit` and retention indexes. The hourly
`EnergyRetention` job is enabled by default (`energy.retention.enabled=true`);
only tests disable it. Each invocation deletes up to 500 terminal runs completed
at least 180 days ago and up to 5,000 audit events at least 180 days old, in one
transaction. Candidate rows cascade; active work and shared immutable publication
cache entries are retained. Large backlogs take multiple invocations. Deleted
idempotency reservations may subsequently create a new run. Monitor sanitized
`energy_retention_completed` / `energy_retention_failed` events and backlog age.
Application deletion does not erase retained database backups; backup expiry and
post-restore cleanup remain an operational responsibility.

Run, candidate and explanation ownership checks write minimized ALLOWED/DENIED
audit events. A failed audit write fails the read operation closed. This table
does not replace platform authentication/access-policy audit or seven-day log
retention. No prompts, results, tokens or customer payloads enter these events.

Private management metrics `energy.odip.state` (fixed state labels),
`energy.odip.observed.epoch.seconds`, and `energy.odip.cache.fallbacks` report the
last observed import dependency state and cumulative fallback count since process
start. They are event-driven observations, not continuous reachability probes.
Readiness stays independent so verified-cache fallback remains usable; account for
observation age before alerting. Hosted scraping and alerts still need verification.

The [local verification record](../04-development/energy-gap-closure-verification.md)
contains migration, cleanup and concurrency evidence. Review the new retention
behavior before rolling out this working-tree change.
