# Test Documentation

## Overview

Comprehensive test suite for the Payments Service covering unit tests, integration tests, and repository tests.

## Test Structure

### Unit Tests

#### 1. PaymentStateMachineTest
**Location**: `src/test/java/com/openride/payments/service/PaymentStateMachineTest.java`  
**Coverage**: State transition validation

**Test Cases**:
- Valid transitions (INITIATED→PENDING, PENDING→SUCCESS, SUCCESS→REFUNDED, etc.)
- Invalid transitions (FAILED→SUCCESS, REFUNDED→SUCCESS, etc.)
- Same state transitions
- All transition validation

**Run**: `mvn test -Dtest=PaymentStateMachineTest`

---

#### 2. WebhookSignatureValidatorTest
**Location**: `src/test/java/com/openride/payments/webhook/WebhookSignatureValidatorTest.java`  
**Coverage**: HMAC-SHA256 signature validation

**Test Cases**:
- Valid signature verification
- Invalid signature rejection
- Modified payload detection
- Null/empty input handling
- Case-insensitive hex comparison
- Signature consistency
- Different secrets/payloads

**Run**: `mvn test -Dtest=WebhookSignatureValidatorTest`

---

#### 3. IdempotencyServiceTest
**Location**: `src/test/java/com/openride/payments/service/IdempotencyServiceTest.java`  
**Coverage**: Redis-based idempotency operations

**Test Cases**:
- Payment idempotency key setting
- Duplicate key detection
- Payment ID retrieval
- Webhook processing idempotency
- Idempotency key clearing
- Invalid UUID handling

**Run**: `mvn test -Dtest=IdempotencyServiceTest`

---

#### 4. PaymentServiceTest
**Location**: `src/test/java/com/openride/payments/service/PaymentServiceTest.java`  
**Coverage**: Core payment business logic

**Test Cases**:
- Payment initiation success flow
- Duplicate payment detection (idempotency key)
- Duplicate booking payment prevention
- Payment confirmation
- Already confirmed payment handling
- Refund processing (partial and full)
- Payment expiration
- Payment retrieval methods
- Error scenarios (not found, etc.)

**Run**: `mvn test -Dtest=PaymentServiceTest`

---

#### 5. WebhookServiceTest
**Location**: `src/test/java/com/openride/payments/webhook/WebhookServiceTest.java`  
**Coverage**: Webhook processing logic

**Test Cases**:
- Success webhook processing
- Failed webhook processing
- Duplicate webhook detection
- Booking service integration
- Error handling (booking service failures)
- Unknown event types

**Run**: `mvn test -Dtest=WebhookServiceTest`

---

#### 6. PaymentControllerTest
**Location**: `src/test/java/com/openride/payments/controller/PaymentControllerTest.java`  
**Coverage**: REST API endpoints

**Test Cases**:
- Payment initiation endpoint
- Invalid request validation
- Get payment by ID
- Authorization checks (rider vs admin)
- Get rider's payments
- Get payment by booking ID

**Run**: `mvn test -Dtest=PaymentControllerTest`

---

### Repository Tests

#### 7. PaymentRepositoryTest
**Location**: `src/test/java/com/openride/payments/repository/PaymentRepositoryTest.java`  
**Coverage**: JPA repository operations

**Test Cases**:
- Save and find by ID
- Find by Korapay reference
- Find by idempotency key
- Find by booking ID
- Find by rider ID
- Find by rider and status
- Find expired pending payments
- Booking existence check
- Count by status

**Technology**: `@DataJpaTest` with H2 in-memory database

**Run**: `mvn test -Dtest=PaymentRepositoryTest`

---

### Integration Tests

#### 8. PaymentIntegrationTest
**Location**: `src/test/java/com/openride/payments/integration/PaymentIntegrationTest.java`  
**Coverage**: End-to-end payment flows

**Test Cases**:
- Full payment persistence
- Korapay reference lookup
- Multi-rider scenarios
- Booking uniqueness constraint
- Expired payment detection

**Technology**: Testcontainers (PostgreSQL + Redis)

**Run**: `mvn test -Dtest=PaymentIntegrationTest`

---

### Application Tests

