# Multi-stage build for Booking Service

# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build

WORKDIR /build

# First, build and install java-commons
COPY shared/java-commons/pom.xml ./java-commons/pom.xml
COPY shared/java-commons/src ./java-commons/src
WORKDIR /build/java-commons
RUN mvn clean install -DskipTests

# Now build the service
WORKDIR /app
COPY services/java/booking-service/pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY services/java/booking-service/src ./src

# Build application
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -g 1001 openride && \
    adduser -u 1001 -G openride -s /bin/sh -D openride

# Copy JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Change ownership
RUN chown -R openride:openride /app

# Switch to non-root user
USER openride

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8083/api/actuator/health || exit 1

# Expose port
EXPOSE 8083

# JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -Djava.security.egd=file:/dev/./urandom"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
