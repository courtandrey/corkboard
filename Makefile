.PHONY: up down seed types jooq check deploy prod-logs prod-ps prod-down prod-seed backup topics dlt

-include .env
API_PORT ?= 8080
PROD := docker compose -f compose.prod.yml

up:
	docker compose up

down:
	docker compose down

types:
	cd web && pnpm exec openapi-typescript http://localhost:$(API_PORT)/api/v1/openapi.json -o src/api/types.gen.ts

jooq:
	cd server && ./gradlew jooqCodegen
	cd notifier && ./gradlew jooqCodegen

check:
	cd server && ./gradlew check
	cd notifier && ./gradlew check
	cd web && pnpm typecheck
	cd web && pnpm test

seed:
	docker compose stop api
	docker compose run --rm api ./gradlew --no-daemon --project-cache-dir /root/.gradle/project-cache bootRun --args='--spring.profiles.active=seed'
	docker compose start api

topics:
	docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

dlt:
	docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
		--topic corkboard.emails.v1.DLT --from-beginning --timeout-ms 5000

deploy:
	./deploy/deploy.sh

prod-ps:
	$(PROD) ps

prod-logs:
	$(PROD) logs -f

prod-down:
	$(PROD) down

prod-seed:
	$(PROD) run --rm -e SPRING_PROFILES_ACTIVE=seed api

backup:
	./deploy/backup.sh
