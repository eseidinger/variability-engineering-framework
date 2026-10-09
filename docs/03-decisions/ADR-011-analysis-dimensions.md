# ADR-011 – Separate Analysis Dimensions from Configuration Features

Status: **Implemented for imported and derived observed-population dimensions**;
feature-derived dimensions remain documented direction.

## Context

Configuration features are decision variables, but useful analysis dimensions may also be imported attributes or derived classifications such as cuisine, diet, or allergen category.

## Decision

Model `AnalysisDimension` separately from `Feature`. A dimension declares its type, origin, value behavior, and derivation where applicable. A feature may project to a dimension, but a dimension does not thereby become a configurable feature.

## Consequences

Observed datasets do not require artificial configuration models. Tree operations and metrics work over record dimensions, while configuration validation continues to use features and model constraints.

## Validation

Produce equivalent tree operations for a feature-derived dimension and an imported or derived dimension without changing their source semantics or population membership.
