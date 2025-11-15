# Phase 12: Testing, Performance & Deployment - Complete Breakdown

Let me break down **EXACTLY** what's left before you can deploy OpenRide to production.

---

## 📊 Current State Assessment

**What You Have:**
- ✅ 12 microservices (6 Java + 6 Python) with business logic
- ✅ Database schemas with migrations
- ✅ API endpoints implemented
- ✅ Authentication & authorization
- ✅ Payment integration (Interswitch)
- ✅ Blockchain ticketing
- ✅ Real-time tracking
- ✅ Analytics & notifications
- ✅ Performance optimizations (Phase 5)

**What's Missing:**
- ❌ Comprehensive test coverage
- ❌ Performance validation under load
- ❌ Production infrastructure
- ❌ Monitoring & observability
- ❌ Deployment automation
- ❌ Security hardening
- ❌ Operational runbooks

---

## 🎯 Phase 12 Deliverables (7 Major Categories)

### **1. TESTING SUITE (Est. 1-2 weeks)**

#### 1.1 Unit Tests (80%+ Coverage Target)

**Java Services (6 services):**
- **Auth Service:**
  - OTP generation/validation logic
  - JWT token creation/verification
  - Refresh token rotation
  - Rate limiting logic
  - Edge cases: expired OTP, max attempts, invalid tokens

- **User Service:**
  - User CRUD operations
  - KYC workflow state machine
  - BVN/License encryption/decryption
  - Role-based authorization
  - Profile update validations

- **Booking Service:** ⭐ **CRITICAL**
  - Seat hold mechanism (Redis TTL)
  - Race condition prevention (distributed locks)
  - State transitions (PENDING→HELD→PAID→CONFIRMED)
  - Concurrent booking attempts
  - Seat availability calculations
  - Cancellation & refund logic

- **Payments Service:** ⭐ **CRITICAL**
  - Payment initiation
  - Webhook signature verification
  - Idempotency handling (Redis SET NX)
  - State machine transitions
  - Reconciliation engine
  - Refund processing
  - Provider fallback logic

- **Ticketing Service:**
  - Ticket JSON canonicalization
  - SHA-256 hash computation
  - ECDSA signature generation/verification
  - Merkle tree construction
  - Blockchain transaction submission
  - Offline verification

- **Payouts Service:**
  - Commission calculation (15% platform fee)
  - Wallet balance management
  - Settlement batch processing
  - Minimum payout threshold checks
  - Audit trail generation

**Python Services (6 services):**
- **Driver Service:**
  - Route CRUD validations
  - Stop ordering enforcement
  - Schedule RRULE parsing
  - Vehicle capacity checks
  - KYC status verification

- **Matchmaking Service:** ⭐ **CRITICAL**
  - Geospatial filtering (PostGIS queries)
  - Temporal filtering (±15 min window)
  - Route matching logic (exact/partial)
  - Composite scoring algorithm
  - Explanation generation
  - Cache hit/miss logic

- **Search Service:**
  - Proximity search (ST_DWithin)
  - Stop deduplication (10m radius)
  - Hub discovery
  - Popular routes caching

- **Notification Service:**
  - FCM push notifications
  - SMS sending (Twilio/Termii)
  - Email delivery (SendGrid)
  - Template rendering
  - Retry logic for failures

- **Analytics Service:**
  - Kafka event consumption
  - Metrics aggregation
  - Time-series calculations
  - Export functionality

- **Fleet Service:**
  - WebSocket connection management
  - Location broadcast logic
  - Subscription management
  - Redis pub/sub integration

**Coverage Tools:**
- Java: JaCoCo (Maven/Gradle plugin)
- Python: pytest-cov

**Expected Output:**
```
Auth Service:        85% coverage (120 tests)
User Service:        82% coverage (95 tests)
Booking Service:     90% coverage (180 tests) ← CRITICAL
Payments Service:    88% coverage (150 tests) ← CRITICAL
Ticketing Service:   75% coverage (80 tests)
Payouts Service:     80% coverage (110 tests)
Driver Service:      85% coverage (90 tests)
Matchmaking Service: 92% coverage (140 tests) ← CRITICAL
Search Service:      78% coverage (70 tests)
Notification Service: 80% coverage (85 tests)
Analytics Service:   75% coverage (65 tests)
Fleet Service:       82% coverage (95 tests)
-------------------------------------------
Overall:             83% coverage (~1,280 tests)
```

---

#### 1.2 Integration Tests (End-to-End Flows)

**Critical User Journeys:**

**Journey 1: Rider Books a Trip (Happy Path)**
```
1. POST /v1/auth/send-otp (phone: +2348012345678)
   → Verify OTP sent to Twilio
   
2. POST /v1/auth/verify-otp (code: 123456)
   → Verify JWT token returned
   → Verify user created in database
   
3. GET /v1/routes?lat=6.4541&lng=3.3947&time=07:00
   → Verify matchmaking returns ranked routes
   → Verify scoring algorithm works
   
4. POST /v1/bookings (routeId, seatCount: 2)
   → Verify booking created (status: PENDING)
   → Verify seat hold in Redis (TTL: 10 min)
   → Verify seats_available decremented in DB
   
5. POST /v1/payments/initiate (bookingId)
   → Verify Interswitch widget token generated
   → Verify payment record created
   
6. POST /v1/webhooks/payments (Interswitch callback)
   → Verify signature validation
   → Verify idempotency (duplicate webhook ignored)
   → Verify booking status → CONFIRMED
   → Verify seat hold converted to confirmed booking
   
7. GET /v1/bookings/{id}/ticket
   → Verify ticket generated with QR code
   → Verify blockchain hash present
   → Verify ECDSA signature valid
   
8. WebSocket: driver:location event
   → Verify rider receives real-time updates
   
9. POST /v1/tickets/verify (at trip start)
   → Verify signature validation works offline
   → Verify booking status → CHECKED_IN
```

