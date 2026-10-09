# Product Vision

Status: documented direction. The observed food-service analysis slice, including
normalization, analysis, impact comparison, PostgreSQL persistence, UI/API integration,
and local container verification, is implemented. Generative modeling, configuration
assistance, portfolio optimization, reusable components, and platform verification remain planned.

The Variability Engineering Framework provides a domain-independent way to model, analyze, configure, and optimize systems with many possible variants. A shared representation connects feature models, observed datasets, structural variants, impact scenarios, and portfolios so that analytical results remain comparable and explainable.

Development starts with a small runnable variability-engineering application. The
current synthetic food-service fixture validates observed variation, element reuse,
supply impact, and dietary classification. A licensed external dataset remains the
planned real-world demonstration. Reusable framework components for Java, Python,
TypeScript, Angular, and React are created later, after the prototype and reference
domain establish stable semantics and contracts.

## Capabilities

- **Modeling:** represent generative configuration models and normalize imported observations through domain adapters.
- **Analysis:** explore configurations, candidate records, distinct variants, elements, and metrics through reorderable analysis dimensions.
- **Impact analysis:** compare a baseline with a scenario such as an unavailable element or an added constraint.
- **Configuration assistance:** complete partial requirements and explain compatible configurations and variants.
- **Optimization:** select portfolios under constraints and expose trade-offs between competing objectives.

## Target users

- Application developers reuse domain models, calculations, contracts, and visualization components.
- Domain users explore candidate records, structural variants, element usages, analysis dimensions, and the effects of changes.
- Decision makers compare feasible portfolios and the trade-offs between coverage, diversity, cost, sustainability, and operational complexity.
- Architecture and lab work uses the project as a reproducible workload for algorithm, persistence, and integration comparisons.

## Product boundaries

The project concerns variability models, observed variant populations, configuration spaces, configuration-dependent elements, impact scenarios, and portfolio decisions. Statistical variance and ANOVA are outside its core function. SaaS tenancy, authentication, a plugin system, and a broad infrastructure catalog are not approved scope. Large-scale solver integration, quantitative resource accounting, and multi-objective optimization are future capabilities rather than prototype commitments.
