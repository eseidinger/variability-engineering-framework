# Food-Service MVP Deployment Verification

Status: local Compose verification completed on October 1, 2026.

## Reproducible command

From the repository root, run:

```shell
apps/prototype/integration-tests/verify-compose.sh
```

The verifier builds the combined image, starts it with a fresh PostgreSQL 17 volume, confirms the packaged Angular shell and food-service API, checks the unfiltered population, vegan query, and garlic-unavailability scenario, restarts the application, and verifies the persisted population again. It uses port `18080` by default so it does not conflict with a locally running API; set `APP_PORT` to override it.

## Recorded result

| Field | Result |
| --- | --- |
| Source baseline | `802f86d` plus an uncommitted worktree; not exactly reconstructible, so a committed rerun is required before treating this as release evidence |
| Combined-image build | successful |
| Runtime verification | successful; cached end-to-end verifier wall time: 14.7 seconds |
| Peak application memory | 271,429,632 bytes (about 259 MiB), from the container cgroup `memory.peak` during the verifier |
| Population result | 12 records; vegan filter: 8 records; garlic scenario executed |
| Persistence recreation | successful after an application-container restart |

## Limitations

- This is a local Docker Compose observation, not a deployment to a target platform.
- The memory figure is a single verifier-run cgroup peak and is not a capacity limit or a load-test result.
- The black-box check confirms the served Angular shell and selected API behavior; it
  does not render the dashboard in a browser. Angular unit tests cover summary loading
  and load-error presentation. Filtering, grouping interaction, leverage, and impact
  still need browser end-to-end coverage.