**Journey 2: Driver Creates Route**
```
1. POST /v1/auth/send-otp (driver phone)
2. POST /v1/auth/verify-otp
3. POST /v1/users/upgrade-to-driver
4. POST /v1/drivers/kyc-documents (BVN, license, photos)
5. PATCH /v1/admin/users/{id}/kyc-status (status: VERIFIED) [admin]
6. POST /v1/drivers/vehicles (plate, model, capacity)
7. POST /v1/drivers/routes (stops, schedule, price)
   → Verify route created with geo columns
   → Verify stops indexed in PostGIS
8. GET /v1/routes?lat=6.4541&lng=3.3947
   → Verify route appears in search results
```

**Journey 3: Payment Failure & Retry**
```
1. Create booking
2. Initiate payment
3. Webhook: payment failed
   → Verify booking status → PENDING (not confirmed)
   → Verify seat hold still active in Redis
4. Retry payment (user tries again)
5. Webhook: payment success
   → Verify booking confirmed
   → Verify no duplicate charges
```

**Journey 4: Booking Cancellation & Refund**
```
1. Create confirmed booking
2. POST /v1/bookings/{id}/cancel
   → Verify cancellation policy applied
   → Verify refund initiated
3. Driver earns payout
4. POST /v1/payouts/request
   → Verify driver wallet updated
   → Verify commission deducted (15%)
```

**Tools:**
- Java: Spring Boot Test + TestContainers (real PostgreSQL/Redis)
- Python: pytest + httpx (async client)
- Docker Compose for test environment

**Expected Output:**
```
4 critical user journeys tested
~50 integration test cases
Success rate: 100% (all green)
Avg test time: <30 seconds per journey
```

---

#### 1.3 Performance/Load Testing (Validate Targets)

**Performance Targets (from implementation plan):**
- Booking latency: **mean < 150ms** ✓
- Matchmaking latency: **p95 ≤ 200ms** ✓
- Payment success rate: **> 98%** ✓
- API uptime: **99.95%** ✓

**Load Testing Scenarios:**

**Scenario 1: Normal Load (Baseline)**
```
Users: 100 concurrent
Duration: 5 minutes
Requests: 
  - 40% GET /v1/routes (search)
  - 30% POST /v1/bookings
  - 20% POST /v1/payments/initiate
  - 10% GET /v1/bookings/{id}
  
Expected:
  - Search p95: <200ms
  - Booking p95: <150ms
  - Error rate: <0.1%
  - Throughput: ~500 req/s
```

**Scenario 2: Peak Load (Rush Hour)**
```
Users: 500 concurrent
Duration: 10 minutes
Ramp-up: 50 users/30s
Same request distribution

Expected:
  - Search p95: <300ms (allows 50% degradation)
  - Booking p95: <250ms
  - Error rate: <1%
  - Throughput: ~2,000 req/s
  - No database connection pool exhaustion
  - Redis cache hit rate: >80%
```

**Scenario 3: Spike Test (Flash Sale)**
```
Baseline: 100 users
Spike to: 1,000 users (instant)
Hold: 2 minutes
Return to: 100 users

Expected:
  - System recovers within 30s
  - No cascading failures
  - Auto-scaling triggers (if on Kubernetes)
  - Circuit breakers activate for slow services
```

**Scenario 4: Stress Test (Find Breaking Point)**
```
Start: 100 users
Increment: +100 users every 2 min
Stop when: Error rate > 10% or latency > 5s

Expected to find:
  - Maximum capacity (likely 800-1,200 concurrent users)
  - Bottleneck services (probably Booking/Payment)
  - Resource limits (DB connections, Redis memory)
```

**Scenario 5: Endurance Test (Memory Leaks)**
```
Users: 200 concurrent
Duration: 4 hours
Monitor:
  - Memory usage (should be stable)
  - Database connection leaks
  - Redis memory growth
  - Response times (should not degrade)
```

**Tools:**
- **k6** (recommended - modern, scriptable in JavaScript)
- **JMeter** (traditional, GUI-based)
- **Locust** (Python-based, code as config)

**k6 Example Script:**
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
  stages: [
    { duration: '2m', target: 100 }, // Ramp up
    { duration: '5m', target: 100 }, // Hold
    { duration: '2m', target: 0 },   // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<200'], // 95% under 200ms
    http_req_failed: ['rate<0.01'],   // Error rate < 1%
  },
};

