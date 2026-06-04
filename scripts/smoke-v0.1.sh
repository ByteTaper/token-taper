#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Haluan Irsad
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "Smoke testing TokenTaper at ${BASE_URL}"

curl -sf "${BASE_URL}/health/live" >/dev/null
echo "OK /health/live"

curl -sf "${BASE_URL}/health/ready" >/dev/null
echo "OK /health/ready"

metrics="$(curl -sf "${BASE_URL}/metrics")"
echo "${metrics}" | grep -q "tokentaper_uptime_seconds"
echo "OK /metrics"

if command -v jq >/dev/null 2>&1; then
  service="$(curl -sf "${BASE_URL}/v1/system/info" | jq -r .service)"
  [[ "${service}" == "tokentaper" ]] || {
    echo "expected service=tokentaper, got ${service}" >&2
    exit 1
  }
else
  curl -sf "${BASE_URL}/v1/system/info" | grep -q '"service":"tokentaper"'
fi
echo "OK /v1/system/info"

echo "Smoke checks passed."
