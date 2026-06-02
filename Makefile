.PHONY: run api migrate test uber build clean fmt-check fmt-fix fmt

run api:
	clojure -M:run api

migrate:
	clojure -M:run migrate

test:
	clojure -M:test

uber build:
	clojure -T:build uber

clean:
	clojure -T:build clean

fmt-check:
	clojure -T:fmt fmt-check

fmt-fix fmt:
	clojure -T:fmt fmt-fix
