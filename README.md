# TokenTaper

Standalone Clojure backend service for AI cost governance.

## Development

Run API mode (starts Jetty HTTP server on port 8080 by default):

```bash
clojure -M:run api
```

HTTP endpoints:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health/live` | Liveness (process alive; no DB checks) |
| GET | `/health/ready` | Readiness (config, system, PostgreSQL, migrations) |
| GET | `/metrics` | Prometheus metrics (text exposition) |
| GET | `/v1/system/info` | Service metadata |

**Health** (`/health/live`, `/health/ready`) and **system info** (`/v1/system/info`) return **flat JSON** with no `{data, error}` envelope. Future `/v1/*` business APIs may use the envelope pattern.

- **Live** — always `200` while the HTTP server is running; does not check PostgreSQL.
- **Ready** — `200` when config, system, database, and `schema_migrations` checks pass; `503` otherwise.
- **System info** — safe service, build, and runtime metadata (`service`, `name`, `version`, `environment`, `build`, `runtime`). No secrets or raw config.

Build metadata defaults come from [`resources/build-info.edn`](resources/build-info.edn). Override the deployed git SHA with `TOKEN_TAPER_GIT_SHA` (also used for Prometheus `tokentaper_build_info`). Application version follows `TOKEN_TAPER_VERSION` / config.

```bash
curl -i http://localhost:8080/health/live
curl -i http://localhost:8080/health/ready
curl -i http://localhost:8080/metrics
curl -i http://localhost:8080/v1/system/info
curl -s http://localhost:8080/v1/system/info | jq .service,.version,.runtime.jvm
```

Prometheus metrics (`GET /metrics`) return **plain text** (`text/plain; version=0.0.4`), not JSON. Metric names use the `tokentaper_` prefix (for example `tokentaper_uptime_seconds`, `tokentaper_http_requests_total`, `tokentaper_database_ready`). Optional `TOKEN_TAPER_GIT_SHA` sets the `git_sha` label on `tokentaper_build_info`.

```bash
curl -s http://localhost:8080/metrics | grep tokentaper_uptime_seconds
curl -s http://localhost:8080/metrics | grep tokentaper_http_requests_total
curl -s http://localhost:8080/metrics | grep tokentaper_database_ready
```

Optional: `TOKEN_TAPER_HEALTH_DATABASE_TIMEOUT_MS` (default `1000`) bounds readiness DB query time.

## Structured logging

The service writes **NDJSON** (one JSON object per line) to **stderr**. Each line includes standard fields: `timestamp`, `level`, `event`, `service` (`tokentaper`), `version`, and `env`.

HTTP requests get an `X-Request-Id` header (generated as `req_<uuid>` when absent). On completion, a `http_request_completed` event is logged with `request_id`, `method`, `path`, `route`, `status`, and `duration_ms`. Uncaught exceptions log `unhandled_exception` before returning a structured 500.

Set log verbosity with `TOKEN_TAPER_LOG_LEVEL` (`debug`, `info`, `warn`, `error`, `fatal`; default `info`). Sensitive header names and connection strings are redacted as `[REDACTED]`.

Example line:

```json
{"timestamp":"2026-06-02T12:00:00Z","level":"info","event":"http_request_completed","service":"tokentaper","version":"0.1.0-SNAPSHOT","env":"local","request_id":"req_…","method":"GET","path":"/health/live","route":"/health/live","status":200,"duration_ms":1}
```

Run database migrations (Migratus; does not start the HTTP server):

```bash
clojure -M:run migrate
# or: clojure -M:migrate
# or: make migrate
```

Migrations live in [`migrations/`](migrations/) at the project root. Run from the repo root (or set `TOKEN_TAPER_MIGRATION_DIR`). Uses the same `TOKEN_TAPER_DATABASE_*` settings as the API (`resources/config.edn`). The uberjar expects `migrations/` on the working directory or `TOKEN_TAPER_MIGRATION_DIR` when you run `java -jar … migrate`.

Run tests (unit tests only; PostgreSQL not required):

```bash
clojure -M:test
```

Run integration tests when PostgreSQL is available:

```bash
TOKEN_TAPER_TEST_DATABASE_URL="jdbc:postgresql://localhost:5432/token_taper_test" \
TOKEN_TAPER_TEST_DATABASE_USER="token_taper" \
TOKEN_TAPER_TEST_DATABASE_PASSWORD="token_taper" \
clojure -M:test-integration
```

Integration tests include migration smoke checks (`^:integration` in `test/token_taper/db/migration_test.clj`) when `TOKEN_TAPER_TEST_DATABASE_URL` is set.

Database configuration uses `TOKEN_TAPER_DATABASE_*` environment variables (see `resources/config.edn`). The connection pool is created on startup but does not fail fast if PostgreSQL is unreachable until the first query. Migration mode verifies connectivity before applying SQL.

Check Clojure formatting ([cljfmt](https://github.com/weavejester/cljfmt)):

```bash
clojure -T:fmt fmt-check    # or: make fmt-check
clojure -T:fmt fmt-fix      # or: make fmt / make fmt-fix
```

Formatting applies to `src/`, `test/`, and `build.clj` (see `.cljfmt.edn`). EDN files under `resources/` are not formatted.

Build uberjar:

```bash
clojure -T:build uber
```

Optional configuration overrides:

```bash
TOKEN_TAPER_CONFIG=resources/config.test.edn clojure -M:run api
TOKEN_TAPER_ENV=local TOKEN_TAPER_HTTP_PORT=8080 clojure -M:run api
TOKEN_TAPER_CONFIG=resources/config.local.edn clojure -M:run api
```

Equivalent `make` targets (`run`/`api`, `migrate`, `test`, `test-integration`, `fmt-check`, `fmt`/`fmt-fix`, `uber`/`build`, `clean`) wrap the same commands.

## License

This tool is licensed under:

- `AGPL-3.0-only`, or
- `LicenseRef-Commercial`

See repository license files and source SPDX headers for details. See
`LICENSES/` for full license texts.
