# Development Plan

Status: proposed sequence without confirmed dates.

```mermaid
flowchart LR
    Semantics[Semantics and stack] --> Core[Evaluation core]
    Core --> App[Prototype application]
    App --> Deploy[First deployment]
    Deploy --> Interactive[Interactive analysis]
    Interactive --> Normalize[Normalized populations]
    Normalize --> Food[Food-service demonstration]
    Food --> Impact[Impact and configuration]
    Impact --> Optimize[Portfolio optimization]
    Optimize --> Framework[Framework components]
    Framework --> Experiments[Additional domains and experiments]
```

The prototype application is the first integrated deliverable. The implemented
synthetic food-service fixture establishes observed-population semantics. A future
generative fixture will establish configuration, constraint, and presence-condition
semantics. Framework components are created later from stable, verified contracts
rather than treated as prerequisites.

Implementation has deliberately advanced the observed-data path ahead of the
generative path: the synthetic food-service population, adapter, analysis, impact,
persistence, UI, and local image verification exist today. Generative evaluation,
the first successful platform deployment, hiding/combining dimensions, configuration
assistance, and optimization remain future milestones. The flow above is the target
dependency model, not a claim that every earlier box is complete.

## Proposed repository evolution

```text
apps/prototype/
  api/                   Independent backend project
    src/                 API and domain source
    tests/               Unit, contract, and persistence tests
  ui/                    Independent frontend project
    src/                 UI source and assets
    tests/               Component, interaction, and accessibility tests
  integration-tests/     Black-box UI/API/PostgreSQL checks
  deployment/            Single-image deployment assembly
docs/                    Product, architecture, decisions, development, operations
examples/                Synthetic models and later domain fixtures
adapters/                Later domain-specific dataset mappings
spec/                    Stable interchange and result contracts
compliance-tests/        Shared reference inputs and expected outputs
java/                    Later Java components
python/                  Later Python components
typescript/              Later TypeScript components
ui/angular/              Later Angular visualization components
ui/react/                Later React visualization components
experiments/             Reproducible algorithm and storage studies
```

Create only the directories required by current work. A future solver dependency belongs behind an optimization interface and is added only after an exhaustive fixture defines its semantics. Add implementation evidence and observed limitations as each milestone is completed.