export default function () {
  // Search routes
  let searchRes = http.get('http://api.openride.com/v1/routes?lat=6.4541&lng=3.3947');
  check(searchRes, { 'search status 200': (r) => r.status === 200 });
  
  // Create booking
  let bookingRes = http.post('http://api.openride.com/v1/bookings', {
    routeId: '123e4567-e89b-12d3-a456-426614174000',
    seatCount: 2,
  });
  check(bookingRes, { 'booking status 201': (r) => r.status === 201 });
  
  sleep(1);
}
```

**Expected Report:**
```
✓ Search p95 latency: 185ms (target: 200ms) ✅
✓ Booking p95 latency: 142ms (target: 150ms) ✅
✓ Payment success rate: 98.7% (target: 98%) ✅
✓ Error rate: 0.08% (target: <1%) ✅
✓ Max throughput: 1,850 req/s
✗ Breaking point: 950 concurrent users (DB connection pool limit)
```

---

#### 1.4 Security Testing & Audit

**Automated Security Scans:**

1. **Dependency Vulnerability Scanning:**
   ```bash
   # Java (Maven)
   mvn org.owasp:dependency-check-maven:check
   
   # Python
   pip install safety
   safety check
   
   # Or use Snyk
   snyk test
   ```
   
   **Checks for:**
   - Known CVEs in dependencies
   - Outdated libraries with security patches
   - License compliance issues

2. **Container Image Scanning:**
   ```bash
   # Trivy (recommended)
   trivy image openride/auth-service:latest
   trivy image openride/matchmaking-service:latest
   ```
   
   **Checks for:**
   - OS vulnerabilities
   - Application dependencies
   - Misconfigurations
   - Exposed secrets in layers

3. **Static Code Analysis:**
   ```bash
   # Java (SpotBugs + Find Security Bugs)
   mvn spotbugs:check
   
   # Python (Bandit)
   bandit -r services/python/
   ```
   
   **Checks for:**
   - SQL injection vulnerabilities
   - XSS vulnerabilities
   - Insecure crypto usage
   - Hardcoded secrets
   - Path traversal issues

4. **Dynamic Application Security Testing (DAST):**
   ```bash
   # OWASP ZAP
   zap-cli quick-scan --self-contained http://api.openride.com
   ```
   
   **Checks for:**
   - Authentication bypasses
   - Authorization flaws
   - CSRF vulnerabilities
   - Insecure headers
   - Cookie security issues

**Manual Security Review:**

**Authentication & Authorization:**
- ✅ JWT tokens use HS256/RS256 (not "none" algorithm)
- ✅ Tokens expire within 1 hour
- ✅ Refresh tokens stored securely (Redis with encryption)
- ✅ OTP rate limiting implemented (3 attempts per hour)
- ✅ Role-based access control enforced on all endpoints
- ✅ No user enumeration via login/registration errors

**Input Validation:**
- ✅ All request bodies validated with Pydantic/Bean Validation
- ✅ SQL injection prevented (using ORM, no raw queries)
- ✅ XSS prevented (no HTML rendering, API only)
- ✅ Phone number format validation (regex)
- ✅ Coordinate bounds checked (-90 to 90 lat, -180 to 180 lon)
- ✅ File upload validation (if applicable)

**API Security:**
- ✅ Rate limiting on public endpoints (100 req/min per IP)
- ✅ CORS configured correctly (whitelist origins)
- ✅ Security headers present:
  ```
  X-Content-Type-Options: nosniff
  X-Frame-Options: DENY
  X-XSS-Protection: 1; mode=block
  Strict-Transport-Security: max-age=31536000
  Content-Security-Policy: default-src 'self'
  ```
- ✅ API versioning enforced (/v1/)
- ✅ No sensitive data in URLs (use POST body)

**Data Protection:**
- ✅ BVN & license numbers encrypted at rest (AES-256)
- ✅ Passwords never stored (OTP-based auth)
- ✅ PII encrypted in database
- ✅ Database credentials in secrets manager (Vault/AWS Secrets)
- ✅ TLS 1.3 enforced for all external connections
- ✅ Database backups encrypted

**Payment Security:**
- ✅ Webhook signature verification (HMAC-SHA256)
- ✅ Idempotency keys prevent duplicate charges
- ✅ PCI-DSS compliance (using hosted payment page)
- ✅ No credit card data stored (Interswitch handles)
- ✅ Reconciliation detects missing/extra payments

**Blockchain Security:**
- ✅ Private keys stored in HSM or secure enclave
- ✅ Signature verification works offline
- ✅ Merkle proofs validated correctly
- ✅ No private data on public blockchain

**Infrastructure Security:**
- ✅ Services run as non-root user
- ✅ Minimal Docker images (Alpine/Distroless)
- ✅ No SSH access to production containers
- ✅ Network segmentation (private subnets for DB/Redis)
- ✅ Firewall rules restrict traffic (Security Groups)

**Expected Findings:**
```
Critical: 0
High: 2-5 (likely dependency updates needed)
Medium: 5-10 (configuration improvements)
Low: 10-20 (informational)
```

---

### **2. INFRASTRUCTURE AS CODE (Est. 1 week)**

You need to define **entire infrastructure** reproducibly using code.

#### 2.1 Kubernetes Manifests (Preferred for Microservices)

**File Structure:**
```
infrastructure/kubernetes/
├── namespaces/
│   └── openride.yaml
├── configmaps/
│   ├── auth-service-config.yaml
│   ├── matchmaking-service-config.yaml
│   └── ...
├── secrets/
│   ├── database-credentials.yaml (encrypted with SealedSecrets)
│   ├── redis-credentials.yaml
│   ├── jwt-secret.yaml
│   └── interswitch-keys.yaml
├── deployments/
│   ├── auth-service.yaml
│   ├── user-service.yaml
│   ├── booking-service.yaml (with PodDisruptionBudget)
│   ├── matchmaking-service.yaml
│   └── ... (12 total)
├── services/
│   ├── auth-service-svc.yaml (ClusterIP)
│   ├── api-gateway-svc.yaml (LoadBalancer)
│   └── ...
├── ingress/
│   └── api-ingress.yaml (with TLS/SSL)
├── hpa/
│   ├── matchmaking-hpa.yaml (horizontal pod autoscaling)
│   ├── booking-hpa.yaml
│   └── ...
├── statefulsets/
│   ├── postgresql.yaml (or use managed RDS)
│   ├── redis.yaml (or use ElastiCache)
│   └── kafka.yaml
├── jobs/
│   ├── db-migration-job.yaml (Flyway/Alembic)
│   └── reconciliation-cronjob.yaml
└── monitoring/
    ├── prometheus.yaml
    ├── grafana.yaml
    └── alert-rules.yaml
