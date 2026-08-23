#!/usr/bin/env bash
set -euo pipefail

mode="${1:-smoke}"
repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
: "${HAMMERLY_LOAD_TEST_BASE_URL:=http://localhost:5000}"

if [[ "${mode}" == "full" && "${PROVIDER_MODE:-}" != "loadtest" \
  && "${ALLOW_LIVE_PROVIDER_LOAD_TEST:-}" != "true" ]]; then
  echo "Full benchmark refused: set PROVIDER_MODE=loadtest." >&2
  exit 2
fi

export HAMMERLY_LOAD_TEST_MODE="${mode}"
export HAMMERLY_LOAD_TEST_BASE_URL
export PHASE5_SUMMARY_EXPORT="${PHASE5_SUMMARY_EXPORT:-${repository_root}/load-test/phase5/results/${mode}-$(date +%Y%m%d-%H%M%S).json}"
mkdir -p "$(dirname "${PHASE5_SUMMARY_EXPORT}")"

if command -v k6 >/dev/null; then
  k6 run "${repository_root}/load-test/phase5/chat-sse.js"
  exit $?
fi

command -v docker >/dev/null || {
  echo "Neither k6 nor Docker is installed." >&2
  exit 2
}

container_base_url="${HAMMERLY_LOAD_TEST_BASE_URL/http:\/\/localhost/http:\/\/host.docker.internal}"
summary_file_name="$(basename "${PHASE5_SUMMARY_EXPORT}")"
cd "${repository_root}"
docker compose --profile loadtest build k6
docker compose --profile loadtest run --rm \
  -e "HAMMERLY_LOAD_TEST_BASE_URL=${container_base_url}" \
  -e "HAMMERLY_LOAD_TEST_MODE=${mode}" \
  -e "PROVIDER_MODE=${PROVIDER_MODE:-}" \
  -e "ALLOW_LIVE_PROVIDER_LOAD_TEST=${ALLOW_LIVE_PROVIDER_LOAD_TEST:-false}" \
  -e "HAMMERLY_LOAD_TEST_TOKEN=${HAMMERLY_LOAD_TEST_TOKEN:-}" \
  -e "PHASE5_SUMMARY_EXPORT=/results/${summary_file_name}" \
  k6 run /workspace/load-test/phase5/chat-sse.js

mounted_summary="${repository_root}/load-test/phase5/results/${summary_file_name}"
if [[ "${mounted_summary}" != "${PHASE5_SUMMARY_EXPORT}" ]]; then
  cp "${mounted_summary}" "${PHASE5_SUMMARY_EXPORT}"
fi
