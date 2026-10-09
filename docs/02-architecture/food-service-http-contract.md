# Food-Service Analysis MVP HTTP Contract

Status: implemented, fixture-backed MVP contract. This contract exposes the binding [food-service analysis semantics](food-service-mvp-semantics.md) without placing HTTP types in the domain-independent core.

## Scope and population source

The API serves the checked-in `food-service-mvp` population at version `1.0.0`. It imports the fixture at application startup through the food-service adapter, stores it in PostgreSQL when that immutable ID/version is absent, and then serves the persisted population.

The fixture directory is configured by `variability.fixture.directory`; the development default is `../../../examples/food-service/mvp-v1`, relative to the API project directory.

All endpoints produce `application/json` below `/api/populations/{populationId}`. An unknown population or record returns `404`. Invalid identifiers, filters, dimensions, or scenarios return `400`.

## Shared response values

Analysis responses, including the baseline and scenario results nested in an impact
response, include:

- `population.id` and `population.version`;
- `query`, echoing the normalized active filters, required elements, and grouping order; and
- `metrics`, containing record, structural-variant, and unique-element counts, plus per-element counts and frequencies.

`DimensionFilterRequest` has optional `requiredState` (`KNOWN` or `UNKNOWN`), `includedValues`, and `excludedValues`. Absent collections mean empty collections. Filters and required elements are combined with logical `AND` according to the binding MVP semantics.

## Endpoints

### Population summary

`GET /api/populations/food-service-mvp`

Returns population identity, complete artifact provenance, dimension definitions with
observed values, unfiltered summary metrics, and rejected source rows.

### Record detail

`GET /api/populations/food-service-mvp/records/{recordId}`

Returns the population identity and one accepted record’s canonical `elementIds` and dimension values. Each dimension carries `state` and `values`, preserving `KNOWN` empty values separately from `UNKNOWN`.

### Analysis

`POST /api/populations/food-service-mvp/analysis`

Request example:

```json
{
  "dimensionFilters": {
    "diet": {"includedValues": ["VEGAN"]}
  },
  "requiredElementIds": ["tomato"],
  "groupingDimensions": ["cuisine", "dishType"]
}
```

Returns the shared analytical values, selected record IDs, and an ordered grouping tree. Each group path item has `dimensionId`, `valueKind` (`VALUE`, `KNOWN_EMPTY`, or `UNKNOWN`), and, for `VALUE`, `value`.

### Element leverage

`POST /api/populations/food-service-mvp/elements/{elementId}/leverage`

The body contains an optional `query` and optional `dimensionIds`. When omitted, `dimensionIds` defaults to `cuisine` and `diet`. The response includes the shared population/query context, the selected-record count for the element, and known values for each requested dimension.

### Element unavailability impact

`POST /api/populations/food-service-mvp/element-unavailability`

Request example:

```json
{
  "query": {"groupingDimensions": ["cuisine"]},
  "unavailableElementIds": ["garlic"]
}
```

`unavailableElementIds` is required and non-empty. The response includes the shared
population/query context, the scenario rule, baseline and scenario analysis results,
lost record IDs, lost structural variants, lost element IDs, absolute `rootDelta`,
relative `relativeDelta`, and absolute grouped deltas. A relative value is `null` when
its baseline is zero. The operation excludes complete records and does not mutate the fixture population.

## Compatibility rule

The endpoint path identifies the population. Responses always identify the exact population version and fixture provenance, so a future persisted or changed fixture population can be exposed under a distinct version without reinterpreting an existing result.
