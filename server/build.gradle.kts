import org.flywaydb.core.Flyway
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration as CodegenConfiguration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target as CodegenTarget
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jooq:jooq-codegen:3.19.24")
        classpath("org.flywaydb:flyway-database-postgresql:11.7.2")
        classpath("org.testcontainers:postgresql:1.21.2")
        classpath("org.postgresql:postgresql:42.7.7")
    }
}

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

group = "app.corkboard"
version = "0.1.0"

System.getenv("GRADLE_BUILD_DIR")?.let { layout.buildDirectory = file(it) }

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.avro:avro:1.12.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    implementation("com.bucket4j:bucket4j-core:8.10.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val jooqGeneratedDir = layout.projectDirectory.dir("generated/jooq")

// the avro plugin generates Java records; Kotlin has to see them on its own source path
val avroGenerated = layout.buildDirectory.dir("generated-main-avro-java")

sourceSets["main"].java.srcDir(avroGenerated)

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
    sourceSets.getByName("main").kotlin.srcDir(jooqGeneratedDir)
    sourceSets.getByName("main").kotlin.srcDir(avroGenerated)
}

tasks.register("jooqCodegen") {
    group = "build"
    description = "Regenerates jOOQ classes from the Flyway migrations via a disposable PostGIS container (needs Docker)."
    inputs.dir(layout.projectDirectory.dir("src/main/resources/db/migration"))
    outputs.dir(jooqGeneratedDir)
    doLast {
        PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                .asCompatibleSubstituteFor("postgres")
        ).use { db ->
            db.start()
            Flyway.configure()
                .dataSource(db.jdbcUrl, db.username, db.password)
                .locations("filesystem:${layout.projectDirectory.dir("src/main/resources/db/migration").asFile}")
                .load()
                .migrate()
            GenerationTool.generate(
                CodegenConfiguration()
                    .withJdbc(
                        Jdbc()
                            .withUrl(db.jdbcUrl)
                            .withUser(db.username)
                            .withPassword(db.password)
                    )
                    .withGenerator(
                        Generator()
                            .withName("org.jooq.codegen.KotlinGenerator")
                            .withDatabase(
                                Database()
                                    .withInputSchema("public")
                                    .withExcludes("spatial_ref_sys|geometry_columns|geography_columns|flyway_schema_history")
                                    .withIncludeRoutines(false)
                                    .withIncludeUDTs(false)
                                    .withTableValuedFunctions(false)
                                    .withForcedTypes(
                                        ForcedType()
                                            .withName("VARCHAR")
                                            .withIncludeTypes("citext")
                                    )
                            )
                            .withTarget(
                                CodegenTarget()
                                    .withPackageName("app.corkboard.jooq")
                                    .withDirectory(jooqGeneratedDir.asFile.absolutePath)
                                    .withClean(true)
                            )
                    )
            )
        }
    }
}

tasks.named("compileKotlin") {
    mustRunAfter("jooqCodegen")
    dependsOn("generateAvroJava")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
