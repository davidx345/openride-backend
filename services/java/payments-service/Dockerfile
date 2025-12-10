# Multi-stage build for payments-service
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy shared commons
COPY shared/java-commons /build/shared/java-commons

# Copy pom.xml and download dependencies
COPY services/java/payments-service/pom.xml ./
RUN mvn dependency:go-offline -B

# Copy source and build
COPY services/java/payments-service/src ./src
# Build application
RUN mvn clean package -Dmaven.test.skip=true -B

# Runtime stage

# Production image
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -g 1001 -S openride && \
    adduser -u 1001 -S openride -G openride

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar
RUN chown -R openride:openride /app

USER openride

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8084/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC"

EXPOSE 8084

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
