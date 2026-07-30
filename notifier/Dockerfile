FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY . .
RUN test -d generated/jooq || (echo "notifier/generated/jooq is missing — run ./gradlew jooqCodegen first (see deploy/DEPLOY.md)" >&2; exit 1)
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10002 --create-home notifier
COPY --from=build --chown=10002:10002 /build/build/libs/*.jar app.jar
USER 10002
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
