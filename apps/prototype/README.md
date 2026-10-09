# Prototype Application

The prototype is split into independent API and UI projects. Each project owns its source code, tests, dependencies, and build configuration.

## Structure

| Path | Responsibility |
|---|---|
| `api/` | Domain validation, analysis, persistence, and HTTP endpoints. |
| `ui/` | Observed-population analysis controls, results, and error presentation. |
| `integration-tests/` | Black-box checks across the built UI, API, and PostgreSQL. |
| `deployment/` | Assembly of the independently built projects into the deployable image. |

## Boundaries

- The UI communicates with the API through the documented population HTTP contract;
  every response identifies the immutable population version it represents.
- The UI does not implement separate constraint, variant, or counting semantics.
- The API does not depend on a specific UI framework.
- PostgreSQL access belongs to the API.
- Shared generated artifacts must come from a documented contract; source code is not copied between projects.
- The deployment layer may serve the built UI and API from one container image without merging their project structures.

The initial implementation uses a Quarkus API, an Angular UI, and a multi-stage Docker build that packages both in one runtime image.
