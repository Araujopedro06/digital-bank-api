# Build and run the API as one image. Multi-stage so the runtime carries a JRE
# and the jar, not Maven and the whole dependency cache.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies resolve in their own layer, so a source-only change does not
# re-download the world.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# A non-root user, because the container has no reason to run as root.
RUN addgroup -S bank && adduser -S bank -G bank
USER bank

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod

# Respect the container's memory limit instead of assuming the host's.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
