# Sources and Traceability

Status: source baseline for planning; links and discussion summaries do not constitute implementation or deployment evidence.

The listed website pages were reviewed and aligned with repository evidence on October 1, 2026. The framework update was incorporated on September 28, 2026. Website material consists of project and lab pages rather than completed technical articles or benchmark reports.

| Source | Contribution |
|---|---|
| [Variability Engineering Framework](https://www.eseidinger.de/projects/variability-engineering-framework/) | Project pipeline, implemented food-service MVP, PostgreSQL, shared container image, resource observation, and remaining roadmap. |
| [Developer Platform](https://www.eseidinger.de/projects/developer-platform/) | Operating foundation, PostgreSQL integration, and platform limitations. |
| [Data Architecture Lab](https://www.eseidinger.de/lab/data-architecture/) | Workload-based storage comparisons and reproducibility. |
| [Performance Lab](https://www.eseidinger.de/lab/performance/) | Planned calculation measurements and measurement method. |
| [Writing](https://www.eseidinger.de/writing/) | Articles in preparation; no published prototype results at review time. |
| [Projects](https://www.eseidinger.de/projects/) | Relationship between platform, application, and lab. |
| [Lab](https://www.eseidinger.de/lab/) | Shared experimental method and investigation status. |
| [Framework update](source-material/2026-09-28-variability-engineering-framework-update.md) | Archived source for the broader variability-engineering vision, food-service demonstration, impact analysis, configuration assistance, and optimization goals. Canonical documents take precedence. |
| [Energy configuration contract 0.4](https://github.com/eseidinger/energy-system-configuration/blob/93885442fd503d5bc529a732b6da47f6baffcfd1/energy-configuration-integration-contract.md) and [released kit 0.4.0](https://github.com/eseidinger/energy-system-configuration/releases/tag/contract-kit-v0.4.0) | Reviewed October 12, 2026 against local VEF revision `93793d1aa785a796b3d7fd9d41aa4cb81e1ef1b9`; archive checksum verified. Normative source for the [energy configuration implementation plan](04-development/energy-configuration-implementation-plan.md), including shared schemas/oracles and platform boundaries. Planning evidence only; no consumer conformance or deployed acceptance claimed. |

The October 17, 2026 energy-plan update uses the [released kit 0.5.0](https://github.com/eseidinger/energy-system-configuration/releases/tag/contract-kit-v0.5.0), [normative AI profile](https://github.com/eseidinger/energy-system-configuration/blob/contract-kit-v0.5.0/contract-kit/ai/README.md), and [AI acceptance cases](https://github.com/eseidinger/energy-system-configuration/blob/contract-kit-v0.5.0/contract-kit/ai/acceptance-cases.json). The downloaded archive passed `SHA256SUMS` verification: `ae4edaf6799d4b57510d14d2004c5b65ff6b05f3b225e96bbd5971b30ce8832b`. The [Gate 5 record](https://github.com/eseidinger/energy-system-configuration/blob/contract-kit-v0.5.0/reviews/2026-10-17-gate-5-acceptance.md) establishes maintainer acceptance for progression to Gate 6 without new execution reports. This planning review does not establish 0.5.0 consumer adoption or AI implementation acceptance.


## Maintenance

After a binding architecture decision, update the corresponding ADR with its date, status, and rationale. After implementation or deployment, record the repository revision, checks, environment, results, and limitations. Mark proposals and planned experiments as implemented or verified only when reproducible evidence exists.