```

**Example: Booking Service Deployment**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: booking-service
  namespace: openride
spec:
  replicas: 3  # High availability
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0  # Zero downtime
  selector:
    matchLabels:
      app: booking-service
  template:
    metadata:
      labels:
        app: booking-service
        version: v1.0.0
    spec:
      containers:
      - name: booking-service
        image: openride/booking-service:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: database-credentials
              key: url
        - name: REDIS_URL
          valueFrom:
            secretKeyRef:
              name: redis-credentials
              key: url
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
      imagePullSecrets:
      - name: docker-registry-secret
---
apiVersion: v1
kind: Service
metadata:
  name: booking-service
  namespace: openride
spec:
  type: ClusterIP
  selector:
    app: booking-service
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: booking-service-hpa
  namespace: openride
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: booking-service
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

**What You Need for EACH Service:**
- Deployment manifest (replicas, health checks, resource limits)
- Service manifest (ClusterIP for internal, LoadBalancer for API Gateway)
- ConfigMap (environment-specific settings)
- Secret (credentials, API keys)
- HPA (auto-scaling rules)
- PodDisruptionBudget (ensure availability during updates)

**Total Files Needed:**
- 12 deployments (one per service)
- 12 services
- 12 configmaps
- ~8 secrets
- 4-6 HPAs (critical services only)
- 1 ingress controller
- 1 StatefulSet for PostgreSQL (or skip if using managed RDS)
- 1 StatefulSet for Redis (or skip if using ElastiCache)

---

#### 2.2 Terraform (Infrastructure Provisioning)

**File Structure:**
```
infrastructure/terraform/
├── modules/
│   ├── networking/
│   │   ├── main.tf (VPC, subnets, NAT gateway)
│   │   ├── variables.tf
│   │   └── outputs.tf
│   ├── database/
│   │   ├── main.tf (RDS PostgreSQL with PostGIS)
│   │   ├── variables.tf
│   │   └── outputs.tf
│   ├── cache/
│   │   ├── main.tf (ElastiCache Redis cluster)
│   │   ├── variables.tf
│   │   └── outputs.tf
│   ├── kubernetes/
│   │   ├── main.tf (EKS cluster or GKE)
│   │   ├── variables.tf
│   │   └── outputs.tf
│   ├── messaging/
│   │   ├── main.tf (MSK Kafka cluster)
│   │   ├── variables.tf
│   │   └── outputs.tf
│   ├── storage/
│   │   ├── main.tf (S3 buckets for logs/backups)
│   │   ├── variables.tf
│   │   └── outputs.tf
│   └── monitoring/
│       ├── main.tf (CloudWatch/Prometheus/Grafana)
│       ├── variables.tf
│       └── outputs.tf
├── environments/
│   ├── dev/
│   │   ├── main.tf
│   │   ├── terraform.tfvars
│   │   └── backend.tf
│   ├── staging/
│   │   ├── main.tf
│   │   ├── terraform.tfvars
│   │   └── backend.tf
│   └── production/
│       ├── main.tf
│       ├── terraform.tfvars
│       └── backend.tf
├── main.tf (root module)
├── variables.tf
├── outputs.tf
└── versions.tf
```

**What Terraform Provisions:**

**1. Networking:**
- VPC with CIDR block (e.g., 10.0.0.0/16)
- Public subnets (for load balancers, NAT gateways)
- Private subnets (for application pods)
- Database subnets (for RDS)
- Internet Gateway
- NAT Gateway (for private subnet internet access)
- Route tables
- Security groups (firewall rules)

**2. Database (RDS PostgreSQL):**
- Multi-AZ deployment for high availability
- PostGIS extension enabled
- Automated backups (7-day retention)
- Read replicas (for read-heavy workloads)
- Parameter groups (connection limits, autovacuum tuning)
- Encryption at rest (KMS)
- Enhanced monitoring

**3. Cache (ElastiCache Redis):**
- Cluster mode enabled (sharding)
- 3 shards × 2 replicas = 6 nodes
- Automatic failover
- Encryption in transit/at rest
- Backup retention (7 days)

**4. Kubernetes Cluster (EKS/GKE/AKS):**
- Control plane (managed by cloud provider)
- Node groups:
  - General workloads: 3-10 nodes (t3.large)
  - High-memory: 2-5 nodes (r5.xlarge) for Java services
  - Spot instances for cost savings
- Cluster autoscaler
- IAM roles for service accounts (IRSA)
- CNI networking plugin

**5. Message Broker (Kafka):**
- MSK cluster (3 brokers across 3 AZs)
- Zookeeper managed
- Topics auto-creation enabled
- Retention: 7 days
- Replication factor: 3

**6. Storage (S3):**
- Logs bucket (lifecycle: 90 days)
- Backups bucket (lifecycle: 365 days)
- Static assets bucket (for profile photos, etc.)
- Versioning enabled
- Encryption at rest

**7. Load Balancer:**
- Application Load Balancer (ALB)
- SSL/TLS certificates (ACM)
- WAF (Web Application Firewall) rules
- Target groups for services
- Health checks

**8. DNS & Certificates:**
- Route 53 hosted zone
- ACM SSL certificate for *.openride.com
- DNS records (api.openride.com, admin.openride.com)

**9. Monitoring:**
- CloudWatch log groups (one per service)
- CloudWatch alarms (CPU, memory, error rate)
- SNS topics for alerts
- Lambda for custom metrics

**10. IAM & Security:**
- Service accounts for Kubernetes pods
- Secrets Manager for credentials
- KMS keys for encryption
- Security groups (least privilege)

**Estimated Terraform Files:**
- ~30 .tf files across all modules
- ~500 lines of infrastructure code
- 3 environments (dev, staging, production)

**Example: RDS PostgreSQL Module**
```hcl
resource "aws_db_instance" "openride_db" {
  identifier                = "openride-${var.environment}"
  engine                   = "postgres"
  engine_version           = "14.7"
  instance_class           = var.db_instance_class  # db.r5.xlarge
  allocated_storage        = 100
  storage_type             = "gp3"
  storage_encrypted        = true
  kms_key_id              = aws_kms_key.rds.arn
  
  db_name                  = "openride"
  username                 = var.db_username
  password                 = random_password.db_password.result
  
  multi_az                 = var.environment == "production"
  backup_retention_period  = 7
  backup_window            = "03:00-04:00"
  maintenance_window       = "sun:04:00-sun:05:00"
  
  vpc_security_group_ids   = [aws_security_group.db.id]
  db_subnet_group_name     = aws_db_subnet_group.main.name
  
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
  performance_insights_enabled    = true
  
  skip_final_snapshot      = var.environment != "production"
  final_snapshot_identifier = "openride-${var.environment}-final-snapshot"
  
  tags = {
    Name        = "openride-${var.environment}"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# PostGIS extension (requires custom parameter group)
resource "aws_db_parameter_group" "postgis" {
  name   = "openride-postgis-${var.environment}"
  family = "postgres14"

  parameter {
    name  = "shared_preload_libraries"
    value = "pg_stat_statements,postgis"
  }
  
  parameter {
    name  = "max_connections"
    value = "500"
  }
}
```

---

### **3. MONITORING & OBSERVABILITY (Est. 1 week)**

You need **full visibility** into system health, performance, and errors.

#### 3.1 Metrics (Prometheus + Grafana)

**Prometheus Setup:**
- Deploy Prometheus server in Kubernetes
- Configure service discovery (scrape all pods with `/metrics`)
- Define recording rules (pre-aggregated queries)
- Define alerting rules (when to notify)

**Metrics to Collect:**

**Service-Level Metrics (from Phase 5):**
- `http_requests_total` - Counter by method/endpoint/status
- `http_request_duration_seconds` - Histogram (p50, p95, p99)
- `http_request_size_bytes` - Request body size
- `http_response_size_bytes` - Response body size

**Business Metrics:**
- `bookings_created_total` - Counter
- `bookings_confirmed_total` - Counter
- `bookings_cancelled_total` - Counter
- `payments_success_total` - Counter
- `payments_failed_total` - Counter
- `tickets_issued_total` - Counter
- `otp_sent_total` - Counter
- `otp_verified_total` - Counter

**Matchmaking Metrics (Phase 5):**
- `matching_duration_seconds` - Histogram
- `matching_candidates_total` - Histogram
- `matching_results_total` - Histogram
- `cache_hits_total` - Counter
- `cache_misses_total` - Counter

**Database Metrics:**
- `db_query_duration_seconds` - Histogram
- `db_pool_connections` - Gauge (checked_in, checked_out)
- `db_pool_size` - Gauge
- `db_connection_errors_total` - Counter

**Redis Metrics:**
- `redis_commands_total` - Counter by command
- `redis_command_duration_seconds` - Histogram
- `redis_keyspace_hits_total` - Counter
- `redis_keyspace_misses_total` - Counter
- `redis_connected_clients` - Gauge
- `redis_used_memory_bytes` - Gauge

**JVM Metrics (Java services):**
- `jvm_memory_used_bytes` - Gauge
- `jvm_gc_pause_seconds` - Histogram
- `jvm_threads_current` - Gauge
- `jvm_classes_loaded` - Gauge

**Python Metrics (FastAPI services):**
- `process_cpu_seconds_total` - Counter
- `process_resident_memory_bytes` - Gauge
- `process_open_fds` - Gauge

**Grafana Dashboards:**

**Dashboard 1: System Overview**
- Total requests/sec (all services)
- Error rate (4xx + 5xx)
- p95 latency by service
- Active users (from auth tokens)
- Database connection pool utilization
- Redis cache hit rate

**Dashboard 2: Business Metrics**
- Bookings created/hour
- Booking conversion rate (created → confirmed)
- Revenue per hour (bookings × price)
- Active drivers count
- Active routes count
- Top 10 routes by bookings

**Dashboard 3: Service Health**
- CPU usage by pod
- Memory usage by pod
- Restart count
- Pod ready replicas vs desired
- Network traffic in/out

**Dashboard 4: Database Performance**
- Query latency (p50, p95, p99)
- Connections in use / max connections
- Lock wait time
- Table size growth
- Index usage statistics

**Dashboard 5: Matchmaking Performance** (Phase 5)
- Matching latency distribution
- Candidates per search request
- Cache hit rate
- Hub discovery time
- Route query time

**Dashboard 6: Alerts & SLOs**
- Current firing alerts
- SLO compliance (99.95% uptime)
- Error budget remaining
- Incidents timeline

**Alerting Rules:**

```yaml
groups:
- name: openride-critical
  interval: 30s
  rules:
  
  # High error rate
  - alert: HighErrorRate
    expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.05
    for: 2m
    labels:
      severity: critical
    annotations:
      summary: "High error rate detected"
      description: "{{ $labels.service }} has {{ $value }}% error rate"
  
  # Slow API responses
  - alert: HighLatency
    expr: histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m])) > 0.5
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "API latency degraded"
      description: "{{ $labels.service }} p95 latency is {{ $value }}s"
  
  # Database connection pool exhausted
  - alert: DBPoolExhausted
    expr: db_pool_connections{state="checked_out"} / db_pool_size > 0.9
    for: 2m
    labels:
      severity: critical
    annotations:
      summary: "Database connection pool near limit"
  
  # Booking service down
  - alert: BookingServiceDown
    expr: up{job="booking-service"} == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "Booking service is down"
      description: "No instances of booking-service responding"
  
  # Cache hit rate low
  - alert: LowCacheHitRate
    expr: rate(cache_hits_total[5m]) / (rate(cache_hits_total[5m]) + rate(cache_misses_total[5m])) < 0.7
    for: 10m
    labels:
      severity: warning
    annotations:
      summary: "Cache hit rate below target"
