plugins {
    java
    jacoco
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.cloud.tools.jib") version "3.4.3"
    id("info.solidsoft.pitest") version "1.15.0"
}

group = "zw.insurehub"
version = (findProperty("appVersion") as String?) ?: "0.1.0-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

extra["springModulithVersion"] = "1.4.13"
// Boot 3.5.16 ships Tomcat 10.1.55; 10.1.58+ fixes CVE-2026-65182, -65905, -68525 (found by the CI Trivy gate)
extra["tomcat.version"] = "10.1.60"

dependencyManagement {
    imports { mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")
    implementation("org.hibernate.orm:hibernate-envers")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.batch:spring-batch-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("net.jqwik:jqwik:1.9.1")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform { includeEngines("junit-jupiter", "jqwik") }
    systemProperty("jqwik.tries.default", findProperty("jqwikTries") ?: "1000")
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required = true; html.required = true }
}

// Mutation testing for the money modules (docs/05-test-strategy.md): ./gradlew :api:pitest
pitest {
    junit5PluginVersion = "1.2.1"
    targetClasses = setOf(
        "zw.insurehub.lendhub.products.schedule.*",
        "zw.insurehub.lendhub.loans.domain.*",
        "zw.insurehub.lendhub.provisioning.domain.*",
        "zw.insurehub.lendhub.shared.money.*")
    targetTests = setOf("zw.insurehub.lendhub.*Test", "zw.insurehub.lendhub.*Properties")
    excludedTestClasses = setOf("*IntegrationTest", "*ModularityTest")
    mutationThreshold = 70
    threads = 4
    outputFormats = setOf("HTML", "XML")
    timestampedReports = false
}

// Multi-arch image without a Dockerfile (ADR in README): ./gradlew :api:jib -Pimage=ghcr.io/momosto/lendhub-api
jib {
    from {
        image = "eclipse-temurin:21-jre"
        platforms {
            platform { architecture = "amd64"; os = "linux" }
            platform { architecture = "arm64"; os = "linux" }
        }
    }
    to {
        image = (findProperty("image") as String?) ?: "ghcr.io/momosto/lendhub-api"
        tags = setOf(version.toString(), "latest")
    }
    container {
        ports = listOf("8080")
        user = "1000:1000"
        jvmFlags = listOf("-XX:MaxRAMPercentage=75", "-XX:+UseSerialGC", "-Duser.timezone=UTC")
        creationTime = "USE_CURRENT_TIMESTAMP"
    }
}
