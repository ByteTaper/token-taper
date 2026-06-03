.PHONY: run api migrate test test-integration uber build clean fmt-check fmt-fix fmt

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
