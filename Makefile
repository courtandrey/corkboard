.PHONY: up down seed types jooq check

-include .env
API_PORT ?= 8080

up:
	docker compose up

down:
	docker compose down

types:
	cd web && pnpm exec openapi-typescript http://localhost:$(API_PORT)/api/v1/openapi.json -o src/api/types.gen.ts

jooq:
	cd server && ./gradlew jooqCodegen

check:
	cd server && ./gradlew check
	cd web && pnpm typecheck
	cd web && pnpm test

seed:
	docker compose stop api
	docker compose run --rm api ./gradlew --no-daemon --project-cache-dir /root/.gradle/project-cache bootRun --args='--spring.profiles.active=seed'
	docker compose start api
