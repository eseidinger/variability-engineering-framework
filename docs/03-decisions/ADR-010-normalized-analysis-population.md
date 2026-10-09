# ADR-010 – Normalize Generated and Observed Variant Records

Status: **Implemented for observed records**; generated-record normalization remains
documented direction.

## Context

Generative feature models derive element presence from configurations, while real datasets such as recipes directly observe attributes and element usages. Forcing observations into artificial features or presence conditions would invent semantics and lose provenance.

## Decision

Both sources produce a versioned analysis population of identified variant records. Each record contains analysis dimensions, normalized element usages, provenance, and a structural signature. Generated records retain their source configuration; imported records retain their source and mapping identity.

## Consequences

Analysis and visualization can share one representation without claiming that observed data was generated. Record identity remains distinct from structural variant equality, so record and distinct-variant counts must both be available.

## Validation

Analyze one generated fixture and one imported recipe fixture through the same analysis API while preserving their different provenance and expected record and variant counts.
