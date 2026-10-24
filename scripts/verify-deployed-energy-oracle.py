#!/usr/bin/env python3
"""Compare authenticated deployed energy responses with the pinned full oracle."""

import argparse
from decimal import Decimal
import json
from pathlib import Path
import sys


def load_json(path: Path):
    with path.open(encoding="utf-8") as source:
        return json.load(source, parse_float=Decimal)


def same_value(expected, actual, path: str):
    if isinstance(expected, bool) or isinstance(actual, bool):
        return type(expected) is type(actual) and expected == actual
    if isinstance(expected, (int, Decimal)) and isinstance(actual, (int, Decimal)):
        if path.endswith("EUR"):
            return Decimal(expected) == Decimal(actual)
        if path.endswith("KWh"):
            return abs(Decimal(expected) - Decimal(actual)) <= Decimal("0.000001")
        return abs(Decimal(expected) - Decimal(actual)) <= Decimal("0.000000001")
    if isinstance(expected, dict) and isinstance(actual, dict):
        return expected.keys() == actual.keys() and all(
            same_value(expected[key], actual[key], f"{path}/{key}") for key in expected
        )
    if isinstance(expected, list) and isinstance(actual, list):
        return len(expected) == len(actual) and all(
            same_value(want, got, f"{path}/{index}")
            for index, (want, got) in enumerate(zip(expected, actual))
        )
    return type(expected) is type(actual) and expected == actual


def verify(run: dict, pages: list[dict], oracle: dict):
    expected_result = oracle["result"]
    if not isinstance(run.get("runId"), str) or not run["runId"].startswith("run-"):
        raise ValueError("Run ID is missing or malformed")
    for field, expected in expected_result.items():
        if field in {"runId", "createdAt", "completedAt"}:
            continue
        if field not in run or not same_value(expected, run[field], f"result/{field}"):
            raise ValueError(f"Result differs from oracle at {field}")

    expected_candidates = {item["configurationId"]: item for item in oracle["candidates"]}
    pages_by_index = {}
    for page in pages:
        index = page.get("page")
        if not isinstance(index, int) or index in pages_by_index:
            raise ValueError("Candidate page index is missing or repeated")
        if page.get("total") != len(expected_candidates):
            raise ValueError(f"Candidate total differs on page {index}")
        pages_by_index[index] = page
    if sorted(pages_by_index) != list(range(len(pages_by_index))):
        raise ValueError("Candidate pages must start at zero without gaps")

    candidates = []
    for index in sorted(pages_by_index):
        page = pages_by_index[index]
        size = page.get("pageSize")
        items = page.get("items")
        if not isinstance(size, int) or size < 1 or not isinstance(items, list):
            raise ValueError(f"Candidate page {index} has invalid shape")
        if len(items) > size or len(candidates) != index * size:
            raise ValueError(f"Candidate page {index} has a gap or wrong page size")
        candidates.extend(items)
    if len(candidates) != len(expected_candidates):
        raise ValueError(f"Captured {len(candidates)} of {len(expected_candidates)} candidates")
    actual_ids = [item.get("configurationId") for item in candidates]
    if len(set(actual_ids)) != len(actual_ids) or set(actual_ids) != set(expected_candidates):
        raise ValueError("Candidate IDs are duplicated, missing, or unexpected")
    if actual_ids[: len(oracle["ranking"])] != oracle["ranking"]:
        raise ValueError("Feasible candidate ranking differs from oracle")
    for candidate in candidates:
        identifier = candidate["configurationId"]
        if not same_value(expected_candidates[identifier], candidate, f"candidate/{identifier}"):
            raise ValueError(f"Candidate differs from oracle: {identifier}")
    return run["runId"], len(candidates), len(oracle["ranking"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kit", type=Path, required=True, help="Verified contract-kit directory")
    parser.add_argument("--run", type=Path, required=True, help="Authenticated run JSON export")
    parser.add_argument("--candidate-page", type=Path, action="append", required=True,
                        help="Candidate page JSON export; repeat for every page")
    args = parser.parse_args()
    oracle = load_json(args.kit / "expected-results" / "optimization-reference.json")
    run_id, total, feasible = verify(
        load_json(args.run), [load_json(path) for path in args.candidate_page], oracle
    )
    print(f"PASS {run_id}: {total} candidates, {feasible} feasible, full oracle and ranking match")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
