#!/usr/bin/env bash
set -euo pipefail

readonly app_port="${APP_PORT:-18080}"
readonly compose=(docker compose --profile app)
readonly base_url="${BASE_URL:-http://localhost:${app_port}}"
export APP_PORT="$app_port"
readonly retries=30

cleanup() {
  "${compose[@]}" down -v
}

wait_for_url() {
  local url="$1"
  for ((attempt = 1; attempt <= retries; attempt++)); do
    if curl --fail --silent --show-error "$url" >/dev/null; then
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for $url" >&2
  return 1
}

assert_contains() {
  local value="$1"
  local expected="$2"
  if [[ "$value" != *"$expected"* ]]; then
    echo "Expected response to contain: $expected" >&2
    return 1
  fi
}

trap cleanup EXIT

"${compose[@]}" up --build --detach
wait_for_url "$base_url/api/populations/food-service-mvp"
wait_for_url "$base_url/api/status"
wait_for_url "$base_url/"

ui=$(curl --fail --silent --show-error "$base_url/")
summary=$(curl --fail --silent --show-error "$base_url/api/populations/food-service-mvp")
analysis=$(curl --fail --silent --show-error --header 'Content-Type: application/json' \
  --data '{"dimensionFilters":{"diet":{"includedValues":["VEGAN"]}},"groupingDimensions":["cuisine"]}' \
  "$base_url/api/populations/food-service-mvp/analysis")
impact=$(curl --fail --silent --show-error --header 'Content-Type: application/json' \
  --data '{"query":{"groupingDimensions":["cuisine"]},"unavailableElementIds":["garlic"]}' \
  "$base_url/api/populations/food-service-mvp/element-unavailability")

assert_contains "$ui" '<app-root></app-root>'
assert_contains "$summary" '"id":"food-service-mvp"'
assert_contains "$summary" '"recordCount":12'
assert_contains "$analysis" '"recordCount":8'
assert_contains "$impact" '"unavailableElementIds":["garlic"]'
assert_contains "$impact" '"relativeDelta":{"recordCount":-0.6666666666666666'

peak_memory_bytes=$("${compose[@]}" exec -T app sh -c "cat /sys/fs/cgroup/memory.peak 2>/dev/null || true")
if [[ "$peak_memory_bytes" =~ ^[0-9]+$ ]]; then
  echo "Observed app cgroup peak memory: $peak_memory_bytes bytes"
else
  echo "Observed app cgroup peak memory: unavailable"
fi

"${compose[@]}" restart app
wait_for_url "$base_url/api/populations/food-service-mvp"
reloaded_summary=$(curl --fail --silent --show-error "$base_url/api/populations/food-service-mvp")
assert_contains "$reloaded_summary" '"recordCount":12'

echo "Compose black-box verification passed."
