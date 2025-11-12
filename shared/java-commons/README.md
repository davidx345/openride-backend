# OpenRide Java Commons

Shared utilities and common code for OpenRide Java microservices.

## Modules

### 1. Exception Handling
- `BusinessException`: For business logic errors (expected errors)
- `TechnicalException`: For system/technical errors (unexpected errors)

### 2. Security
- `JwtUtil`: JWT token generation, validation, and claims extraction

### 3. Response
- `ApiResponse`: Standard API response wrapper for consistent responses

### 4. Logging
- `CorrelationIdFilter`: Adds correlation IDs to requests for distributed tracing

### 5. Configuration
- `OpenApiConfig`: Base OpenAPI/Swagger configuration

## Usage

Add this dependency to your service's `pom.xml`:

```xml
<dependency>
    <groupId>com.openride</groupId>
    <artifactId>java-commons</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Installation

```bash
cd shared/java-commons
mvn clean install
```

This will install the library to your local Maven repository.
