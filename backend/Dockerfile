# ---- Build stage ----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cache dependencies as their own layer (|| true — go-offline can't always
# resolve every plugin; the package step below fetches anything still missing).
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

# Build the executable jar
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# non-root user
RUN addgroup -S ieia && adduser -S ieia -G ieia
RUN mkdir -p /data/reports && chown -R ieia:ieia /data

COPY --from=build /build/target/ieia-backend.jar app.jar
USER ieia

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
