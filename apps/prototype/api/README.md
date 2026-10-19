# Variability Engineering API

## Implemented domain-independent core

The API contains a pure Java core in `de.eseidinger.variabilityengineering.core`. It provides immutable versioned analysis populations, structural variants, record and dimension validation, explicit filters, selected-record IDs, ordered grouping trees, metrics, element leverage, and whole-record element-unavailability impact scenarios.

The core is independent of food-service terminology, HTTP, persistence, and UI frameworks. `de.eseidinger.variabilityengineering.adapter.foodservice.FoodServiceFixtureAdapter` verifies the [food-service MVP fixture](../../../examples/food-service/mvp-v1/README.md), normalizes its aliases, derives dimensions, preserves artifact provenance, and returns rejected records. The fixture is stored and reloaded through PostgreSQL; analyses remain calculated on demand. The implemented HTTP contract is documented in [Food-Service Analysis MVP HTTP Contract](../../../docs/02-architecture/food-service-http-contract.md).

Run the core and API tests with:

```shell
python3 ../../../scripts/validate-energy-contract-kit.py --prepare-only --output-dir /tmp/vef-energy-api-kit
export ENERGY_CONTRACT_KIT_DIRECTORY=/tmp/vef-energy-api-kit/contract-kit-v0.5.0/contract-kit
./mvnw test
```

Run from this API directory and choose an extraction directory that does not
already exist. Preparation requires Python 3.12 or newer and uses the locked
release checksum. PostgreSQL tests require Docker; a local `.env` can override
Dev Services, so ensure its database is running or pass explicit test database
connection properties.

## Energy contract fixtures

The energy implementation targets the [checksum-pinned shared contract kit](../../../contracts/energy/README.md).
API CI prepares its schemas, fixtures, and reviewed expected results and sets
`ENERGY_CONTRACT_KIT_DIRECTORY` for Java model conformance tests. The pure Java
`energy` package validates typed catalog/customer inputs and enumerates bounded
new-equipment configurations matching all 252 reference choices. It also
simulates hourly dispatch and matches the released 194-candidate feasible
ranking. The ODIP adapter verifies bytes and supports bounded HTTPS retrieval;
PostgreSQL stores verified immutable publication bytes. `EnergyPublicationImportService`
uses VEF's managed reader binding, rechecks ODIP on each import, and stores
only verified bytes. ODIP's publication API is implemented and locally verified
in its own repository; VEF's authenticated retrieval from a deployed ODIP instance,
run APIs, and the energy UI remain planned. Non-empty existing
equipment is explicitly unsupported pending shared examples. See the
[energy architecture](../../../docs/02-architecture/energy-configuration.md).

For the deployed handoff, `application.json` sets the non-secret
`ENERGY_ODIP_BINDING_PREFIX` to `ACCESS_209A1DDA331C` for the current `vareng`
deployment project. The dataset grant must target that resource's stable
principal ID, reported as `prn_0a3b0952ce0e4c2689bcbdb9331bc185`.
The platform must inject
`<prefix>_CLIENT_ID`, `<prefix>_CLIENT_SECRET`, and `<prefix>_TOKEN_URL` into the
VEF process. `ENERGY_ODIP_ORIGIN` defaults to
`https://web-odip.apps.eseidinger.de`; override it only with another approved
HTTPS origin. Do not place the managed secret or token in application config.
The deployment descriptor allows HTTPS egress to ODIP and
`identity.eseidinger.de`; its DNS pins must be approved and reapplied after
address changes.

For a local optimization run without the managed ODIP reader binding, the
Quarkus `dev` profile has an explicit fixture importer. First verify/extract the
pinned 0.5.0 kit as described above. Start the API with
`ENERGY_CONTRACT_KIT_DIRECTORY` pointing to its `contract-kit` directory and
`ENERGY_ODIP_LOCAL_FIXTURE=true`:

```shell
export ENERGY_CONTRACT_KIT_DIRECTORY=/tmp/vef-energy-kit-050-verified/contract-kit-v0.5.0/contract-kit
export ENERGY_ODIP_LOCAL_FIXTURE=true
./mvnw quarkus:dev
```

This imports only the released Wasserburg
dataset version `1.0.0`, verifies its manifest and artifacts, and persists the
verified bytes in the local database. The importer is unavailable in production
and is disabled in development unless explicitly selected.

## AI assistance

