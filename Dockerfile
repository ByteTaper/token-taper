# SPDX-FileCopyrightText: 2026 Haluan Irsad
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Commercial

FROM clojure:temurin-21-tools-deps AS builder

WORKDIR /app

COPY deps.edn build.clj ./
COPY src ./src
COPY resources ./resources
COPY migrations ./migrations

RUN clojure -T:build uber

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system --gid 10001 tokentaper \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app tokentaper \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder --chown=tokentaper:tokentaper /app/target/tokentaper.jar /app/tokentaper.jar
COPY --from=builder --chown=tokentaper:tokentaper /app/migrations /app/migrations

ENV TOKEN_TAPER_HTTP_HOST=0.0.0.0 \
    TOKEN_TAPER_MIGRATION_DIR=migrations

USER tokentaper

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/tokentaper.jar"]
CMD ["api"]
