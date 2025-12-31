# Multi-stage Dockerfile for GraalVM native image

# Stage 1: Build with GraalVM
FROM ghcr.io/graalvm/graalvm-ce:ol9-java25 AS builder

WORKDIR /app

# Install Maven
RUN microdnf install -y findutils tar gzip && \
    curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz | tar xz -C /opt && \
    ln -s /opt/apache-maven-3.9.6/bin/mvn /usr/local/bin/mvn

# Copy Maven files
COPY pom.xml ./
COPY src ./src

# Build the application (JVM mode first for faster development)
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Copy the JAR from builder
COPY --from=builder /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

# Optional: Native image build (uncomment to use)
# Stage 1 (alternative): Native build
# FROM ghcr.io/graalvm/graalvm-ce:ol9-java25 AS native-builder
#
# WORKDIR /app
#
# RUN microdnf install -y gcc glibc-devel zlib-devel findutils tar gzip && \
#     curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz | tar xz -C /opt && \
#     ln -s /opt/apache-maven-3.9.6/bin/mvn /usr/local/bin/mvn
#
# COPY pom.xml ./
# COPY src ./src
#
# RUN mvn -Pnative native:compile -DskipTests
#
# Stage 2 (alternative): Native runtime
# FROM alpine:3.19
#
# WORKDIR /app
#
# RUN apk add --no-cache gcompat libstdc++
#
# COPY --from=native-builder /app/target/availability-tests /app/availability-tests
#
# EXPOSE 8080
#
# ENTRYPOINT ["/app/availability-tests"]