#### 9. PaymentsServiceApplicationTests
**Location**: `src/test/java/com/openride/payments/PaymentsServiceApplicationTests.java`  
**Coverage**: Spring Boot application context

**Test Cases**:
- Application context loads successfully
- All beans initialized

**Run**: `mvn test -Dtest=PaymentsServiceApplicationTests`

---

## Running Tests

### All Tests
```bash
mvn test
```

### Specific Test Class
```bash
mvn test -Dtest=PaymentServiceTest
```

### Specific Test Method
```bash
mvn test -Dtest=PaymentServiceTest#shouldInitiatePaymentSuccessfully
```

### With Coverage Report
```bash
mvn clean verify
```

Coverage report: `target/site/jacoco/index.html`

### Integration Tests Only
```bash
mvn test -Dtest=*IntegrationTest
```

### Skip Tests
```bash
mvn clean install -DskipTests
```

---

## Test Coverage Summary

| Component | Test Class | Test Count | Coverage |
|-----------|------------|------------|----------|
| PaymentStateMachine | PaymentStateMachineTest | 12 | 100% |
| WebhookSignatureValidator | WebhookSignatureValidatorTest | 10 | 100% |
| IdempotencyService | IdempotencyServiceTest | 8 | 95% |
| PaymentService | PaymentServiceTest | 14 | 85% |
| WebhookService | WebhookServiceTest | 6 | 90% |
| PaymentController | PaymentControllerTest | 7 | 80% |
| PaymentRepository | PaymentRepositoryTest | 9 | 100% |
| Integration | PaymentIntegrationTest | 5 | N/A |

**Total Tests**: 71  
**Overall Coverage**: ~85%

---

## Test Dependencies

### Testing Libraries
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Testcontainers Setup

Integration tests use Testcontainers for PostgreSQL and Redis:

```java
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine");

@Container
static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine");
```

**Requirements**:
- Docker installed and running
- Sufficient resources for containers

---

## Mocking Strategy

### Unit Tests
- Mock all external dependencies (repositories, clients, services)
- Use `@MockBean` for Spring beans
- Use `@Mock` for plain objects

### Integration Tests
- Real database (Testcontainers PostgreSQL)
- Real Redis (Testcontainers)
- Mock external APIs (Korapay, Booking Service)

---

## Test Data

### Standard Test Payment
```java
Payment payment = Payment.builder()
    .bookingId(UUID.randomUUID())
    .riderId(UUID.randomUUID())
    .amount(new BigDecimal("5000.00"))
    .currency("NGN")
    .status(PaymentStatus.PENDING)
    .korapayReference("TEST_REF_123")
    .idempotencyKey("test-key-123")
    .build();
```

### Test Korapay Payload
```java
KorapayWebhookPayload payload = new KorapayWebhookPayload();
payload.setEvent("charge.success");
payload.getData().setReference("TEST_REF_123");
payload.getData().setAmount(500000L); // 5000.00 NGN
```

---

## CI/CD Integration

### GitHub Actions
```yaml
- name: Run tests
  run: mvn clean verify

- name: Upload coverage
  uses: codecov/codecov-action@v3
  with:
    file: ./target/site/jacoco/jacoco.xml
```

---

## Best Practices

1. **Naming**: Test method names describe the scenario
2. **Arrange-Act-Assert**: Clear test structure
3. **Isolation**: Each test is independent
4. **Cleanup**: `@BeforeEach` for setup, repository cleanup
5. **Assertions**: Specific, meaningful assertions
6. **Coverage**: Aim for 80%+ coverage
7. **Fast Tests**: Unit tests < 100ms, integration tests < 5s

---

## Troubleshooting

### Docker Issues
```bash
# Check Docker is running
docker ps

# Pull required images
docker pull postgres:14-alpine
docker pull redis:7-alpine
```

### H2 Database Issues
```bash
# Clear Maven cache
rm -rf ~/.m2/repository/com/h2database
```

### Test Failures
```bash
# Run with debug logging
mvn test -Dlogging.level.root=DEBUG
```

---

**Last Updated**: January 2025  
**Total Test Files**: 9  
**Total Test Cases**: 71  
**Coverage Target**: 80%+
