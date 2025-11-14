FROM eclipse-temurin:17-jre-alpine

# Set working directory
WORKDIR /app

# Create non-root user
RUN addgroup -g 1001 -S openride && \
    adduser -u 1001 -S openride -G openride

# Copy application JAR
COPY target/admin-service-*.jar app.jar

# Change ownership
RUN chown -R openride:openride /app

# Switch to non-root user
USER openride

# Expose port
EXPOSE 8086

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8086/admin-service/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/app/logs/heap-dump.hprof", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "app.jar"]
