# ADR-006 – Analysis Tree as a Derived View

Status: **Partially implemented** for filtering and ordered grouping; hiding,
combination, and the complete `AnalysisView` contract remain proposed.

## Context

Users must reorder, hide, and combine feature-derived, imported, or derived analysis dimensions without changing domain rules or population membership.

## Decision

Represent view choices separately in an `AnalysisView`; derive the tree from an identified normalized analysis population.

## Consequences

Projection and grouping require explicit aggregation semantics. Tree identity does not become model identity.

## Validation

Reordering preserves overall results, hiding preserves records, and grouping follows documented configuration, record, variant, and element count rules.
