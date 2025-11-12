# OpenRide Python Commons

Shared utilities and common code for OpenRide Python microservices.

## Modules

### 1. Exceptions
- `BusinessException`: For business logic errors (expected errors)
- `TechnicalException`: For system/technical errors (unexpected errors)
- `NotFoundException`: For resource not found errors
- `UnauthorizedException`: For unauthorized access
- `ForbiddenException`: For forbidden access
- `ValidationException`: For validation errors

### 2. Response
- `ApiResponse`: Standard API response wrapper
- `SuccessResponse`: Helper for creating success responses
- `ErrorResponse`: Helper for creating error responses
- `PaginatedData`: Container for paginated data

### 3. Security
- `JwtUtil`: JWT token generation, validation, and claims extraction
- `PasswordUtil`: Password hashing and verification

### 4. Logging
- `setup_logging()`: Configure logging for a service
- `CorrelationIdFilter`: Logging filter that adds correlation IDs
- `get_correlation_id()`, `set_correlation_id()`, `clear_correlation_id()`: Correlation ID management

### 5. Middleware
- `CorrelationIdMiddleware`: Adds correlation IDs to requests
- `RequestLoggingMiddleware`: Logs request and response details

### 6. Database
- `DatabaseManager`: Manager for database connections and sessions
- `get_db_session()`: FastAPI dependency for database sessions

## Installation

```bash
cd shared/python-commons
pip install -e .
```

For development with all tools:
```bash
pip install -e ".[dev]"
```

## Usage Example

```python
from fastapi import FastAPI
from openride_commons.middleware import CorrelationIdMiddleware, RequestLoggingMiddleware
from openride_commons.logging import setup_logging
from openride_commons.response import SuccessResponse
from openride_commons.security import JwtUtil

# Set up logging
setup_logging("my-service", log_level="INFO")

# Create FastAPI app
app = FastAPI()

# Add middleware
app.add_middleware(CorrelationIdMiddleware)
app.add_middleware(RequestLoggingMiddleware)

# Initialize JWT utility
jwt_util = JwtUtil(secret_key="your-secret-key")

# Use standard responses
@app.get("/health")
async def health():
    return SuccessResponse.create(data={"status": "healthy"})
```

## Development

### Format code
```bash
black openride_commons
```

### Lint code
```bash
flake8 openride_commons
```

### Type checking
```bash
mypy openride_commons
```

### Run tests
```bash
pytest
```
