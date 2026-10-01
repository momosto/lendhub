# Local/compose image. CI publishes multi-arch images with Jib instead (./gradlew :api:jib).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts ./
COPY gradle gradle
COPY api/build.gradle.kts api/
RUN ./gradlew --no-daemon :api:dependencies > /dev/null
COPY api/src api/src
RUN ./gradlew --no-daemon :api:bootJar -x test && cp api/build/libs/*.jar /app.jar

FROM eclipse-temurin:21-jre
# uid 1000 is the base image's unprivileged "ubuntu" user
USER 1000
COPY --from=build /app.jar /app/app.jar
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Duser.timezone=UTC"
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
