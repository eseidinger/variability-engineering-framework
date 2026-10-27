# ADR-014 – Quantitative Energy Configuration Identity

Status: **Accepted** for the bounded, new-equipment model on October 12, 2026.
Implementation evidence belongs in the [model verification record](../04-development/energy-model-verification.md).

## Context

The energy contract requires selected component models and quantities. The
food-service `Variant` remains an unordered set of element IDs under ADR-013.
Reusing that binary identity would collapse choices with different quantities,
capacity, and investment. The released kit defines a complete reference
configuration set; a general expression language is not needed for this slice.

## Decision

Introduce immutable domain-neutral `ElementSelection(elementId, quantity,
existing)` with positive integer quantity. Keep `Variant` and observed-population
analysis semantics unchanged. Typed energy configurations contain one PV model
with 1–100 units, exactly one inverter, and no battery or 1–4 units of one model.
Component field names declare their units; no implicit conversion is performed.

`energy-model/1.0.0` enumerates available choices within roof area and DC/AC
ratio `0.8..1.5`, using the kit's tolerances and quantity bounds. Investment and
self-sufficiency are subsequent evaluation constraints. Estimation applies the
same structural rules as enumeration and rejects more than 100,000 evaluations
before allocating candidates. Exactly 100,000 is allowed. Catalog limits include
unavailable models: 1–20 models of each type.

New-equipment IDs match the released format:

```text
{pvId}-q{pvQuantity}-{inverterId}-battery-none
{pvId}-q{pvQuantity}-{inverterId}-{batteryId}-q{batteryQuantity}
```

Identity is scoped to pinned model/catalog/dataset context, not globally unique
equipment or calculated results. Output order is lexical configuration ID.
Reject collisions and IDs longer than the candidate schema's 128 characters
instead of altering the format or merging assignments. Catalog changes must
carry version context and cannot reinterpret existing results silently.

The request preserves validated unique `existingComponentIds`, but the current
model rejects non-empty lists with `existing_equipment_not_supported`. The
shared oracle supplies no quantity, mixed-ownership, or identity examples for
that case. Shared acceptance examples must precede support. This slice does
not claim to fulfill the entire energy contract.

## October 27 release-scope amendment

The maintainer approved new installations only for the current release in shared
[DEC-07](https://github.com/eseidinger/energy-system-configuration/blob/main/decisions/DEC-07-new-equipment-release-scope.md). This supersedes the original unresolved release-scope gap above: the explicit
non-empty-list rejection is the required behavior for this release. Existing
equipment remains a later version requiring shared quantities, ownership,
compatibility, costing and identity examples. The v1 wire shape, immutable kit
0.5.0 pin and numerical oracle remain unchanged; release packaging must carry
the scope decision before final acceptance.

## Alternatives and consequences

Changing binary `Variant` equality would break the accepted observed contract.
A general numeric AST or portfolio solver would add unverified semantics beyond
this model. Hashed IDs would diverge from the oracle and its tie-breaking rule.
Assuming each existing model ID means one unit would invent shared semantics.

The energy types and enumeration are pure Java, independent of HTTP, Jackson,
SQL, and UI. Publication parsing/verification, simulation, persistence, and
presentation follow separately. Existing-equipment and ID limits remain visible.

## Validation

Consumer tests compare all 252 configurations, selections/quantities,
investment totals, roof use, and DC/AC ratios with the released oracle. Boundary
tests cover availability, no battery, roof/quantity limits, finite numbers,
immutable collections, search bounds, ordering, and ID collisions. Existing
food-service core/API/PostgreSQL tests remain regression requirements.
