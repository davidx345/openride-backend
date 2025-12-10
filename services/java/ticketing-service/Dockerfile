# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy pom and install dependencies
COPY services/java/ticketing-service/pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY services/java/ticketing-service/src ./src

# Build application
RUN mvn clean package -Dmaven.test.skip=true -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Add non-root user
RUN addgroup -S openride && adduser -S openride -G openride

# Copy the built JAR from builder stage
COPY --from=builder /app/target/ticketing-service-*.jar app.jar

# Create directory for cryptographic keys
RUN mkdir -p /etc/openride/keys && \
    chown -R openride:openride /etc/openride

# Switch to non-root user
USER openride

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8086/actuator/health || exit 1

# Expose port
EXPOSE 8086

# Run the application
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "app.jar"]
