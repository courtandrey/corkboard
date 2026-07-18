FROM node:22-alpine AS web
WORKDIR /build
RUN corepack enable
COPY web/package.json web/pnpm-lock.yaml web/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY web/ ./
ARG MAP_STYLE_URL
ARG DEFAULT_CENTER
RUN MAP_STYLE_URL=$MAP_STYLE_URL DEFAULT_CENTER=$DEFAULT_CENTER pnpm build

# jOOQ sources must exist in server/generated (run ./gradlew jooqCodegen on the
# host first — codegen needs Docker and cannot run inside this build).
FROM eclipse-temurin:21-jdk AS server
WORKDIR /build
COPY server/ ./
COPY --from=web /build/dist/ src/main/resources/static/
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=server /build/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