The optional AI routes use the Quarkus LangChain4j OpenAI extension to prepare
validated request proposals and evidence-cited explanations. They are
disabled by default. Set `ENERGY_AI_ENABLED=true`, inject `OPENAI_API_KEY` from
the managed secret store, and allow reviewed HTTPS egress to the provider before
enabling them in a deployment. `ENERGY_AI_MODEL` defaults to `gpt-6.1-sol`.
`ENERGY_AI_TIMEOUT` controls the OpenAI call timeout and defaults to `180s`;
the Quarkiverse extension otherwise uses `10s`, which is too short for some
reasoning-model responses. A local override can be set before starting Quarkus,
for example `export ENERGY_AI_TIMEOUT=240s`.
OpenAI JSON mode is enabled for both AI routes. VEF still validates the released
envelopes and domain rules; a rejected proposal returns a short validation
reason such as `invalid_ready_shape` without exposing the model's raw response
or customer input.
For a `503 ai_unavailable` response, the API log records exception types and
an upstream HTTP status when available; it does not log the provider response,
prompt, or API key.
Console logs use the [Quarkus JSON logging extension](https://quarkus.io/guides/logging#json-logging)
in all profiles, with one JSON object per line and the startup banner disabled.
Records contain structured timestamps, levels, logger names, messages, MDC and
exception details when present. AI request/response logging remains disabled.
The routes require the platform's normalized principal context and the same
browser CSRF policy as run creation. The proposal route never starts a run.
The UI displays the complete request and requires confirmation after the latest
edit before using the existing optimization endpoint. The explanation route
reads only an owned completed run and its recorded recommended candidate,
checks the 16-source/64-KiB evidence bound, and validates every returned
citation before display. On October 19, 2026, the owner confirmed that live AI
explanations and the full deployed energy integration flow work end to end.
Formal AI acceptance cases and regression evidence remain tracked in the
[implementation plan](../../../docs/04-development/energy-configuration-implementation-plan.md).

An explanation rejected with `502 invalid_ai_explanation` includes a safe reason
in its message, such as `invalid_envelope`, `missing_required_citations`,
`invalid_citation_pointer`, or `citation_value_mismatch`. The explanation prompt
specifies the exact category names, document-relative pointers, and complete
constraint-object citations required by validation. Rejected output never replaces
the saved deterministic result; raw model responses and customer evidence are not
included in validation errors.

With an authenticated Developer Platform CLI session, inspect the current
project's outgoing service access and confirm that the ODIP resource is active
and reports this prefix and principal:

```shell
devplat access services list --project vareng --direction outgoing --output json
```

With the binding variables available to the test process, run the focused
deployed check from this directory:

```shell
export ENERGY_ODIP_BINDING_PREFIX="ACCESS_209A1DDA331C"
./mvnw -Dtest=EnergyPublicationDeployedTest test
```

The test verifies the energy publication's exact digest and conditional reads,
then expects `403` for the non-energy control dataset. It is skipped during
ordinary tests when the prefix is absent. A dataset grant alone does not supply
VEF's service credential; keep the access resource and stable principal ID in
the non-secret handoff record. This check does not establish recovery, rotation,
or deployed VEF run-API acceptance.

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

Start PostgreSQL from the repository root:

```shell script
docker compose up -d db
```

Then run Quarkus from `apps/prototype/api`:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The packaged application requires the production PostgreSQL variables documented in
[`application.properties`](src/main/resources/application.properties). The repository
Compose environment is the supported local packaged-image path.

The API includes Micrometer Prometheus metrics on the Quarkus management interface
at port 9000 (`/q/metrics`) and OpenTelemetry HTTP tracing. The platform
deployment descriptor declares the private metrics scrape and enables tracing;
the platform supplies the OTLP endpoint and sampling rate. The application uses
HTTP/protobuf export to the injected `OTEL_EXPORTER_OTLP_ENDPOINT`, defaulting to
the platform forwarder at `http://127.0.0.1:4318`. Parent-based sampling uses the
platform's `OTEL_TRACES_SAMPLER_ARG`, with a 100% root fallback. An unsampled
parent remains unsampled. LangChain4j generates AI-service and model-completion
spans automatically; prompt, completion, and tool payload capture are explicitly
disabled. See [LangChain4j tracing](https://docs.quarkiverse.io/quarkus-langchain4j/dev/observability.html#_tracing).
Development and test
profiles disable trace export, as does repository Compose through
`QUARKUS_OTEL_SDK_DISABLED=true`. To test trace export locally, set
`QUARKUS_OTEL_SDK_DISABLED=false` and provide a reachable OTLP HTTP collector
through the Quarkus exporter endpoint and protocol settings.

The current Developer Platform telemetry gateway renames every application span
to `application.span` and removes `gen_ai.*` attributes and AI scope names before
storage. Consequently, searching the hosted backend for `completion` or
`langchain4j.aiservices.*` does not find these spans by name. Use the
`X-Platform-Trace-Id` response header to look up the complete request trace with
`devplat traces get TRACE_ID --project vareng-test --output json` (or `vareng`).
Identifiable AI span names, model names and token counts require a reviewed
platform collector allowlist change. The application cannot override that policy.

A local packaged-production smoke check exported HTTP, AI-service
(`langchain4j.aiservices.ProposalModel.propose`) and model-completion spans through
an OTLP/HTTP collector using a fake local model, without prompt/completion capture.
This verifies application instrumentation/export, not hosted collector readback.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute the native binary under `target/`; its name is derived from the
Maven artifact name and version.

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.
