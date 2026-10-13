# Architecture Overview

Status: target architecture. The implemented slice currently covers observed-dataset
adaptation, normalized populations, analysis, element-unavailability impact,
PostgreSQL persistence, the application API, and visualization. A separate
[bounded energy model](energy-configuration.md) now validates typed inputs,
generates new-equipment configurations, and evaluates the released hourly
reference profile; durable run integration remains planned.

```mermaid
flowchart LR
    Model[Generative model] --> Validation[Model validation]
    Validation --> Evaluation[Constraints and presence conditions]
    Dataset[Observed dataset] --> Adapter[Domain adapter]
    Evaluation --> Population[Normalized analysis population]
    Adapter --> Population
    Population --> Analysis[Analysis and metrics]
    Population --> Impact[Impact comparison]
    Model --> Configuration[Configuration assistance]
    Configuration --> Population
    Population --> Optimization[Portfolio optimization]
    Analysis --> View[Analysis view and tree]
    Impact --> View
    Optimization --> View
    View --> UI[Visualization]
    Model <--> Persistence[Persistence adapter]
    Population <--> Persistence
    Persistence <--> DB[(PostgreSQL)]
```

| Area | Responsibility | Boundary |
|---|---|---|
| Core model | Features, model constraints, elements, usages, configurations, records, dimensions, scenarios, and portfolios | No UI or database logic |
| Domain adapter | Map and document external observations | No invented generative semantics |
| Expression engine | Validate and evaluate portable model rules | Numeric portfolio rules are a separate contract |
| Normalization | Produce versioned variant records and structural signatures | Preserve source and derivation provenance |
| Analysis engine | Calculate records, distinct variants, element metrics, and grouped results | Domain- and UI-independent |
| Impact engine | Compare an explicit scenario with its baseline | Does not mutate or reinterpret the baseline |
| Configuration engine | Complete partial requirements against a generative model | Distinguishes partial requests from complete configurations |
| Optimization engine | Select feasible portfolios and evaluate objectives | Does not redefine model validity or analysis counts |
| View builder | Project and group results by dimensions | Does not change domain rules |
| UI | Control views and explain results and errors | Does not redefine semantics |
| Application/API | Coordinate use cases and expose results | Transport remains outside the core |
| Persistence adapter | Save and load versioned models, mappings, populations, and results | Database details remain replaceable |

The prototype currently owns the observed-population normalization, analysis, impact,
persistence, and presentation slice. General-purpose generative-model evaluation, configuration
assistance, and optimization remain target components. Stable boundaries become
reusable framework components only after synthetic fixtures and the food-service
reference domain validate them.
