.PHONY: run api migrate test uber build clean

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
