# Build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# First, build and install java-commons
COPY shared/java-commons/pom.xml ./java-commons/pom.xml
COPY shared/java-commons/src ./java-commons/src
WORKDIR /build/java-commons
RUN mvn clean install -DskipTests

# Now build the service
WORKDIR /app
COPY services/java/auth-service/pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY services/java/auth-service/src ./src

# Build application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S openride && adduser -S openride -G openride

# Copy JAR from builder
COPY --from=builder /app/target/*.jar app.jar

# Change ownership
RUN chown -R openride:openride /app

USER openride

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
