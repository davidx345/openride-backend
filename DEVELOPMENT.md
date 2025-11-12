# OpenRide Backend Development Guidelines

This document outlines the development standards, conventions, and best practices for the OpenRide backend project.

## Table of Contents

1. [Code Style Conventions](#code-style-conventions)
2. [Git Workflow](#git-workflow)
3. [Testing Requirements](#testing-requirements)
4. [Logging Standards](#logging-standards)
5. [Error Handling Patterns](#error-handling-patterns)
6. [API Versioning Strategy](#api-versioning-strategy)
7. [Security Best Practices](#security-best-practices)
8. [Performance Guidelines](#performance-guidelines)
9. [Documentation Requirements](#documentation-requirements)

---

## Code Style Conventions

### Java Services

**Style Guide**: Google Java Style Guide

**Key Conventions**:
- **Indentation**: 2 spaces (no tabs)
- **Line Length**: Maximum 100 characters
- **Naming**:
  - Classes: `PascalCase` (e.g., `BookingService`)
  - Methods: `camelCase` (e.g., `createBooking()`)
  - Variables: `camelCase` (e.g., `userId`)
  - Constants: `UPPER_SNAKE_CASE` (e.g., `MAX_RETRY_ATTEMPTS`)
  - Packages: `lowercase` (e.g., `com.openride.booking`)

**File Organization**:
```
src/
├── main/
│   ├── java/
│   │   └── com/openride/{service}/
│   │       ├── controller/
│   │       ├── service/
│   │       ├── repository/
│   │       ├── model/
│   │       ├── dto/
│   │       ├── exception/
│   │       ├── config/
│   │       └── util/
│   └── resources/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-prod.yml
│       └── db/migration/  (Flyway migrations)
└── test/
    └── java/
        └── com/openride/{service}/
```

**Tools**:
- **Formatter**: Use Spotless Maven plugin
- **Linter**: Checkstyle with Google checks
- **Static Analysis**: SonarLint

### Python Services

**Style Guide**: PEP 8

**Key Conventions**:
- **Indentation**: 4 spaces (no tabs)
- **Line Length**: Maximum 100 characters
- **Naming**:
  - Classes: `PascalCase` (e.g., `MatchingService`)
  - Functions: `snake_case` (e.g., `create_booking()`)
  - Variables: `snake_case` (e.g., `user_id`)
  - Constants: `UPPER_SNAKE_CASE` (e.g., `MAX_RETRY_ATTEMPTS`)
  - Private methods: `_snake_case` (e.g., `_calculate_score()`)

**File Organization**:
```
{service-name}/
├── app/
│   ├── main.py
│   ├── config.py
│   ├── dependencies.py
│   ├── api/
│   │   └── v1/
│   │       ├── endpoints/
│   │       └── schemas/
│   ├── core/
│   │   ├── models/
│   │   ├── services/
│   │   └── repositories/
│   └── utils/
├── alembic/  (database migrations)
├── tests/
├── requirements.txt
├── pyproject.toml
└── Dockerfile
```

**Tools**:
- **Formatter**: Black (line length 100)
- **Linter**: Flake8
- **Type Checker**: mypy
- **Import Sorter**: isort

---

## Git Workflow

### Branch Naming

- **Feature branches**: `feature/{ticket-number}-{short-description}`
  - Example: `feature/OR-123-add-booking-service`
- **Bug fixes**: `bugfix/{ticket-number}-{short-description}`
  - Example: `bugfix/OR-456-fix-payment-validation`
- **Hotfixes**: `hotfix/{ticket-number}-{short-description}`
  - Example: `hotfix/OR-789-critical-security-fix`
- **Release branches**: `release/{version}`
  - Example: `release/1.2.0`

### Commit Message Format

Follow Conventional Commits specification:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, no logic change)
- `refactor`: Code refactoring
- `perf`: Performance improvements
- `test`: Adding or updating tests
- `chore`: Build process or auxiliary tool changes

**Example**:
```
feat(booking): add seat hold mechanism with Redis TTL

- Implement Redis-based seat hold with 10-minute TTL
- Add pessimistic locking with SELECT FOR UPDATE
- Handle race conditions during concurrent bookings

Closes OR-123
```

### Pull Request Process

1. **Create PR** with descriptive title and description
2. **Link to ticket** (e.g., "Closes OR-123")
3. **Self-review** your changes before requesting review
4. **Ensure CI passes** (tests, linting, security scans)
5. **Request review** from at least 2 team members
6. **Address feedback** and update PR
7. **Squash and merge** once approved

### PR Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Related Tickets
Closes OR-XXX

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing performed

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Comments added for complex logic
- [ ] Documentation updated
- [ ] No console.log or debug statements
- [ ] All tests pass
```

---

## Testing Requirements

### Coverage Requirements

- **Minimum coverage**: 80% for all services
- **Critical paths**: 95%+ coverage (booking, payments, ticketing)
- **New code**: Must include tests

### Test Types

#### 1. Unit Tests
- Test individual methods/functions in isolation
- Mock external dependencies
- Fast execution (<1s per test)

**Java Example**:
```java
@Test
void shouldCreateBookingSuccessfully() {
    // Arrange
    BookingRequest request = new BookingRequest(...);
    when(repository.save(any())).thenReturn(booking);
    
    // Act
    Booking result = bookingService.createBooking(request);
    
    // Assert
    assertNotNull(result);
    assertEquals(BookingStatus.PENDING, result.getStatus());
}
```

**Python Example**:
```python
@pytest.mark.asyncio
async def test_create_booking_successfully():
    # Arrange
    request = BookingRequest(...)
    mock_repo.save = AsyncMock(return_value=booking)
    
    # Act
    result = await booking_service.create_booking(request)
    
    # Assert
    assert result is not None
    assert result.status == BookingStatus.PENDING
```

#### 2. Integration Tests
- Test interactions between components
- Use test database/Redis
- Test API endpoints end-to-end

#### 3. Edge Case Tests
- Boundary conditions
- Invalid inputs
- Error scenarios

#### 4. Negative Tests
- Test failure paths
- Exception handling
- Validation errors

### Test Organization

```
tests/
├── unit/
│   ├── services/
│   ├── repositories/
│   └── utils/
├── integration/
│   ├── api/
│   └── database/
└── fixtures/
    └── test_data.py
```

---

## Logging Standards

### Log Levels

- **ERROR**: System errors, exceptions, failures requiring attention
- **WARN**: Unexpected situations that don't prevent operation
- **INFO**: Important business events (booking created, payment processed)
- **DEBUG**: Detailed diagnostic information

### Logging Format

**Required Fields**:
- Timestamp (ISO 8601)
- Log level
- Service name
- Correlation ID
- Message

**Example**:
```
2024-11-12T10:30:45.123Z - INFO - booking-service - [corr-id: abc-123] - Booking created: booking_id=xyz-789, user_id=user-456
```

### What to Log

**DO Log**:
- All HTTP requests/responses (method, path, status, duration)
- Business events (booking created, payment successful)
- External service calls (with correlation ID)
- Errors and exceptions (with stack trace)
- Security events (failed auth, unauthorized access)

**DON'T Log**:
- Sensitive data (passwords, tokens, credit cards, BVN)
- PII without proper redaction
- Large payloads (>1KB)

### Correlation IDs

- Generate correlation ID for each request
- Propagate through all service calls
- Include in all log messages
- Return in response headers (`X-Correlation-ID`)

**Java**:
```java
MDC.put("correlationId", correlationId);
logger.info("Processing booking request");
```

**Python**:
```python
set_correlation_id(correlation_id)
logger.info("Processing booking request")
```

---

## Error Handling Patterns

### Exception Hierarchy

**Java**:
```
Exception
├── BusinessException (expected errors)
│   ├── NotFoundException
│   ├── ValidationException
│   ├── UnauthorizedException
│   └── ForbiddenException
└── TechnicalException (unexpected errors)
    ├── DatabaseException
    ├── ExternalServiceException
    └── CacheException
```

**Python**:
```
Exception
├── BusinessException
│   ├── NotFoundException
│   ├── ValidationException
│   ├── UnauthorizedException
│   └── ForbiddenException
└── TechnicalException
    ├── DatabaseException
    └── ExternalServiceException
```

### Error Response Format

```json
{
  "success": false,
  "error": {
    "code": "BOOKING_NOT_FOUND",
    "message": "Booking with ID 'xyz-789' not found",
    "details": {
      "bookingId": "xyz-789"
    }
  },
  "meta": {
    "timestamp": "2024-11-12T10:30:45.123Z",
    "correlationId": "abc-123"
  }
}
```

### HTTP Status Code Mapping

- `400 Bad Request`: ValidationException
- `401 Unauthorized`: UnauthorizedException
- `403 Forbidden`: ForbiddenException
- `404 Not Found`: NotFoundException
- `409 Conflict`: ConflictException (e.g., duplicate booking)
- `429 Too Many Requests`: RateLimitException
- `500 Internal Server Error`: TechnicalException
- `503 Service Unavailable`: ServiceUnavailableException

### Best Practices

1. **Never swallow exceptions** - Always log or rethrow
2. **Use specific exception types** - No generic `Exception`
3. **Include context** - Add relevant data to exceptions
4. **Don't expose internal errors** - Return clean error messages to clients
5. **Handle errors at boundaries** - API layer, service layer
6. **Implement retry logic** - For transient failures

---

## API Versioning Strategy

### URL Versioning

All APIs use URL-based versioning:

```
/v1/bookings
/v1/users/me
/v2/bookings  (when v1 is deprecated)
```

### Version Lifecycle

1. **Active**: Currently supported version
2. **Deprecated**: Still functional but discouraged (6-month notice)
3. **Sunset**: No longer available

### Breaking Changes

Require a new API version:
- Removing fields from responses
- Changing field types
- Removing endpoints
- Changing validation rules (stricter)

### Non-Breaking Changes

Can be made to existing version:
- Adding new endpoints
- Adding optional fields to requests
- Adding fields to responses
- Relaxing validation rules

### Deprecation Process

1. **Announce** deprecation with 6-month notice
2. **Add deprecation header** to responses: `Deprecation: true`
3. **Update documentation** with migration guide
4. **Monitor usage** via metrics
5. **Sunset** after 6 months

---

## Security Best Practices

### Authentication & Authorization

- **All endpoints** require JWT authentication (except public endpoints)
- **Validate JWT** on every request
- **Check authorization** based on user role
- **Use correlation IDs** for security audit trails

### Input Validation

- **Validate all inputs** before processing
- **Sanitize data** to prevent injection attacks
- **Use DTOs/Schemas** for type safety
- **Enforce length limits** on all string fields

### Secrets Management

- **Never commit secrets** to version control
- **Use environment variables** for configuration
- **Use HashiCorp Vault** or AWS Secrets Manager for production
- **Rotate secrets** regularly

### Sensitive Data

- **Encrypt at rest**: BVN, license numbers, bank accounts
- **Never log**: Passwords, tokens, credit cards, OTPs
- **Hash passwords**: Use BCrypt with minimum 10 rounds
- **Use HTTPS**: All communication must be encrypted

### Rate Limiting

- **Apply rate limits** to all public endpoints
- **Use Redis** for distributed rate limiting
- **Return 429** with `Retry-After` header

---

## Performance Guidelines

### Database

- **Avoid N+1 queries**: Use joins or batch loading
- **Use indexes**: On frequently queried fields
- **Optimize queries**: EXPLAIN ANALYZE all slow queries
- **Use connection pooling**: Pool size 10-20
- **Implement pagination**: For list endpoints (max 100 items)

### Caching

- **Cache expensive operations**: PostGIS queries, ML predictions
- **Use Redis**: For distributed caching
- **Set appropriate TTLs**: Based on data freshness requirements
- **Implement cache invalidation**: On data updates

### Async Processing

- **Use async/await**: For I/O operations
- **Offload heavy tasks**: To background workers (Celery/Kafka)
- **Implement timeouts**: For all external calls
- **Use circuit breakers**: For external service calls

### Performance Targets

- **Booking API**: Mean <150ms, P95 <200ms
- **Matching API**: P95 ≤200ms
- **Payment webhook**: <100ms
- **Database queries**: <50ms
- **External service calls**: <500ms

---

## Documentation Requirements

### Code Documentation

#### Java (Javadoc)
```java
/**
 * Creates a new booking for a rider.
 *
 * @param request The booking request containing route and seat details
 * @param userId  The ID of the user making the booking
 * @return The created booking
 * @throws NotFoundException    If the route is not found
 * @throws ValidationException  If the booking request is invalid
 */
public Booking createBooking(BookingRequest request, String userId) {
    // Implementation
}
```

#### Python (Docstrings)
```python
async def create_booking(request: BookingRequest, user_id: str) -> Booking:
    """
    Create a new booking for a rider.

    Args:
        request: The booking request containing route and seat details
        user_id: The ID of the user making the booking

    Returns:
        The created booking

    Raises:
        NotFoundException: If the route is not found
        ValidationException: If the booking request is invalid
    """
    # Implementation
```

### API Documentation

- **Use OpenAPI/Swagger**: Generate interactive documentation
- **Document all endpoints**: Including request/response schemas
- **Include examples**: For requests and responses
- **Document error codes**: With descriptions

### README Files

Each service must have a README with:
- Service description
- Setup instructions
- Environment variables
- API endpoints
- Running tests
- Deployment instructions

---

## Summary

Following these guidelines ensures:
- ✅ **Consistency** across all services
- ✅ **Quality** through testing and code review
- ✅ **Security** through best practices
- ✅ **Maintainability** through clean code and documentation
- ✅ **Performance** through optimization guidelines

**Questions?** Contact the architecture team or raise in #backend-dev channel.
