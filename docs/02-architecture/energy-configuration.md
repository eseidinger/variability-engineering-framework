# Energy Configuration Architecture

Status: typed inputs and bounded new-equipment generation implemented;
publication retrieval, simulation/ranking, run APIs/persistence, UI, and AI
assistance planned. The dependency is [contract kit 0.4.0](../../contracts/energy/README.md).

## Implemented boundary

`core.configuration.ElementSelection` is a domain-neutral positive integer
selection that distinguishes new/existing elements. It does not change binary
`Variant` equality or automatically produce an analysis population.
[ADR-014](../03-decisions/ADR-014-energy-configuration-identity.md) defines energy
identity and enumeration.

The pure Java `energy` package owns:

| Type | Responsibility |
| --- | --- |
| `EnergyComponent` | Sealed PV/inverter/battery types, explicit units, availability, price, and source owner/license/reference/retrieval time/version. |
| `EnergyCatalog` | Immutable schema/version/currency, unique model IDs, canonical order, 1–20 models per type, and available-model selection. |
| `EnergyOptimizationRequest` | Immutable dataset reference, site, constraints, objective, and fixed v1 economics assumptions. |
| `EnergyConfiguration` | Complete quantities/components, reference ID, selections, capacity, area, ratio, and initial investment. |
| `EnergyFeatureModel` | Independent model version, structural search estimate, limits, and bounded generation. |
| `EnergyModelException` | Model failure code for a later application/HTTP adapter. |

Constructors enforce supported schema majors, region `DE-BY-09187182`, EUR,
positive demand/technical attributes, bounded fractions, non-negative finite
price/roof/budget, unique existing IDs, and 20-year/0.04 assumptions. Aggregated
values must stay finite. Constructing a dataset reference does not prove
publication availability; that belongs to the planned ODIP consumer.

The model selects one PV model, one inverter, and either no battery or one
battery model. Available choices satisfy roof area, at most 100 PV units, and
DC/AC ratio `0.8..1.5`. Each valid PV/inverter pair yields one no-battery choice
plus four choices per available battery model. Search estimation uses checked
long arithmetic and the same pruning rules as generation. More than 100,000
evaluations fails before candidate allocation. Immutable output is sorted by ID.

Budget/self-sufficiency do not prune the evaluated population; violated customer
constraints must remain available for diagnostics. Simulation and feasibility
follow separately. The generator reproduces 252 reference configurations; it
does not yet calculate the 194 feasible results or choose a recommendation.

## Limits of this slice

- Non-empty existing-equipment requests fail explicitly pending shared quantity/ownership/identity examples.
- Individually valid component IDs may concatenate into an ID exceeding 128 characters or colliding with another assignment. These cases fail instead of truncating or merging choices.
- Domain types are not wire DTOs or a JSON Schema validator. Test-only `EnergyFixture` maps known released documents; production parsing and integrity checks belong to the publication consumer.
- There are no energy HTTP endpoints or UI yet. The general-purpose Boolean feature-model AST remains planned.

## Planned flow

The application will pin and verify an ODIP profile, construct typed inputs,
estimate/generate configurations, simulate each against the same versions,
evaluate constraints, and rank results. Durable runs will identify request,
dataset/artifact checksums, model/simulation/optimizer versions, execution state,
candidates, and evidence. DTOs, repositories, and UI stay outside the pure model.

The upstream DEC-02 already approves v1 dispatch and economics. A local
simulation ADR will record Java numeric parity before simulation implementation.
A run-execution ADR will define queue claims, deadlines, idempotency, ownership,
and recovery before persistence/API work.

See the [implementation plan](../04-development/energy-configuration-implementation-plan.md)
and [verification record](../04-development/energy-model-verification.md).
