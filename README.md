# TokenTaper

Standalone Clojure backend service for AI cost governance.

## Development

Run placeholder API mode:

```bash
clojure -M:run api
```

Run placeholder migration mode:

```bash
clojure -M:run migrate
```

Run tests:

```bash
clojure -M:test
```

Build uberjar:

```bash
clojure -T:build uber
```

Equivalent `make` targets (`run`/`api`, `migrate`, `test`, `uber`/`build`, `clean`) wrap the same commands.

## License

This tool is licensed under:

- `AGPL-3.0-only`, or
- `LicenseRef-Commercial`

See repository license files and source SPDX headers for details. See
`LICENSES/` for full license texts.
