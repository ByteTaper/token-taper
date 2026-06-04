#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Haluan Irsad
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
BODY_FILE="$(mktemp)"
trap 'rm -f "${BODY_FILE}"' EXIT

log() {
  printf '[smoke-v0.1] %s\n' "$1"
}

fail() {
  printf '[smoke-v0.1] ERROR: %s\n' "$1" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

http_status() {
  local path="$1"
  local status

  if ! status="$(curl -sS -o "${BODY_FILE}" -w "%{http_code}" "${BASE_URL}${path}")"; then
    fail "Request failed for ${path} (is the API running at ${BASE_URL}?)"
  fi

  printf '%s' "$status"
}

assert_status() {
  local path="$1"
  local expected="$2"
  local status

  status="$(http_status "$path")"

  if [ "$status" != "$expected" ]; then
    printf '[smoke-v0.1] Response body for %s:\n' "$path" >&2
    cat "${BODY_FILE}" >&2 || true
    printf '\n' >&2
    fail "Expected ${path} to return ${expected}, got ${status}"
  fi

  log "OK ${path} -> ${status}"
}

assert_body_contains() {
  local path="$1"
  local expected="$2"

  curl -sS "${BASE_URL}${path}" | grep -q "$expected" \
    || fail "Expected ${path} body to contain: ${expected}"

  log "OK ${path} contains ${expected}"
}

require_cmd curl
require_cmd grep

log "Using BASE_URL=${BASE_URL}"

assert_status "/health/live" "200"
assert_status "/health/ready" "200"
assert_status "/metrics" "200"
assert_status "/v1/system/info" "200"

for path in /health/live /health/ready /v1/system/info /metrics; do
  curl -sf "${BASE_URL}${path}" >/dev/null
done

METRICS_BODY="$(curl -sf "${BASE_URL}/metrics")"
printf '%s' "${METRICS_BODY}" | grep -Fq "tokentaper_uptime_seconds" \
  || fail "Expected /metrics body to contain: tokentaper_uptime_seconds"
log "OK /metrics contains tokentaper_uptime_seconds"

printf '%s' "${METRICS_BODY}" | grep -Fq "tokentaper_http_requests_total" \
  || fail "Expected /metrics body to contain: tokentaper_http_requests_total"
log "OK /metrics contains tokentaper_http_requests_total"

printf '%s' "${METRICS_BODY}" | grep -Fq "tokentaper_database_ready" \
  || fail "Expected /metrics body to contain: tokentaper_database_ready"
log "OK /metrics contains tokentaper_database_ready"

assert_body_contains "/v1/system/info" "tokentaper"

log "v0.1 smoke test passed"
