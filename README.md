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
| GET | `/health/live` | Liveness |
| GET | `/health/ready` | Readiness (HTTP only in v0.1) |
| GET | `/v1/system/info` | Service metadata |

```bash
curl -i http://localhost:8080/health/live
curl -i http://localhost:8080/health/ready
curl -i http://localhost:8080/v1/system/info
```

Run placeholder migration mode:

```bash
clojure -M:run migrate
```

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

Database configuration uses `TOKEN_TAPER_DATABASE_*` environment variables (see `resources/config.edn`). The connection pool is created on startup but does not fail fast if PostgreSQL is unreachable until the first query.

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
