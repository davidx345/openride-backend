# OpenRide Backend Implementation Plan

## Table of Contents
1. [Service Technology Stack Breakdown](#service-technology-stack-breakdown)
2. [Implementation Phases](#implementation-phases)
3. [Phase-by-Phase Detailed Prompts](#phase-by-phase-detailed-prompts)
4. [Development Constraints & Rules](#development-constraints--rules)

---

## Service Technology Stack Breakdown

### Java Spring Boot Services (High-Throughput, Transactional)

These services require **strong consistency**, **ACID transactions**, **complex state management**, and **high concurrency handling**:

1. **Booking Service** ⭐ (CRITICAL)
   - Seat inventory management with ACID guarantees
   - Redis-based seat hold mechanism with TTL
   - PostgreSQL transactions with SELECT FOR UPDATE
   - Race condition prevention
   - Optimistic/pessimistic locking strategies

2. **Payments Service** ⭐ (CRITICAL)
   - Payment orchestration with Interswitch
   - Webhook idempotency handling
   - Payment reconciliation engine
   - Chargeback & dispute management
   - Complex state machine (INIT → SUCCESS/FAILED/REFUNDED)

3. **Ticketing Service**
   - Blockchain integration (Hyperledger Fabric/Polygon)
   - Merkle tree generation for batched anchoring
   - ECDSA signature generation/verification
   - QR payload generation
   - Ticket verification logic

4. **Payouts Service**
   - Driver wallet & ledger management
   - Commission calculation engine
   - Settlement processing
   - Complex financial transactions
   - Audit trail requirements

5. **User Service**
   - User profile management
   - KYC status workflow
   - Role-based access control
   - Data encryption for sensitive fields (BVN/ID)

6. **Auth Service**
   - JWT token generation/validation
   - OTP generation & verification
   - Token refresh mechanism
   - Session management
   - Security-critical operations

**Why Spring Boot for these services?**
- Excellent transaction management with `@Transactional`
- Mature ecosystem for database handling (JPA/Hibernate)
- Strong type safety and compile-time checks
- Robust concurrency primitives
- Battle-tested for financial applications
- Excellent observability with Micrometer/Actuator

---

### Python FastAPI Services (Fast Iteration, ML/Geospatial, Lightweight)

These services require **fast development**, **ML integration**, **geospatial operations**, or **lightweight CRUD**:

1. **Matchmaking Service** ⭐ (AI/ML CORE)
   - Geospatial queries with PostGIS
   - ML-based scoring & ranking algorithms
   - Real-time candidate retrieval (<200ms p95)
   - In-memory caching with Redis
   - Numpy/Pandas for scoring calculations
   - Future ML model integration (scikit-learn/TensorFlow)

2. **Search & Discovery Service**
   - PostGIS geospatial queries (ST_DWithin, ST_Distance)
   - Route proximity calculations
   - Stop/hub search
   - Fast read-heavy operations
   - GeoJSON response formatting

3. **Driver Service**
   - Route CRUD operations
   - Vehicle management
   - Schedule management (RRULE parsing)
   - Route stop ordering & validation
   - Lightweight business logic

4. **Notification Service**
   - Push notification via FCM
   - SMS via Twilio/Termii
   - Email via SendGrid
   - Template management
   - Async message queuing with Celery

5. **Analytics Service**
   - Kafka consumer for event streams
   - Data aggregation & metrics calculation
   - Time-series data handling
   - Dashboard data preparation
   - Export/reporting features

6. **Fleet/Monitoring Service**
   - Real-time trip status tracking
   - Driver location aggregation
   - Live map data feed
   - WebSocket connection management
   - Redis pub/sub for location updates

**Why FastAPI for these services?**
- Extremely fast development & iteration
- Native async/await support (performance)
- Excellent for ML/data science integration
- Great GeoSpatial library support (Shapely, GeoPandas)
- Lightweight for read-heavy services
- Auto-generated OpenAPI docs
- Easy Kafka/Redis integration

---

### Shared Infrastructure (Both Stacks)

- **API Gateway**: Kong/Envoy/NGINX (language-agnostic)
- **Database**: PostgreSQL 14+ with PostGIS extension
- **Cache**: Redis 7+ (Cluster mode for production)
- **Message Broker**: Kafka (Confluent Cloud or self-managed)
- **Service Mesh**: Optional (Istio/Linkerd) for advanced routing
- **Monitoring**: Prometheus + Grafana + ELK/Loki
- **Tracing**: Jaeger/Tempo (OpenTelemetry SDK)
- **Secrets**: HashiCorp Vault or AWS Secrets Manager

---

## Implementation Phases

### Phase 0: Foundation & Infrastructure Setup (Week 1-2)
**Goal**: Set up development environment, shared infrastructure, and project structure

**Deliverables**:
- Repository structure & monorepo setup
- Docker Compose for local development
- PostgreSQL + PostGIS setup
- Redis setup
- Kafka setup (optional for Phase 0, required by Phase 3)
- Shared libraries/utilities
- CI/CD pipeline skeleton
- Development guidelines & conventions document

---

### Phase 1: Core Authentication & User Management (Week 2-3)
**Goal**: Enable user registration, authentication, and basic profile management

**Services to Build**:
1. Auth Service (Java Spring Boot)
2. User Service (Java Spring Boot)

**Deliverables**:
- Phone OTP flow (send & verify)
- JWT token generation & validation
- Token refresh mechanism
- User registration & profile CRUD
- KYC status workflow (NONE → PENDING → VERIFIED → REJECTED)
- Role-based access (Rider/Driver/Admin)
- Basic admin APIs for user management

**APIs**:
- `POST /v1/auth/send-otp`
- `POST /v1/auth/verify-otp`
- `POST /v1/auth/refresh-token`
- `GET /v1/users/me`
- `PATCH /v1/users/{id}`
- `PATCH /v1/admin/users/{id}/kyc-status`

---

### Phase 2: Driver & Route Management (Week 3-4)
**Goal**: Allow drivers to create/manage routes and vehicles

**Services to Build**:
1. Driver Service (Python FastAPI)

**Deliverables**:
- Vehicle CRUD operations
- Route creation with ordered stops
- Schedule management (RRULE support)
- Price matrix per route segment
- Route status management (ACTIVE/PAUSED/CANCELLED)
- Validation: stops must be ordered, schedule must be future
- Integration with User Service for driver profiles

**APIs**:
- `POST /v1/drivers/vehicles`
- `GET /v1/drivers/vehicles`
- `POST /v1/drivers/routes`
- `PUT /v1/drivers/routes/{id}`
- `DELETE /v1/drivers/routes/{id}`
- `GET /v1/drivers/routes/active`
- `PATCH /v1/drivers/routes/{id}/status`

---

### Phase 3: Search, Discovery & Matching Engine (Week 4-6)
**Goal**: Enable riders to discover routes and get intelligent match recommendations

**Services to Build**:
1. Search & Discovery Service (Python FastAPI)
2. Matchmaking Service (Python FastAPI) ⭐ **AI/ML CORE**

**Deliverables**:
- PostGIS geospatial queries for nearby stops/routes
- Temporal filtering (±15 min window)
- Route matching logic (exact/partial overlap + directionality)
- Composite scoring algorithm (RouteMatch + Time + Rating + Price)
- Explanation generation system
- Redis caching for hot routes
- Performance optimization (<200ms p95 latency)

**APIs**:
- `GET /v1/routes?lat={lat}&lng={lng}&timeWindow={iso8601}`
- `GET /v1/routes/{routeId}`
- `POST /v1/match` (internal: returns ranked driver list)

**Algorithms**:
- Geohash-based proximity search
- Bearing/directionality calculation
- Multi-factor scoring with configurable weights
- Explanation template system

---

### Phase 4: Booking & Seat Inventory Management (Week 6-8) ⭐ **CRITICAL**
**Goal**: Implement robust booking flow with seat hold mechanism and race condition prevention

**Services to Build**:
1. Booking Service (Java Spring Boot)

**Deliverables**:
- PENDING booking creation
- Redis seat-hold mechanism with TTL (10 min default)
- PostgreSQL ACID transactions (SELECT FOR UPDATE)
- Seat availability queries
- Booking state machine (PENDING → HELD → PAID → CONFIRMED → CHECKED_IN → COMPLETED)
- Cancellation & refund logic
- Integration with Payment Service (webhooks)
- Concurrency testing & race condition prevention
- Idempotency key handling

**APIs**:
- `POST /v1/bookings` (create booking + hold seat)
- `GET /v1/bookings/{id}`
- `GET /v1/bookings?riderId={id}&status={status}`
- `POST /v1/bookings/{id}/cancel`
- `POST /v1/bookings/{id}/confirm` (internal: called by Payment Service)

**Critical Implementation Details**:
- Distributed locks using Redis (Redisson for Java)
- TTL extension when payment initiated
- Webhook reconciliation for late payments
- Seat hold expiration cleanup job

---

### Phase 5: Payment Processing & Reconciliation (Week 8-10) ⭐ **CRITICAL**
**Goal**: Integrate Interswitch hosted payments with idempotent webhook handling

**Services to Build**:
1. Payments Service (Java Spring Boot)

**Deliverables**:
- Payment adapter pattern (supports multiple providers)
- Interswitch SDK integration
- Hosted widget token generation
- Webhook signature verification
- Idempotency handling (Redis + DB)
- Payment state machine
- Reconciliation engine (daily job)
- Chargeback handling
- Dispute evidence collection
- Admin APIs for manual refunds

**APIs**:
- `POST /v1/payments/initiate`
- `POST /v1/webhooks/payments` (Interswitch webhook)
- `GET /v1/payments/{id}`
- `POST /v1/admin/payments/{id}/refund`
- `GET /v1/payments/reconciliation?date={date}`

**Critical Implementation Details**:
- HMAC signature verification for webhooks
- Redis SET NX for deduplication
- Retry queue for failed webhook processing
- Provider fallback mechanism
- Daily reconciliation with provider API

---

### Phase 6: Ticketing & Blockchain Integration (Week 10-12)
**Goal**: Generate tamper-evident tickets with blockchain anchoring

**Services to Build**:
1. Ticketing Service (Java Spring Boot)

**Deliverables**:
- Canonical ticket JSON generation
- SHA-256 hash computation
- ECDSA signature generation
- QR payload encoding
- Batched Merkle tree anchoring (hourly/daily)
- Blockchain transaction submission
- Offline signature verification support
- Public key distribution for drivers
- Ticket verification API

**APIs**:
- `POST /v1/tickets` (internal: called by Booking Service)
- `GET /v1/bookings/{id}/ticket`
- `POST /v1/tickets/verify`
- `GET /v1/tickets/public-key`

**Blockchain Integration**:
- Hyperledger Fabric SDK OR Polygon Web3.js
- Merkle proof generation & storage
- Gas optimization strategies
- Fallback for blockchain downtime

---

### Phase 7: Real-Time Tracking & WebSocket (Week 12-13)
**Goal**: Enable live driver location tracking and trip updates

**Services to Build**:
1. Fleet/Monitoring Service (Python FastAPI + Socket.IO)

**Deliverables**:
- WebSocket server (Socket.IO)
- Driver location broadcast
- Rider subscription management
- Trip status updates
- Redis pub/sub for horizontal scaling
- Connection authentication (JWT in handshake)
- Graceful degradation (polling fallback)
- Rate limiting for location updates

**WebSocket Events**:
- Client → Server: `driver:online`, `driver:location`, `rider:subscribe`
- Server → Client: `driver:location`, `trip:update`, `booking:confirmed`

**Infrastructure**:
- Socket.IO with Redis adapter (for multi-instance)
- Nginx WebSocket proxy configuration

---

### Phase 8: Notifications & Messaging (Week 13-14)
**Goal**: Send push notifications, SMS, and emails for critical events

**Services to Build**:
1. Notification Service (Python FastAPI)

**Deliverables**:
- Firebase Cloud Messaging (FCM) integration
- SMS provider integration (Twilio/Termii)
- Email provider integration (SendGrid)
- Template management system
- Notification queue (Celery + Redis)
- Retry logic for failed deliveries
- User preference management
- Admin APIs for broadcast messages

**APIs**:
- `POST /v1/notifications/token` (register FCM token)
- `POST /v1/notifications/send` (internal)
- `GET /v1/notifications/preferences`
- `PATCH /v1/notifications/preferences`

**Event Triggers**:
- Booking confirmed → Push + SMS
- Payment success → Push + Email
- Driver arriving → Push
- Trip started/completed → Push

---

### Phase 9: Payouts & Financial Management (Week 14-15)
**Goal**: Calculate driver earnings and process settlements

**Services to Build**:
1. Payouts Service (Java Spring Boot)

**Deliverables**:
- Driver wallet/ledger system
- Commission calculation (percentage-based)
- Earning aggregation per trip
- Settlement batch processing
- Bank account verification
- Payout status tracking
- Admin approval workflow
- Audit trail & reporting

**APIs**:
- `GET /v1/earnings` (driver's earnings summary)
- `GET /v1/earnings/history`
- `POST /v1/payouts/request`
- `GET /v1/admin/payouts/pending`
- `POST /v1/admin/payouts/{id}/approve`

**Financial Logic**:
- Platform commission: 15% (configurable)
- Driver earnings = (trip_price × 0.85)
- Minimum payout threshold: ₦5,000
- Settlement schedule: Weekly/bi-weekly

---

### Phase 10: Analytics & Event Pipeline (Week 15-16)
**Goal**: Capture system events and generate actionable insights

**Services to Build**:
1. Analytics Service (Python FastAPI)

**Deliverables**:
- Kafka consumer for event streams
- Event schema definitions
- Metrics aggregation (daily/weekly/monthly)
- Dashboard data APIs
- Export functionality (CSV/JSON)
- Retention policy enforcement
- Real-time metrics via Redis

**Event Types**:
- `user.registered`
- `route.created`
- `booking.created`
- `payment.success`
- `trip.completed`
- `driver.location.updated`

**Metrics to Track**:
- Daily active users (DAU/WAU/MAU)
- Booking conversion rate
- Payment success rate
- Average trip duration
- Driver earnings distribution
- Popular routes

---

### Phase 11: Admin Dashboard & Support APIs (Week 16-17)
**Goal**: Provide operations team with tools for management and support

**Deliverables**:
- Driver verification queue
- KYC document review
- Booking search & filtering
- Manual refund issuance
- Dispute resolution workflow
- User ban/suspension
- System health dashboard
- Audit log viewer

**APIs**:
- `GET /v1/admin/drivers/pending-verification`
- `PATCH /v1/admin/drivers/{id}/verify`
- `GET /v1/admin/bookings?filters={json}`
- `POST /v1/admin/refunds`
- `GET /v1/admin/disputes`
- `PATCH /v1/admin/disputes/{id}/resolve`
- `GET /v1/admin/metrics/realtime`

---

### Phase 12: Testing, Performance & Deployment (Week 17-20)
**Goal**: Comprehensive testing, optimization, and production deployment

**Deliverables**:
- Unit tests (80%+ coverage)
- Integration tests for critical flows
- Load testing (JMeter/k6)
- Chaos engineering tests
- Security audit & penetration testing
- Performance profiling & optimization
- Database query optimization
- Redis cache tuning
- Kubernetes deployment manifests
- Terraform infrastructure as code
- Production monitoring setup
- Runbook documentation

**Performance Targets Validation**:
- Booking latency: mean < 150ms ✓
- Matching latency: p95 ≤ 200ms ✓
- Payment success rate: > 98% ✓
- API uptime: 99.95% ✓

---

## Phase-by-Phase Detailed Prompts

### PHASE 0: Foundation & Infrastructure Setup

**PROMPT FOR PHASE 0:**

```
You are building the OpenRide backend - a fixed-route carpooling platform.

CONTEXT:
- Microservices architecture with Java Spring Boot and Python FastAPI services
- PostgreSQL + PostGIS for relational & geospatial data
- Redis for caching and distributed locks
- Kafka for event streaming
- Monorepo structure with shared libraries

TASK: Set up the foundation infrastructure and project structure.

DELIVERABLES:

1. **Repository Structure** (Monorepo Layout):
   ```
   openride-backend/
   ├── services/
   │   ├── java/
   │   │   ├── auth-service/
   │   │   ├── user-service/
   │   │   ├── booking-service/
   │   │   ├── payments-service/
   │   │   ├── ticketing-service/
   │   │   └── payouts-service/
   │   └── python/
   │       ├── driver-service/
   │       ├── matchmaking-service/
   │       ├── search-service/
   │       ├── notification-service/
   │       ├── analytics-service/
   │       └── fleet-service/
   ├── shared/
   │   ├── java-commons/
   │   │   ├── exception-handling/
   │   │   ├── security/
   │   │   └── observability/
   │   └── python-commons/
   │       ├── exceptions/
   │       ├── security/
   │       └── observability/
   ├── infrastructure/
   │   ├── docker/
   │   ├── kubernetes/
   │   └── terraform/
   ├── docs/
   └── docker-compose.yml
   ```

2. **Docker Compose for Local Development**:
   - PostgreSQL 14 with PostGIS 3.3
   - Redis 7 (single instance for dev)
   - Kafka + Zookeeper (optional for Phase 0)
   - pgAdmin for database management
   - Redis Commander for cache inspection

3. **Shared Java Commons Library**:
   - Base Exception classes (BusinessException, TechnicalException)
   - JWT utility (generation, validation, claims extraction)
   - Standard API response wrapper (success/error format)
   - Logging interceptor (correlation ID, request/response logging)
   - Security configuration templates
   - OpenAPI configuration

4. **Shared Python Commons Library**:
   - Base exception classes
   - JWT middleware
   - Standard response models (Pydantic)
   - Logging configuration
   - Security utilities
   - Database connection helpers

5. **Database Schema Foundation**:
   - Create `openride` database
   - Enable PostGIS extension
   - Create schema migration tool setup (Flyway for Java, Alembic for Python)
   - Create shared tables: `users`, `audit_logs`

6. **CI/CD Pipeline Skeleton** (GitHub Actions):
   - Lint & format check (Checkstyle for Java, Black/Flake8 for Python)
   - Unit test runner
   - Docker image build
   - Security scanning (Trivy/Snyk)

7. **Development Guidelines Document** (`DEVELOPMENT.md`):
   - Code style conventions
   - Git workflow (feature branches, PR templates)
   - Testing requirements
   - Logging standards
   - Error handling patterns
   - API versioning strategy

CONSTRAINTS:
- Use Spring Boot 3.2+ with Java 17
- Use FastAPI 0.104+ with Python 3.11+
- PostgreSQL 14+ with PostGIS 3.3+
- Redis 7+
- Follow all rules from SECTION 1-10
- No code yet, only structure and configuration files

OUTPUT:
Generate complete folder structure, Docker Compose, shared library skeletons, and development guidelines.
```

---

### PHASE 1: Core Authentication & User Management

**PROMPT FOR PHASE 1:**

```
You are building Phase 1 of OpenRide backend: Authentication & User Management.

COMPLETED: Phase 0 - Infrastructure setup ✓

CONTEXT:
OpenRide uses phone-based OTP authentication with JWT tokens.
- Riders and Drivers both register via phone number
- OTP sent via SMS (integrate with Twilio/Termii)
- JWT tokens with 1-hour expiry + refresh token (7 days)
- KYC workflow for drivers (document upload & verification)

SERVICES TO BUILD:

1. **Auth Service** (Java Spring Boot)
2. **User Service** (Java Spring Boot)

---

AUTH SERVICE REQUIREMENTS:

**Tech Stack**:
- Spring Boot 3.2, Spring Security 6
- PostgreSQL (for OTP storage)
- Redis (for OTP caching & rate limiting)
- Twilio SDK for SMS

**Database Tables** (use Flyway migrations):

```sql
CREATE TABLE otp_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number VARCHAR(15) NOT NULL,
    otp_code VARCHAR(6) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    verified BOOLEAN DEFAULT FALSE,
    attempts INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_otp_phone ON otp_requests(phone_number, expires_at);
```

**API Endpoints**:

1. `POST /v1/auth/send-otp`
   - Request: `{ "phone": "+2348012345678" }`
   - Generate 6-digit OTP
   - Store in Redis (key: `otp:{phone}`, TTL: 5 min) + PostgreSQL
   - Send SMS via Twilio
   - Rate limit: 3 requests per phone per hour
   - Response: `{ "message": "OTP sent", "expiresIn": 300 }`

2. `POST /v1/auth/verify-otp`
   - Request: `{ "phone": "+2348012345678", "code": "123456" }`
   - Verify OTP from Redis/PostgreSQL
   - Max 3 attempts, then invalidate OTP
   - Generate JWT access token (1 hour expiry)
   - Generate refresh token (7 days expiry)
   - Store refresh token in Redis (key: `refresh:{userId}`)
   - Response: `{ "accessToken": "...", "refreshToken": "...", "user": {...} }`

3. `POST /v1/auth/refresh-token`
   - Request: `{ "refreshToken": "..." }`
   - Validate refresh token from Redis
   - Generate new access token
   - Response: `{ "accessToken": "..." }`

4. `POST /v1/auth/logout`
   - Invalidate refresh token from Redis
   - Response: `{ "message": "Logged out successfully" }`

**JWT Claims**:
```json
{
  "sub": "user_id",
  "phone": "+2348012345678",
  "role": "RIDER|DRIVER|ADMIN",
  "iat": 1699804800,
  "exp": 1699808400
}
```

**Security**:
- Use BCrypt for hashing OTP before storage (optional, depends on threat model)
- Rate limiting using Redis + Spring RateLimiter
- Audit log all auth attempts
- Block IP after 10 failed attempts in 1 hour

**Error Handling**:
- `OTP_EXPIRED`: 400
- `OTP_INVALID`: 401
- `OTP_MAX_ATTEMPTS`: 429
- `RATE_LIMIT_EXCEEDED`: 429

---

USER SERVICE REQUIREMENTS:

**Tech Stack**:
- Spring Boot 3.2, Spring Data JPA
- PostgreSQL
- AES-256 encryption for sensitive fields

**Database Tables** (use Flyway migrations):

```sql
CREATE TYPE user_role AS ENUM ('RIDER', 'DRIVER', 'ADMIN');
CREATE TYPE kyc_status AS ENUM ('NONE', 'PENDING', 'VERIFIED', 'REJECTED');

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(15) UNIQUE NOT NULL,
    email VARCHAR(255),
    full_name VARCHAR(255),
    role user_role NOT NULL DEFAULT 'RIDER',
    profile_photo_url TEXT,
    kyc_status kyc_status DEFAULT 'NONE',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE driver_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    bvn VARCHAR(255), -- encrypted
    drivers_license_number VARCHAR(255), -- encrypted
    license_photo_url TEXT,
    vehicle_photo_url TEXT,
    bank_account_number VARCHAR(10),
    bank_code VARCHAR(3),
    payout_wallet_id VARCHAR(255),
    rating DECIMAL(3,2) DEFAULT 0.00,
    total_trips INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id)
);

CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_role ON users(role);
```

**API Endpoints**:

1. `POST /v1/users` (internal: called by Auth Service after OTP verification)
   - Request: `{ "phone": "+234...", "role": "RIDER" }`
   - Create user with default fields
   - Response: User object

2. `GET /v1/users/me` (requires JWT)
   - Get current user profile
   - Include driver_profile if role = DRIVER
   - Response: Full user object

3. `PATCH /v1/users/me` (requires JWT)
   - Request: `{ "full_name": "...", "email": "..." }`
   - Update user profile
   - Validate email format
   - Response: Updated user object

4. `POST /v1/users/upgrade-to-driver` (requires JWT, role = RIDER)
   - Upgrade rider to driver
   - Create driver_profile record
   - Set kyc_status = PENDING
   - Response: Updated user object

5. `POST /v1/drivers/kyc-documents` (requires JWT, role = DRIVER)
   - Request: `{ "bvn": "...", "licenseNumber": "...", "licensePhotoUrl": "...", "vehiclePhotoUrl": "..." }`
   - Encrypt BVN and license number before storage
   - Update kyc_status = PENDING
   - Trigger admin notification
   - Response: `{ "message": "Documents submitted for review" }`

6. `PATCH /v1/admin/users/{userId}/kyc-status` (requires JWT, role = ADMIN)
   - Request: `{ "status": "VERIFIED|REJECTED", "notes": "..." }`
   - Update kyc_status
   - Send notification to driver
   - Response: Updated user object

**Business Logic**:
- Users start as RIDER by default
- Drivers must complete KYC before creating routes
- Encrypt BVN and license number using AES-256 (store key in Vault)
- Validate Nigerian phone format: `+234[7-9][0-1][0-9]{8}`
- Rating calculated from completed trips (updated by Trip Service later)

**Security**:
- JWT required for all endpoints except `POST /v1/users` (internal)
- Role-based authorization (RIDER, DRIVER, ADMIN)
- Sensitive fields (BVN) never returned in API responses
- Audit log all KYC status changes

**Testing Requirements**:
- Unit tests for service layer (80%+ coverage)
- Integration tests for auth flow (send OTP → verify → get user)
- Test OTP expiration logic
- Test rate limiting
- Test JWT generation and validation
- Test encryption/decryption of sensitive fields

---

IMPLEMENTATION ORDER:

1. Create shared JWT utility in `java-commons`
2. Create shared exception classes
3. Implement Auth Service (OTP + JWT)
4. Implement User Service
5. Write Flyway migrations
6. Write unit tests
7. Write integration tests
8. Test end-to-end flow in Postman/Insomnia
9. Generate OpenAPI documentation

CONSTRAINTS:
- Follow ALL rules from SECTION 1-10
- No hardcoded secrets (use environment variables)
- All code must compile and run
- All endpoints must have request/response validation
- All database operations must have proper error handling
- Include comprehensive logging

OUTPUT:
Generate complete working code for both services with all tests.
```

---

### PHASE 2: Driver & Route Management

**PROMPT FOR PHASE 2:**

```
You are building Phase 2 of OpenRide backend: Driver & Route Management.

COMPLETED:
- Phase 0: Infrastructure ✓
- Phase 1: Auth & User Management ✓

CONTEXT:
Drivers create fixed routes with ordered stops and recurring schedules.
Routes are NOT dynamic - they are predetermined daily commutes.
Example: "Lekki Phase 1 → Victoria Island, Mon-Fri, 7:00 AM, 4 seats, ₦1500/seat"

SERVICE TO BUILD:

**Driver Service** (Python FastAPI)

---

TECH STACK:
- FastAPI 0.104+
- SQLAlchemy 2.0 (async) + asyncpg
- PostgreSQL (connects to shared database)
- Alembic for migrations
- Pydantic v2 for validation
- Python 3.11+

---

DATABASE TABLES (create Alembic migrations):

```sql
CREATE TABLE vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plate_number VARCHAR(20) UNIQUE NOT NULL,
    model VARCHAR(100) NOT NULL,
    color VARCHAR(50),
    year INT,
    capacity INT NOT NULL CHECK (capacity >= 1 AND capacity <= 8),
    vehicle_photo_url TEXT,
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TYPE route_status AS ENUM ('ACTIVE', 'PAUSED', 'CANCELLED');

CREATE TABLE routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vehicle_id UUID NOT NULL REFERENCES vehicles(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    stops JSONB NOT NULL, -- Array of {stop_id, lat, lon, address, planned_arrival_offset_minutes}
    schedule_rrule VARCHAR(500), -- iCal RRULE format
    departure_time TIME NOT NULL, -- Daily departure time
    active_days JSONB, -- [1,2,3,4,5] for Mon-Fri
    price_per_seat INT NOT NULL, -- in kobo (Nigerian smallest currency unit)
    seats_total INT NOT NULL,
    seats_available INT NOT NULL,
    status route_status DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT seats_check CHECK (seats_available >= 0 AND seats_available <= seats_total)
);

CREATE TABLE stops (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    lat DECIMAL(10, 8) NOT NULL,
    lon DECIMAL(11, 8) NOT NULL,
    address TEXT,
    landmark VARCHAR(255),
    hub_id UUID, -- For future hub grouping
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(lat, lon)
);

-- Geospatial index for stops
CREATE INDEX idx_stops_location ON stops USING GIST (
    ST_SetSRID(ST_MakePoint(lon, lat), 4326)
);

CREATE INDEX idx_routes_driver ON routes(driver_id);
CREATE INDEX idx_routes_status ON routes(status);
CREATE INDEX idx_vehicles_driver ON vehicles(driver_id);
```

---

PYDANTIC MODELS:

```python
from pydantic import BaseModel, Field, validator
from typing import List, Optional
from datetime import time
from uuid import UUID

class StopCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=255)
    lat: float = Field(..., ge=-90, le=90)
    lon: float = Field(..., ge=-180, le=180)
    address: Optional[str] = None
    landmark: Optional[str] = None

class StopResponse(StopCreate):
    id: UUID
    created_at: datetime

class RouteStopInput(BaseModel):
    stop_id: Optional[UUID] = None  # If stop exists, use stop_id
    lat: float = Field(..., ge=-90, le=90)
    lon: float = Field(..., ge=-180, le=180)
    address: str
    planned_arrival_offset_minutes: int = Field(..., ge=0)  # Minutes from departure

class VehicleCreate(BaseModel):
    plate_number: str = Field(..., pattern=r'^[A-Z]{3}-\d{3}[A-Z]{2}$')  # Nigerian format
    model: str
    color: str
    year: int = Field(..., ge=1990, le=2025)
    capacity: int = Field(..., ge=1, le=8)
    vehicle_photo_url: Optional[str] = None

class VehicleResponse(VehicleCreate):
    id: UUID
    driver_id: UUID
    is_verified: bool
    created_at: datetime

class RouteCreate(BaseModel):
    vehicle_id: UUID
    name: str = Field(..., min_length=1, max_length=255)
    description: Optional[str] = None
    stops: List[RouteStopInput] = Field(..., min_items=2, max_items=20)
    schedule_rrule: Optional[str] = None  # "FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR"
    departure_time: time
    active_days: List[int] = Field(..., min_items=1, max_items=7)  # 1=Mon, 7=Sun
    price_per_seat: int = Field(..., gt=0)  # in kobo
    seats_total: int = Field(..., ge=1, le=8)

    @validator('active_days')
    def validate_active_days(cls, v):
        if not all(1 <= day <= 7 for day in v):
            raise ValueError('active_days must be between 1 and 7')
        return sorted(list(set(v)))  # Remove duplicates and sort

    @validator('stops')
    def validate_stops_order(cls, v):
        if len(v) < 2:
            raise ValueError('Route must have at least 2 stops')
        # Check that planned_arrival times are increasing
        times = [stop.planned_arrival_offset_minutes for stop in v]
        if times != sorted(times):
            raise ValueError('Stops must be ordered by planned arrival time')
        return v

class RouteUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    departure_time: Optional[time] = None
    price_per_seat: Optional[int] = None
    status: Optional[str] = None  # ACTIVE, PAUSED, CANCELLED

class RouteResponse(BaseModel):
    id: UUID
    driver_id: UUID
    vehicle_id: UUID
    name: str
    description: Optional[str]
    stops: List[dict]
    schedule_rrule: Optional[str]
    departure_time: time
    active_days: List[int]
    price_per_seat: int
    seats_total: int
    seats_available: int
    status: str
    created_at: datetime
    updated_at: datetime
```

---

API ENDPOINTS:

1. **Vehicle Management**

   `POST /v1/drivers/vehicles` (JWT required, role=DRIVER)
   - Request: VehicleCreate
   - Validate plate number uniqueness
   - Validate driver has KYC status = VERIFIED
   - Response: VehicleResponse

   `GET /v1/drivers/vehicles` (JWT required, role=DRIVER)
   - Get all vehicles for current driver
   - Response: List[VehicleResponse]

   `PATCH /v1/drivers/vehicles/{id}` (JWT required, role=DRIVER)
   - Update vehicle details
   - Only allow if no active routes using this vehicle
   - Response: VehicleResponse

   `DELETE /v1/drivers/vehicles/{id}` (JWT required, role=DRIVER)
   - Soft delete (mark is_verified=false)
   - Only allow if no routes reference this vehicle
   - Response: 204 No Content

2. **Route Management**

   `POST /v1/drivers/routes` (JWT required, role=DRIVER, KYC=VERIFIED)
   - Request: RouteCreate
   - Validate vehicle belongs to driver
   - Validate vehicle capacity >= seats_total
   - Create or reference stops (upsert by lat/lon)
   - Set seats_available = seats_total initially
   - Validate departure_time is in future (for first occurrence)
   - Response: RouteResponse

   `GET /v1/drivers/routes` (JWT required, role=DRIVER)
   - Get all routes for current driver
   - Optional query params: ?status=ACTIVE
   - Response: List[RouteResponse]

   `GET /v1/drivers/routes/{id}` (JWT required)
   - Get single route details
   - Include vehicle info
   - Response: RouteResponse with nested vehicle

   `PUT /v1/drivers/routes/{id}` (JWT required, role=DRIVER)
   - Request: RouteUpdate
   - Validation rules:
     * Cannot change stops if route has bookings
     * Cannot change departure_time within 24 hours of next trip
     * Cannot reduce seats_total below (seats_total - seats_available)
   - Response: RouteResponse

   `PATCH /v1/drivers/routes/{id}/status` (JWT required, role=DRIVER)
   - Request: `{ "status": "ACTIVE|PAUSED|CANCELLED" }`
   - If CANCELLED, trigger refund flow for future bookings
   - Response: RouteResponse

   `DELETE /v1/drivers/routes/{id}` (JWT required, role=DRIVER)
   - Mark as CANCELLED
   - Prevent deletion if active bookings exist
   - Response: 204 No Content

3. **Stop Discovery** (Public or JWT required)

   `GET /v1/stops?lat={lat}&lon={lon}&radius={meters}` 
   - Find stops near location (PostGIS ST_DWithin)
   - Default radius: 500m
   - Response: List[StopResponse]

   `POST /v1/stops` (JWT required, role=DRIVER)
   - Create new stop
   - Upsert logic: if lat/lon exists within 10m, return existing
   - Response: StopResponse

---

BUSINESS LOGIC:

1. **Route Validation**:
   - Driver must have KYC_STATUS = VERIFIED
   - Vehicle must belong to driver
   - Stops must be ordered by planned_arrival_offset_minutes
   - First stop planned_arrival = 0 (departure point)
   - Active_days must match schedule_rrule if provided

2. **Seat Management**:
   - seats_available initialized to seats_total
   - Decremented by Booking Service (not in Driver Service)
   - Cannot change seats_total to less than currently booked

3. **Schedule Management**:
   - Support simple RRULE: "FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR"
   - Use `dateutil.rrule` library for parsing
   - Generate next N occurrences (for search service)

4. **Stop Deduplication**:
   - When creating route, check if stop exists within 10m radius
   - If yes, reuse existing stop_id
   - If no, create new stop

---

SECURITY:
- JWT authentication on all endpoints
- Verify driver_id matches JWT user_id
- Validate KYC status before route creation
- Rate limit route creation: 10 routes per driver per day

---

TESTING REQUIREMENTS:

1. **Unit Tests**:
   - Test Pydantic validators (stops order, active_days)
   - Test route update business logic
   - Test stop deduplication logic

2. **Integration Tests**:
   - Create vehicle → Create route → Update route → Cancel route
   - Test concurrent route creation
   - Test stop proximity search (PostGIS queries)

3. **Edge Cases**:
   - Driver without KYC tries to create route
   - Update route with existing bookings
   - Create route with duplicate stops
   - Invalid plate number format

---

IMPLEMENTATION ORDER:

1. Create Alembic migrations
2. Create Pydantic models
3. Implement Vehicle CRUD
4. Implement Stop management with PostGIS queries
5. Implement Route CRUD with validations
6. Add business logic (KYC checks, seat validation)
7. Write unit tests
8. Write integration tests
9. Generate OpenAPI docs

CONSTRAINTS:
- Follow ALL rules from SECTION 1-10
- Use async/await throughout
- All database queries must use SQLAlchemy 2.0 async
- Proper error handling with HTTPException
- Comprehensive logging with correlation IDs
- All coordinates use WGS84 (SRID 4326)

OUTPUT:
Generate complete working FastAPI service with all tests and migrations.
```

---

### PHASE 3: Search, Discovery & Matching Engine

**PROMPT FOR PHASE 3:**

```
You are building Phase 3 of OpenRide backend: Search, Discovery & AI/ML Matching Engine.

COMPLETED:
- Phase 0: Infrastructure ✓
- Phase 1: Auth & User Management ✓
- Phase 2: Driver & Route Management ✓

CONTEXT:
This is the CORE INTELLIGENCE of OpenRide. The matching engine retrieves drivers near a rider's hub and ranks them using geospatial + temporal filtering + ML-based scoring.

Performance target: **P95 latency ≤ 200ms**

SERVICES TO BUILD:

1. **Search & Discovery Service** (Python FastAPI)
2. **Matchmaking Service** (Python FastAPI) ⭐ **AI/ML CORE**

---

MATCHMAKING SERVICE REQUIREMENTS:

**Tech Stack**:
- FastAPI 0.104+
- SQLAlchemy 2.0 (async) + asyncpg
- Redis 7+ for caching
- PostGIS for geospatial queries
- NumPy for scoring calculations
- Shapely for geometric operations
- Python 3.11+

**Key Algorithms**:

1. **Geospatial Filter** (Candidate Retrieval):
   ```sql
   -- Find drivers within 150m of hub
   SELECT r.*, ST_Distance(
       ST_SetSRID(ST_MakePoint(r.start_lon, r.start_lat), 4326)::geography,
       ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography
   ) as distance_meters
   FROM routes r
   WHERE ST_DWithin(
       ST_SetSRID(ST_MakePoint(r.start_lon, r.start_lat), 4326)::geography,
       ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
       150  -- meters
   )
   AND r.status = 'ACTIVE'
   AND r.seats_available > 0;
   ```

2. **Temporal Filter**:
   - Keep only routes departing within ±15 minutes of rider's desired time
   - Account for current time vs scheduled time
   - Filter expired routes (past departure time)

3. **Route Matching Logic**:
   - **Exact Match**: Driver's final stop = Rider's destination (score 1.0)
   - **Partial Overlap**: Rider's destination in driver's stop list (score 0.8)
   - **Directionality**: Rider's destination must appear AFTER hub in stop sequence

4. **Composite Scoring**:
   ```python
   def calculate_match_score(
       route_match_type: str,  # 'exact' or 'partial'
       time_diff_minutes: int,
       driver_rating: float,
       price_kobo: int,
       max_time_window: int = 15,
       max_price_kobo: int = 500000
   ) -> float:
       # RouteMatchScore
       route_score = 1.0 if route_match_type == 'exact' else 0.8
       
       # TimeScore (linear decay)
       time_score = max(0, 1 - (abs(time_diff_minutes) / max_time_window))
       
       # RatingScore (normalize 0-5 to 0-1)
       rating_score = driver_rating / 5.0 if driver_rating else 0.5
       
       # PriceScore (inverse: cheaper is better)
       price_score = 1 - (price_kobo / max_price_kobo)
       
       # Weighted composite
       composite = (
           0.4 * route_score +
           0.3 * time_score +
           0.2 * rating_score +
           0.1 * price_score
       )
       return composite
   ```

5. **Explanation Generation**:
   ```python
   def generate_explanations(match_data: dict) -> List[str]:
       tags = []
       
       if match_data['route_match_type'] == 'exact':
           tags.append(f"Exact match to {match_data['destination_name']}")
       else:
           tags.append(f"Passes your stop via {match_data['destination_name']}")
       
       if match_data['time_diff_minutes'] <= 5:
           tags.append("Leaving soon ⏰")
       elif match_data['time_diff_minutes'] <= 10:
           tags.append(f"Leaving in {match_data['time_diff_minutes']} min")
       
       if match_data['driver_rating'] >= 4.5:
           tags.append(f"★ {match_data['driver_rating']:.1f} rated driver")
       
       if match_data['seats_available'] <= 2:
           tags.append(f"Only {match_data['seats_available']} seats left!")
       
       return tags
   ```

---

DATABASE ADDITIONS (Alembic migration):

```sql
-- Add geospatial columns to routes table
ALTER TABLE routes 
ADD COLUMN start_lat DECIMAL(10, 8),
ADD COLUMN start_lon DECIMAL(11, 8),
ADD COLUMN end_lat DECIMAL(10, 8),
ADD COLUMN end_lon DECIMAL(11, 8);

-- Extract from stops JSONB (trigger or application logic)
CREATE OR REPLACE FUNCTION update_route_geo_columns()
RETURNS TRIGGER AS $$
BEGIN
    NEW.start_lat := (NEW.stops->0->>'lat')::DECIMAL;
    NEW.start_lon := (NEW.stops->0->>'lon')::DECIMAL;
    NEW.end_lat := (NEW.stops->-1->>'lat')::DECIMAL;
    NEW.end_lon := (NEW.stops->-1->>'lon')::DECIMAL;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER route_geo_update
BEFORE INSERT OR UPDATE ON routes
FOR EACH ROW EXECUTE FUNCTION update_route_geo_columns();

-- Geospatial index
CREATE INDEX idx_routes_start_location ON routes USING GIST (
    ST_SetSRID(ST_MakePoint(start_lon, start_lat), 4326)
);
```

---

PYDANTIC MODELS:

```python
from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import time, datetime
from uuid import UUID

class MatchRequest(BaseModel):
    """Rider's search request"""
    hub_lat: float = Field(..., ge=-90, le=90)
    hub_lon: float = Field(..., ge=-180, le=180)
    destination_lat: float = Field(..., ge=-90, le=90)
    destination_lon: float = Field(..., ge=-180, le=180)
    destination_name: Optional[str] = None
    desired_departure_time: time
    time_window_minutes: int = Field(default=15, ge=5, le=60)
    max_results: int = Field(default=10, ge=1, le=50)

class MatchResult(BaseModel):
    """Single match result"""
    route_id: UUID
    driver_id: UUID
    driver_name: str
    driver_rating: float
    driver_photo_url: Optional[str]
    vehicle_model: str
    vehicle_color: str
    vehicle_plate: str
    route_name: str
    departure_time: time
    price_per_seat: int  # kobo
    seats_available: int
    stops: List[dict]
    
    # Matching metadata
    match_type: str  # 'exact' | 'partial'
    match_score: float
    time_diff_minutes: int
    distance_from_hub_meters: float
    explanations: List[str]

class MatchResponse(BaseModel):
    """Matching API response"""
    matches: List[MatchResult]
    total_results: int
    search_time_ms: float
    cached: bool = False
```

---

API ENDPOINTS:

1. **POST /v1/match** (Internal or JWT required)
   - Request: MatchRequest
   - Execute matching algorithm:
     1. Geospatial filter (150m radius from hub)
     2. Temporal filter (±15 min from desired time)
     3. Route match check (exact/partial + directionality)
     4. Calculate composite scores
     5. Sort by score descending
     6. Generate explanations
     7. Limit to max_results
   - Cache results in Redis (key: `match:{hub_lat}:{hub_lon}:{dest_lat}:{dest_lon}:{time}`, TTL: 2 min)
   - Response: MatchResponse

2. **GET /v1/search/routes** (Public or JWT)
   - Query params: `?lat={lat}&lon={lon}&destLat={destLat}&destLon={destLon}&time={HH:MM}&window={minutes}`
   - Wrapper around POST /v1/match
   - Response: MatchResponse

3. **GET /v1/routes/{routeId}/details** (Public)
   - Get full route details
   - Include driver profile, vehicle info
   - Response: Detailed route object

---

CACHING STRATEGY:

```python
import json
import hashlib
from redis import asyncio as aioredis

class MatchCache:
    def __init__(self, redis_client: aioredis.Redis):
        self.redis = redis_client
        self.ttl = 120  # 2 minutes
    
    def generate_key(self, request: MatchRequest) -> str:
        # Create deterministic cache key
        key_data = f"{request.hub_lat:.6f}:{request.hub_lon:.6f}:" \
                   f"{request.destination_lat:.6f}:{request.destination_lon:.6f}:" \
                   f"{request.desired_departure_time.isoformat()}:" \
                   f"{request.time_window_minutes}"
        key_hash = hashlib.md5(key_data.encode()).hexdigest()
        return f"match:{key_hash}"
    
    async def get(self, request: MatchRequest) -> Optional[MatchResponse]:
        key = self.generate_key(request)
        cached = await self.redis.get(key)
        if cached:
            data = json.loads(cached)
            return MatchResponse(**data)
        return None
    
    async def set(self, request: MatchRequest, response: MatchResponse):
        key = self.generate_key(request)
        await self.redis.setex(
            key,
            self.ttl,
            response.json()
        )
```

---

PERFORMANCE OPTIMIZATIONS:

1. **Database Indexing**:
   - GiST index on route start location ✓
   - B-tree index on departure_time
   - Composite index on (status, seats_available)

2. **Query Optimization**:
   - Use `EXPLAIN ANALYZE` to verify index usage
   - Limit query to essential columns
   - Use JOINs wisely (fetch user/vehicle data in single query)

3. **Redis Caching**:
   - Cache match results for 2 minutes
   - Cache hot routes (frequently searched)
   - Use Redis pipelining for batch operations

4. **Async Processing**:
   - All database queries async
   - Parallel execution for independent operations (scoring calculations)

---

SEARCH & DISCOVERY SERVICE:

**Endpoints**:

1. `GET /v1/stops/nearby?lat={lat}&lon={lon}&radius={meters}`
   - PostGIS proximity search
   - Response: List[Stop]

2. `GET /v1/routes/popular`
   - Return top 20 most-booked routes (from analytics)
   - Cache for 1 hour
   - Response: List[RouteResponse]

3. `GET /v1/hubs`
   - Return all registered hubs (major pickup points)
   - Response: List[Hub]

---


TESTING REQUIREMENTS:

1. **Unit Tests**:
   - Test scoring algorithm with various inputs
   - Test explanation generation logic
   - Test route match type detection (exact vs partial)
   - Test directionality enforcement

2. **Integration Tests**:
   - Create test routes in database
   - Execute match request
   - Verify correct candidates returned
   - Verify scoring order is correct

3. **Performance Tests**:
   - Load 1000 active routes
   - Execute 100 concurrent match requests
   - Verify p95 latency < 200ms
   - Verify cache hit rate > 80% for repeated queries

4. **Edge Cases**:
   - No drivers available (empty result)
   - All drivers full (seats_available = 0)
   - Rider's destination not in any route
   - Time window edge cases (midnight crossing)

---

IMPLEMENTATION ORDER:

1. Create database migration for geo columns
2. Implement scoring algorithm (pure Python functions)
3. Implement explanation generator
4. Implement geospatial queries (PostGIS)
5. Implement temporal filtering
6. Implement route matching logic
7. Integrate caching layer
8. Build FastAPI endpoints
9. Write unit tests
10. Write integration tests
11. Performance testing with k6/Locust

CONSTRAINTS:
- P95 latency MUST be ≤ 200ms
- Follow ALL rules from SECTION 1-10
- Use async/await throughout
- Comprehensive logging with timing metrics
- Cache aggressively
- Optimize database queries
- No N+1 queries

OUTPUT:
Generate complete working Matchmaking Service with all tests and performance benchmarks.
```

---

### PHASES 4-12 DETAILED PROMPTS

Due to length constraints, I'll provide the structure for remaining phases. Each would follow the same detailed format as above:

**PHASE 4**: Booking & Seat Inventory (Redis locks + PostgreSQL transactions)
**PHASE 5**: Payment Processing (Interswitch + webhooks + reconciliation)
**PHASE 6**: Ticketing & Blockchain (Merkle trees + signatures)
**PHASE 7**: Real-Time Tracking (WebSocket + Socket.IO)
**PHASE 8**: Notifications (FCM + SMS + Email)
**PHASE 9**: Payouts & Financial (Ledger + settlements)
**PHASE 10**: Analytics & Events (Kafka consumers)
**PHASE 11**: Admin Dashboard APIs
**PHASE 12**: Testing, Performance, Deployment

---

## Development Constraints & Rules

### SECTION 1 — GENERAL BEHAVIOR RULES

- **Always track progress and avoid getting stuck**
  - Keep a clear, step-by-step execution path for every feature
  
- **Think before coding**
  - Always produce a high-level plan, verify the plan, then implement

- **No hallucinations**
  - No invented APIs, no fake functions
  - If unsure, state uncertainty and request clarification

- **Stay within the boundaries of the chosen tech stack**
  - Do not introduce tools not discussed by the user

- **Maintain consistency across all modules**

---

### SECTION 2 — ARCHITECTURE RULES

- **Enforce modular architecture always**
  - Every feature = separate module/service/component

- **No file should exceed 500–600 lines**
  - Split logically into: controllers, services, repositories, models, utils, config

- **Ensure all microservices follow the same structure**:
  - Folder layout
  - Naming conventions
  - API style
  - Error-handling format
  - Logging strategy

- **Use dependency injection patterns**:
  - Spring Boot DI for Java
  - FastAPI dependency injection for Python

---

### SECTION 3 — CODE QUALITY RULES

- **Generate clean, readable code with no duplication**
  - If a pattern repeats, extract it into a helper/util

- **Follow the official style guide**:
  - Java → Google Java Style
  - Python → PEP8

- **Use proper naming conventions**:
  - `ClassNames`, `methodNames`, `variable_names`, `CONSTANTS`

- **Keep functions small and single-purpose**

- **Avoid unnecessary abstractions or complexity**

- **Never leave unused imports, dead code, or commented-out blocks**

---

### SECTION 4 — RELIABILITY + ERROR HANDLING

- **Wrap all external calls in robust error handling**:
  - DB queries
  - Network requests
  - File access
  - Cache operations
  - Queue consumers

- **Use language-appropriate exception types**
  - No generic `Exception` unless unavoidable

- **Always validate all user input before processing**

- **Never trust external data sources without sanitizing**

- **Ensure graceful fallback behavior for microservices**

---

### SECTION 5 — SECURITY RULES

- **No plaintext passwords. No secrets in code.**

- **Every service must include**:
  - Authentication
  - Authorization
  - Request validation
  - Rate limiting (if public-facing)

- **Never expose internal errors to the client**
  - Return clean, standardized responses

- **Use correct hashing, encryption, CSRF protection, and proper CORS config**

---

### SECTION 6 — PERFORMANCE RULES

- **Avoid N+1 queries**
  - Batch operations when needed

- **Cache expensive operations** (Redis, memory cache, etc)

- **Keep APIs fast, predictable, and scalable**

- **Respect async/await or reactive patterns where suitable**

- **Optimize database access, indexes, and query patterns**

---

### SECTION 7 — TESTING REQUIREMENTS

For each module, generate:
- Unit tests
- Integration tests
- Edge-case tests
- Negative tests

- **All generated code must include tests** (unless explicitly skipped)

- **Tests must be realistic and executable** (no imaginary methods)

- **Before finalizing, mentally simulate test execution** to ensure logic correctness

---

### SECTION 8 — DOCUMENTATION + COMMENTS

- **Provide docstrings for every function**
  - Include parameters, return types, and purpose

- **Add comments only when needed** (not for obvious code)

- **Provide API documentation** (OpenAPI/Swagger for backend)

---

### SECTION 9 — REVIEW + VERIFICATION

At the end of coding, perform a **full project-wide review**, checking:
- Architecture consistency
- Errors
- Duplications
- Security issues
- Performance bottlenecks
- Logical correctness
- Naming consistency
- Missing tests

**Confirm alignment with the initial plan.**

**Fix all found issues before finalizing.**

---

### SECTION 10 — OUTPUT STRICTNESS

- **No partial code unless requested**
  - If producing a file, produce it fully

- **No guesses about undefined APIs, libraries, endpoints**
  - Ask the user to specify missing information

- **Always return code that can realistically compile and run**

---

## Summary

This implementation plan provides:

1. ✅ **Clear service breakdown** by technology (Java vs Python)
2. ✅ **12 sequential implementation phases** with dependencies
3. ✅ **Detailed prompts** for each phase with exact requirements
4. ✅ **Comprehensive constraints** (10 sections of rules)
5. ✅ **Database schemas**, API contracts, algorithms, and testing requirements
6. ✅ **Performance targets** and optimization strategies

**Ready to begin implementation when you are.**