```

---

#### 3.2 Logging (ELK Stack or Loki)

**Centralized Logging Architecture:**

```
Application Logs → Fluent Bit (sidecar) → Elasticsearch → Kibana
                                       → Loki → Grafana
```

**Log Levels & What to Log:**

**ERROR:**
- Exceptions/stack traces
- Failed database queries
- External API failures (payment gateway, SMS)
- Authentication failures

**WARN:**
- Slow queries (>100ms)
- Circuit breaker activations
- Retry attempts
- Cache misses (if frequent)

**INFO:**
- Request started/completed (with correlation ID)
- State transitions (booking created, payment confirmed)
- Scheduled job execution
- Service startup/shutdown

**DEBUG:**
- Function entry/exit
- Variable values
- Query parameters

**Structured Logging Format (JSON):**
```json
{
  "timestamp": "2025-11-15T14:32:18.123Z",
  "level": "ERROR",
  "service": "booking-service",
  "correlation_id": "req-abc123",
  "user_id": "user-xyz789",
  "message": "Failed to acquire seat lock",
  "error": {
    "type": "RedisConnectionException",
    "message": "Connection timeout after 5000ms",
    "stack_trace": "..."
  },
  "context": {
    "route_id": "route-456",
    "seat_count": 2
  }
}
```

**Log Retention:**
- Production: 30 days in hot storage, 90 days in cold storage
- Staging: 7 days
- Development: 3 days

**Kibana Dashboards:**
- Error log stream (last 15 minutes)
- Slow query log (>100ms)
- Authentication failures (potential attacks)
- Payment webhook failures
- Correlation ID search (trace single request)

---

#### 3.3 Tracing (Jaeger/Tempo)

**Distributed Tracing:**

Trace a single request across all services:

```
User Request → API Gateway → Auth Service → Matchmaking Service → Database
                                                                  → Redis
