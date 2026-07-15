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

seed:
	docker compose run --rm --no-deps api ./gradlew --no-daemon bootRun --args='--spring.profiles.active=seed'
