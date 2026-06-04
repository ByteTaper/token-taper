DOCKER_COMPOSE ?= docker-compose

.PHONY: run api migrate test test-integration uber build clean fmt-check fmt-fix fmt \
	docker-build docker-up docker-migrate docker-down docker-smoke

run api:
	clojure -M:run api

migrate:
	clojure -M:run migrate

test:
	clojure -M:test

test-integration:
	clojure -M:test-integration

uber build:
	clojure -T:build uber

clean:
	clojure -T:build clean

fmt-check:
	clojure -T:fmt fmt-check

fmt-fix fmt:
	clojure -T:fmt fmt-fix

docker-build:
	$(DOCKER_COMPOSE) build

docker-up:
	$(DOCKER_COMPOSE) up -d postgres
	$(DOCKER_COMPOSE) run --rm tokentaper-api migrate
	$(DOCKER_COMPOSE) up -d tokentaper-api

docker-migrate:
	$(DOCKER_COMPOSE) run --rm tokentaper-api migrate

docker-down:
	$(DOCKER_COMPOSE) down

docker-smoke:
	./scripts/smoke-v0.1.sh
