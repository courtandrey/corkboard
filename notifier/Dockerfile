FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY . .
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10002 --create-home notifier
COPY --from=build --chown=10002:10002 /build/build/libs/*.jar app.jar
USER 10002
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
