FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Add non-root user
RUN addgroup -S openride && adduser -S openride -G openride

# Copy the built JAR
COPY target/ticketing-service-*.jar app.jar

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
