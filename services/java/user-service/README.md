# User Service

User management and KYC service for the OpenRide platform.

## Features

- User profile management (CRUD)
- Driver profile management
- KYC document submission and verification workflow
- AES-256-GCM encryption for sensitive data (BVN, license numbers)
- JWT authentication
- Role-based authorization (RIDER, DRIVER, ADMIN)
- Distributed tracing with correlation IDs

## API Endpoints

### POST /api/v1/users
Create new user or return existing (internal, called by Auth Service).

**Request:**
```json
{
  "phone": "+2348012345678",
  "role": "RIDER"
}
```

### GET /api/v1/users/me
Get current user profile (requires JWT).

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "phone": "+2348012345678",
    "fullName": "John Doe",
    "email": "john@example.com",
    "role": "RIDER",
    "kycStatus": "NONE",
    "rating": 0.0,
    "isActive": true,
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-01T00:00:00"
  }
}
```

### PATCH /api/v1/users/me
Update current user profile (requires JWT).

**Request:**
```json
{
  "fullName": "John Doe",
  "email": "john@example.com"
}
```

### POST /api/v1/users/upgrade-to-driver
Upgrade from rider to driver (requires JWT).

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "role": "DRIVER",
    "kycStatus": "NONE",
    "driverProfile": {
      "id": "uuid",
      "totalTrips": 0,
      "totalEarnings": 0.00
    }
  }
}
```

### POST /api/v1/drivers/kyc-documents
Submit KYC documents for verification (requires JWT, must be driver).

**Request:**
```json
{
  "bvn": "12345678901",
  "licenseNumber": "ABC123456",
  "licensePhotoUrl": "https://...",
  "vehiclePhotoUrl": "https://..."
}
```

**Response:**
```json
{
  "success": true,
  "message": "KYC documents submitted for review",
  "data": {
    "kycStatus": "PENDING"
  }
}
```

### GET /api/v1/admin/drivers/pending-verification
Get all drivers with pending KYC (requires ADMIN role).

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "fullName": "Jane Driver",
      "kycStatus": "PENDING",
      "driverProfile": {
        "licensePhotoUrl": "https://...",
        "vehiclePhotoUrl": "https://..."
      }
    }
  ]
}
```

### PATCH /api/v1/admin/users/{userId}/kyc-status
Update KYC status (requires ADMIN role).

**Request:**
```json
{
  "status": "VERIFIED",
  "notes": "All documents verified successfully"
}
```

## Environment Variables

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=openride
DB_USER=openride_user
DB_PASSWORD=openride_password

# JWT
JWT_SECRET_KEY=your-secret-key-min-256-bits

# Encryption (AES-256 requires exactly 32 characters)
ENCRYPTION_KEY=your-32-char-encryption-key!!

# Logging
LOG_LEVEL=DEBUG
SQL_LOG_LEVEL=WARN
```

## Running Locally

### Prerequisites
- Java 17
- Maven 3.9+
- PostgreSQL 14+

### Steps

1. **Install shared commons library:**
```bash
cd ../../shared/java-commons
mvn clean install
```

2. **Run database migrations:**
```bash
cd ../../services/java/user-service
mvn flyway:migrate
```

3. **Build and run:**
```bash
mvn clean install
mvn spring-boot:run
```

4. **Access Swagger UI:**
```
http://localhost:8082/api/swagger-ui.html
```

## Running with Docker

```bash
docker build -t openride/user-service:latest .
docker run -p 8082:8082 \
  -e DB_HOST=postgres \
  -e JWT_SECRET_KEY=your-secret \
  -e ENCRYPTION_KEY=your-32-char-encryption-key!! \
  openride/user-service:latest
```

## Testing

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

## Security

### Encryption
- Sensitive data (BVN, license numbers) encrypted with **AES-256-GCM**
- Encryption key must be exactly 32 characters (256 bits)
- IV (Initialization Vector) generated randomly for each encryption
- Authentication tag (128 bits) prevents tampering
- Encrypted data stored as Base64 strings

### Authentication & Authorization
- JWT tokens validated on all protected endpoints
- Role-based access control:
  - **RIDER**: Can view/update own profile, upgrade to driver
  - **DRIVER**: All rider permissions + submit KYC documents
  - **ADMIN**: All permissions + manage KYC statuses
- `/api/v1/users` endpoint public (internal use only)
- `/api/v1/admin/**` endpoints require ADMIN role

### Data Protection
- Encrypted fields never returned in API responses
- BVN and license numbers encrypted at rest
- Passwords not stored (handled by Auth Service)
- Input validation on all endpoints
- SQL injection protection via JPA

## KYC Workflow

```
1. User registers → KYC_STATUS = NONE
2. User upgrades to driver → KYC_STATUS = NONE (creates driver_profile)
3. Driver submits documents → KYC_STATUS = PENDING (encrypts BVN/license)
4. Admin reviews documents → KYC_STATUS = VERIFIED or REJECTED
5. If VERIFIED → Driver can create routes
```

## Database Schema

### users
- `id` (UUID, PK)
- `phone` (VARCHAR, unique)
- `full_name` (VARCHAR, nullable)
- `email` (VARCHAR, nullable)
- `role` (ENUM: RIDER, DRIVER, ADMIN)
- `kyc_status` (ENUM: NONE, PENDING, VERIFIED, REJECTED)
- `rating` (DECIMAL)
- `is_active` (BOOLEAN)
- `created_at`, `updated_at` (TIMESTAMP)

### driver_profiles
- `id` (UUID, PK)
- `user_id` (UUID, FK → users, unique)
- `bvn_encrypted` (TEXT) - **Encrypted**
- `license_number_encrypted` (TEXT) - **Encrypted**
- `license_photo_url` (TEXT)
- `vehicle_photo_url` (TEXT)
- `kyc_notes` (TEXT)
- `total_trips` (INT)
- `total_earnings` (DECIMAL)
- `created_at`, `updated_at` (TIMESTAMP)

## Monitoring

- Actuator endpoints: `/actuator/health`, `/actuator/metrics`
- Prometheus metrics: `/actuator/prometheus`
- Correlation ID in all logs for distributed tracing

## Architecture

```
┌─────────────┐      ┌──────────┐      ┌───────────┐
│  Controller │─────▶│ Service  │─────▶│Repository │
└─────────────┘      └──────────┘      └───────────┘
                           │                  │
                           │                  ▼
                           │            ┌───────────┐
                           │            │PostgreSQL │
                           │            └───────────┘
                           │
                           ▼
                     ┌──────────┐
                     │Encryption│
                     │ Service  │
                     └──────────┘
```

## Integration with Auth Service

User Service is called by Auth Service after OTP verification:

```
Auth Service                    User Service
     │                               │
     ├──POST /v1/users (phone)──────▶│
     │                               ├─ Get or create user
     │◀──Return user data────────────┤
     │                               │
     ├─ Generate JWT tokens          │
     └─ Return to client             │
```

## License

Proprietary - OpenRide Platform
