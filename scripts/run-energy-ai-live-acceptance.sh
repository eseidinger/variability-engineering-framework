#!/usr/bin/env bash
set -euo pipefail

vef_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
vef_api="$vef_root/apps/prototype/api"

if [[ -z "${OPENAI_API_KEY:-}" ]]; then
    for vef_env_file in "$vef_api/.env" "$vef_root/.env"; do
        if [[ -f "$vef_env_file" ]]; then
            while IFS= read -r vef_line || [[ -n "$vef_line" ]]; do
                if [[ "$vef_line" == OPENAI_API_KEY=* ]]; then
                    OPENAI_API_KEY=${vef_line#OPENAI_API_KEY=}
                    OPENAI_API_KEY=${OPENAI_API_KEY%$'\r'}
                    if [[ "$OPENAI_API_KEY" == \"*\" || "$OPENAI_API_KEY" == \'*\' ]]; then
                        OPENAI_API_KEY=${OPENAI_API_KEY:1:${#OPENAI_API_KEY}-2}
                    fi
                    export OPENAI_API_KEY
                    break 2
                fi
            done < "$vef_env_file"
        fi
    done
fi

if [[ -z "${OPENAI_API_KEY:-}" ]]; then
    printf 'OPENAI_API_KEY is required in the environment or API/root .env.\n' >&2
    exit 2
fi
if ! docker info >/dev/null 2>&1; then
    printf 'Docker access is required for the PostgreSQL test container.\n' >&2
    exit 2
fi

vef_hold=$(mktemp -d /tmp/vef-ai-live-acceptance.XXXXXX)
vef_restore() {
    if [[ -f "$vef_hold/.env" ]]; then
        mv "$vef_hold/.env" "$vef_api/.env"
    fi
    rm -rf "$vef_hold"
}
trap vef_restore EXIT

python3 "$vef_root/scripts/validate-energy-contract-kit.py" \
    --prepare-only --output-dir "$vef_hold/kit"
export ENERGY_CONTRACT_KIT_DIRECTORY="$vef_hold/kit/contract-kit-v1.0.0/contract-kit"
export ENERGY_AI_LIVE_ACCEPTANCE=true
export ENERGY_AI_ENABLED=true

# Local development credentials pin port 5432 and would override Quarkus Dev Services.
if [[ -f "$vef_api/.env" ]]; then
    mv "$vef_api/.env" "$vef_hold/.env"
fi

cd "$vef_api"
./mvnw --batch-mode --no-transfer-progress -Dtest=EnergyAiLiveAcceptanceIT test
printf 'Review validated explanations and model/prompt manifests in %s/target/ai-live-acceptance\n' "$vef_api"
