FROM node:22-alpine AS web
WORKDIR /build
RUN corepack enable
COPY web/package.json web/pnpm-lock.yaml web/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY web/ ./
ARG MAP_STYLE_URL
ARG DEFAULT_CENTER
RUN MAP_STYLE_URL=$MAP_STYLE_URL DEFAULT_CENTER=$DEFAULT_CENTER pnpm build

FROM eclipse-temurin:21-jdk AS server
WORKDIR /build
COPY server/ ./
COPY --from=web /build/dist/ src/main/resources/static/
RUN test -d generated/jooq || (echo "server/generated/jooq is missing — run ./gradlew jooqCodegen first" >&2; exit 1)
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --create-home corkboard
COPY --from=server --chown=10001:10001 /build/build/libs/*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