```

**OpenTelemetry Integration:**

**Java (Spring Boot):**
```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
</dependency>
```

**Python (FastAPI):**
```python
from opentelemetry import trace
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor

FastAPIInstrumentor.instrument_app(app)
```

**What Gets Traced:**
- HTTP requests (inbound/outbound)
- Database queries
- Redis operations
- Kafka message publishing
- External API calls (Interswitch, Twilio)

**Trace Spans:**
```
POST /v1/bookings [500ms total]
├─ Validate auth token [5ms]
├─ Get route details [80ms]
│  └─ Database query [75ms]
├─ Acquire seat lock [320ms] ← SLOW!
│  └─ Redis SET NX [315ms]
└─ Create booking record [95ms]
   └─ Database INSERT [90ms]
```

**Jaeger UI Features:**
- Search traces by service/operation/duration
- Dependency graph (service map)
- Span timeline visualization
- Error trace highlighting

---

### **4. CI/CD PIPELINE (Est. 3-5 days)**

**GitHub Actions Workflow (.github/workflows/deploy.yml):**

```yaml
name: Build, Test & Deploy

on:
  push:
    branches: [main, staging]
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_PREFIX: openride

jobs:
  # Job 1: Lint & Format Check
  lint:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service:
          - auth-service
          - booking-service
          - matchmaking-service
    steps:
      - uses: actions/checkout@v3
      
      # Java services
      - name: Checkstyle (Java)
        if: contains(matrix.service, '-service') && !contains(matrix.service, 'matchmaking')
        run: |
          cd services/java/${{ matrix.service }}
          mvn checkstyle:check
      
      # Python services
      - name: Black & Flake8 (Python)
        if: contains(matrix.service, 'matchmaking')
        run: |
          cd services/python/${{ matrix.service }}
          black --check .
          flake8 .

  # Job 2: Unit Tests
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service:
          - auth-service
          - booking-service
          - matchmaking-service
    steps:
      - uses: actions/checkout@v3
      
      # Java tests
      - name: Run Java tests
        if: contains(matrix.service, '-service') && !contains(matrix.service, 'matchmaking')
        run: |
          cd services/java/${{ matrix.service }}
          mvn test
          mvn jacoco:report
      
      # Python tests
      - name: Run Python tests
        if: contains(matrix.service, 'matchmaking')
        run: |
          cd services/python/${{ matrix.service }}
          pytest --cov --cov-report=xml
      
      # Upload coverage
      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3

  # Job 3: Integration Tests
  integration-test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgis/postgis:14-3.3
        env:
          POSTGRES_PASSWORD: test
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
      redis:
        image: redis:7-alpine
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
    steps:
      - uses: actions/checkout@v3
      - name: Run integration tests
        run: ./scripts/run-integration-tests.sh

  # Job 4: Security Scan
  security:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Run Trivy vulnerability scanner
        uses: aquasecurity/trivy-action@master
        with:
          scan-type: 'fs'
          scan-ref: '.'
          severity: 'CRITICAL,HIGH'
      
      - name: Run Snyk security scan
        uses: snyk/actions/maven@master
        env:
          SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}

  # Job 5: Build & Push Docker Images
  build:
    needs: [lint, test, integration-test, security]
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service:
          - auth-service
          - booking-service
          - matchmaking-service
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v2
      
      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v2
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Build and push
        uses: docker/build-push-action@v4
        with:
          context: ./services/java/${{ matrix.service }}
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/${{ matrix.service }}:latest
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/${{ matrix.service }}:${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

  # Job 6: Deploy to Staging
  deploy-staging:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/staging'
    steps:
      - uses: actions/checkout@v3
      
      - name: Configure kubectl
        uses: azure/k8s-set-context@v3
        with:
          kubeconfig: ${{ secrets.KUBE_CONFIG_STAGING }}
      
      - name: Deploy to staging
        run: |
          kubectl set image deployment/booking-service \
            booking-service=${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/booking-service:${{ github.sha }} \
            -n openride-staging
          kubectl rollout status deployment/booking-service -n openride-staging

  # Job 7: Deploy to Production
  deploy-production:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    environment:
      name: production
      url: https://api.openride.com
    steps:
      - uses: actions/checkout@v3
      
      - name: Configure kubectl
        uses: azure/k8s-set-context@v3
        with:
          kubeconfig: ${{ secrets.KUBE_CONFIG_PROD }}
      
      - name: Deploy to production
        run: |
          kubectl set image deployment/booking-service \
            booking-service=${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/booking-service:${{ github.sha }} \
            -n openride-production
          kubectl rollout status deployment/booking-service -n openride-production
      
      - name: Run smoke tests
        run: ./scripts/smoke-tests.sh https://api.openride.com
```

**What CI/CD Does:**
1. **On Pull Request:** Lint → Test → Security Scan
2. **On Merge to Staging:** Full pipeline → Deploy to staging
3. **On Merge to Main:** Full pipeline → Deploy to production (with approval)
4. **Rollback:** `kubectl rollout undo deployment/booking-service`

---

### **5. DATABASE MIGRATION STRATEGY**

**Zero-Downtime Migration Process:**

**Step 1: Preparation (Dev/Staging)**
```bash
# Test migration on staging
kubectl exec -it postgres-0 -- psql -U openride -d openride -f V13_001_optimize_queries.sql

# Verify indices created
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'routes';

# Check query plans use new indices
EXPLAIN ANALYZE SELECT * FROM routes WHERE origin_hub_id = '...' AND status = 'ACTIVE';
```

**Step 2: Production Migration (During Low Traffic)**
```bash
# Backup first
pg_dump -U openride -d openride > backup_$(date +%Y%m%d).sql

# Run migration
kubectl exec -it openride-db-0 -- psql -U openride -d openride -f /migrations/V13_001_optimize_queries.sql

# Monitor performance
SELECT * FROM pg_stat_user_indexes WHERE schemaname = 'public';
```

**Step 3: Validation**
```bash
# Verify no breaking changes
./scripts/integration-tests.sh production

# Monitor metrics
# - Query latency should decrease
# - No error rate increase
```

**Step 4: Rollback Plan (If Needed)**
```sql
-- Drop indices if they cause issues
DROP INDEX IF EXISTS idx_routes_hubs_status_time;
DROP INDEX IF EXISTS idx_route_stops_covering;
-- etc.
```

---

### **6. OPERATIONAL RUNBOOKS**

**Runbook = Step-by-step guide for ops team**

**Runbook 1: Service Deployment**
```markdown
# Service Deployment Runbook

## Prerequisites
- [ ] All tests passing in CI/CD
- [ ] Approval from tech lead
- [ ] Change request ticket created

## Steps
1. Notify team in #deployments Slack channel
2. Enable maintenance mode (optional)
3. Deploy to staging first:
   ```
   kubectl apply -f k8s/deployments/booking-service.yaml -n staging
   ```
4. Run smoke tests on staging
5. Deploy to production:
   ```
   kubectl apply -f k8s/deployments/booking-service.yaml -n production
   ```
6. Monitor metrics for 15 minutes
7. Mark deployment as complete in ticket

## Rollback
```
kubectl rollout undo deployment/booking-service -n production
```
```

**Runbook 2: Database Incident Response**
```markdown
# Database Incident Runbook

## Symptom: High database CPU (>80%)

### Investigation
1. Check current queries:
   ```sql
   SELECT pid, query, state, wait_event 
   FROM pg_stat_activity 
   WHERE state != 'idle' 
   ORDER BY query_start;
   ```

2. Find slow queries:
   ```sql
   SELECT query, mean_exec_time, calls 
   FROM pg_stat_statements 
   ORDER BY mean_exec_time DESC 
   LIMIT 10;
   ```

3. Check locks:
   ```sql
   SELECT * FROM pg_locks WHERE NOT granted;
   ```

### Mitigation
1. Kill long-running queries (if safe):
   ```sql
   SELECT pg_terminate_backend(pid) WHERE pid = <problematic_pid>;
   ```

2. Increase connection pool size (temporary):
   ```
   kubectl edit configmap booking-service-config
   # Set max_db_connections: 100
   kubectl rollout restart deployment/booking-service
   ```

3. Enable read replicas:
   ```
   # Route read-only queries to replica
   UPDATE config SET replica_database_url = '...'
   ```

### Prevention
- Review query plans for full table scans
- Add missing indices
- Implement connection pooling limits
```

**Runbook 3: Payment Webhook Failure**
```markdown
# Payment Webhook Failure Runbook

## Symptom: Bookings stuck in HELD status

### Investigation
1. Check webhook logs:
   ```
   kubectl logs -l app=payments-service --tail=100 | grep "webhook"
   ```

2. Check Redis for pending webhooks:
   ```
   redis-cli KEYS "webhook:pending:*"
   ```

3. Query stuck bookings:
   ```sql
   SELECT id, status, created_at, updated_at 
   FROM bookings 
   WHERE status = 'HELD' 
   AND created_at < NOW() - INTERVAL '15 minutes';
   ```

### Mitigation
1. Manually verify payment status with Interswitch API
2. Update booking status manually (if payment confirmed):
   ```sql
   UPDATE bookings SET status = 'CONFIRMED' WHERE id = '...';
   ```

3. Refund if payment failed:
   ```
   POST /v1/admin/payments/{id}/refund
   ```

### Prevention
- Monitor webhook success rate
- Implement retry queue for failed webhooks
- Set up alert for bookings stuck >20 minutes
```

**Additional Runbooks Needed:**
- Redis cache failure recovery
- Kafka broker down
- Kubernetes node failure
- SSL certificate renewal
- Database backup/restore
- Scaling services manually
- Log analysis for security incidents

---

### **7. DOCUMENTATION**

**User Documentation:**
- API Reference (Swagger/OpenAPI)
- Authentication guide
- Rate limiting policy
- Error codes reference
- Webhook integration guide (for partners)

**Developer Documentation:**
- Architecture overview
- Service dependency map
- Database schema documentation
- Local development setup
- Testing guide
- Code contribution guidelines

**Operations Documentation:**
- Deployment procedures
- Monitoring dashboard guide
- Alert response playbooks
- Disaster recovery plan
- Backup/restore procedures
- Security incident response

---

## 📋 PHASE 12 CHECKLIST SUMMARY

### Testing (2-3 weeks)
- [ ] **Unit tests:** 80%+ coverage, ~1,280 tests across 12 services
- [ ] **Integration tests:** 50+ tests covering critical user journeys
- [ ] **Load testing:** k6 scripts validating performance targets (p95 <200ms)
- [ ] **Security audit:** OWASP ZAP scan, dependency vulnerability check, manual review

### Infrastructure (1-2 weeks)
- [ ] **Kubernetes manifests:** 12 deployments, 12 services, HPAs, ingress
- [ ] **Terraform:** VPC, RDS, ElastiCache, EKS, Kafka, S3, monitoring
- [ ] **Environments:** Dev, staging, production (separate configs)

### Monitoring (1 week)
- [ ] **Prometheus:** 50+ metrics across all services
- [ ] **Grafana:** 6 dashboards (overview, business, health, DB, matching, alerts)
- [ ] **Alerting:** 15+ critical alert rules
- [ ] **Logging:** ELK/Loki stack with structured JSON logs
- [ ] **Tracing:** Jaeger/Tempo with OpenTelemetry

### CI/CD (3-5 days)
- [ ] **GitHub Actions:** Lint → Test → Build → Deploy pipeline
- [ ] **Docker images:** Multi-stage builds, security scanning
- [ ] **Deployment automation:** Staging → Production with approvals
- [ ] **Rollback procedures:** One-command rollback capability

### Operations (1 week)
- [ ] **Runbooks:** 10+ operational playbooks
- [ ] **Database migration:** Zero-downtime migration process
- [ ] **Disaster recovery:** Backup/restore procedures tested
- [ ] **Documentation:** API docs, developer guides, ops manuals

### Final Validation (3-5 days)
- [ ] **Staging deployment:** Full system running in staging
- [ ] **End-to-end testing:** All user journeys validated
- [ ] **Performance testing:** Load tests passing (1,000+ concurrent users)
- [ ] **Security review:** Penetration testing complete, vulnerabilities fixed
- [ ] **Production deployment:** Phased rollout with monitoring

---

## ⏱️ TIME ESTIMATE

**Conservative Estimate: 6-8 weeks**
- Testing: 2-3 weeks (most time-consuming)
- Infrastructure: 1-2 weeks
- Monitoring: 1 week
- CI/CD: 3-5 days
- Operations: 1 week
- Final validation: 3-5 days

**Aggressive Estimate: 4-5 weeks** (if you skip some optional items)

---

## 🚨 CRITICAL PATH ITEMS

These **MUST** be done before production:

1. ✅ **Booking service tests** - Prevents double-booking disasters
2. ✅ **Payment webhook tests** - Prevents revenue loss
3. ✅ **Load testing** - Proves system can handle traffic
4. ✅ **Database migration** - Applies Phase 5 performance indices
5. ✅ **Kubernetes manifests** - Can't deploy without these
6. ✅ **Monitoring** - Can't operate blind
7. ✅ **Security audit** - Prevents breaches

---

**Next Steps:** Which part of Phase 12 would you like to start with?