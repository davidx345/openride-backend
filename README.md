# OpenRide Backend

> Fixed-route carpooling platform for Lagos, Nigeria daily commuters

OpenRide connects car owners (drivers) who post recurring fixed routes with riders needing affordable daily transport between community hubs and workplaces.

## 🏗️ Architecture Overview

OpenRide is built using a **microservices architecture** with 12 independent services:

### Java Spring Boot Services (High-Throughput, Transactional)

| Service | Purpose | Port |
|---------|---------|------|
| **Auth Service** | OTP verification, JWT token management | 8081 |
| **User Service** | User profiles, KYC management | 8082 |
| **Booking Service** ⭐ | Seat inventory with ACID guarantees | 8083 |
| **Payments Service** ⭐ | Payment orchestration, webhooks | 8084 |
| **Payouts Service** | Driver earnings, settlements | 8085 |
| **Ticketing Service** ✅ | Blockchain-anchored digital tickets | 8086 |

### Python FastAPI Services (Fast Iteration, ML/Geospatial)

| Service | Purpose | Port |
|---------|---------|------|
| **Driver Service** | Route/vehicle management | 8091 |
| **Matchmaking Service** ⭐ | AI/ML route matching engine | 8092 |
| **Search Service** | Geospatial route discovery | 8093 |
| **Notification Service** | Push/SMS/Email notifications | 8094 |
| **Analytics Service** | Event streaming, metrics | 8095 |
| **Fleet Service** | Real-time location tracking | 8096 |

### Shared Infrastructure

- **Database**: PostgreSQL 14 + PostGIS 3.3
- **Cache**: Redis 7 (Cluster mode in production)
- **Message Broker**: Kafka (for analytics pipeline)
- **API Gateway**: Kong/Envoy
- **Monitoring**: Prometheus + Grafana + ELK

---

## 🚀 Quick Start

### Prerequisites

- **Docker** and **Docker Compose**
- **Java 17** (for Java services)
- **Python 3.11+** (for Python services)
- **Maven 3.9+**
- **Git**

### 1. Clone the Repository

```bash
git clone https://github.com/openride/openride-backend.git
cd openride-backend
```

### 2. Start Infrastructure Services

```bash
docker-compose up -d
```

This starts:
- PostgreSQL (port 5432)
- Redis (port 6379)
- pgAdmin (port 5050)
- Redis Commander (port 8081)

**Access pgAdmin**: http://localhost:5050
- Email: `admin@openride.com`
- Password: `admin`

**Access Redis Commander**: http://localhost:8081

### 3. Install Shared Libraries

#### Java Commons

```bash
cd shared/java-commons
mvn clean install
cd ../..
```

#### Python Commons

```bash
cd shared/python-commons
pip install -e .
cd ../..
```

### 4. Verify Database Setup

```bash
# Connect to PostgreSQL
docker exec -it openride-postgres psql -U openride_user -d openride

# Verify PostGIS extension
SELECT PostGIS_version();

# Exit
\q
```

### 5. Run a Service (Example: Auth Service)

**Note**: Individual services will be built in subsequent phases. For now, the infrastructure is ready.

---

## 📁 Project Structure

```
openride-backend/
├── services/
│   ├── java/                      # Java Spring Boot services
│   │   ├── auth-service/
│   │   ├── user-service/
│   │   ├── booking-service/
│   │   ├── payments-service/
│   │   ├── ticketing-service/
│   │   └── payouts-service/
│   └── python/                    # Python FastAPI services
│       ├── driver-service/
│       ├── matchmaking-service/
│       ├── search-service/
│       ├── notification-service/
│       ├── analytics-service/
│       └── fleet-service/
├── shared/
│   ├── java-commons/              # Shared Java utilities
│   └── python-commons/            # Shared Python utilities
├── infrastructure/
│   ├── docker/                    # Docker configurations
│   ├── kubernetes/                # K8s manifests (future)
│   └── terraform/                 # IaC (future)
├── docs/                          # Additional documentation
├── .github/workflows/             # CI/CD pipelines
├── docker-compose.yml             # Local development setup
├── DEVELOPMENT.md                 # Development guidelines
└── README.md                      # This file
```

---

## 🛠️ Development Workflow

### Creating a New Feature

1. **Create feature branch**:
   ```bash
   git checkout -b feature/OR-123-add-booking-endpoint
   ```

2. **Follow development guidelines** in [DEVELOPMENT.md](DEVELOPMENT.md)

3. **Write tests** (minimum 80% coverage)

4. **Run linters**:
   - Java: `mvn checkstyle:check`
   - Python: `black . && flake8`

5. **Create Pull Request** following the PR template

6. **Wait for CI/CD** to pass

7. **Get approval** from 2 reviewers

8. **Squash and merge**

### Running Tests

#### Java Services
```bash
cd services/java/{service-name}
mvn test
```

#### Python Services
```bash
cd services/python/{service-name}
pytest
```

### Database Migrations

#### Java (Flyway)
```bash
cd services/java/{service-name}
mvn flyway:migrate
```

#### Python (Alembic)
```bash
cd services/python/{service-name}
alembic upgrade head
```

---

## 🔑 Environment Variables

Each service requires specific environment variables. Create a `.env` file:

```bash
# Database
DATABASE_URL=postgresql://openride_user:openride_password@localhost:5432/openride

# Redis
REDIS_URL=redis://:openride_redis_password@localhost:6379

# JWT
JWT_SECRET_KEY=your-super-secret-key-change-in-production
JWT_EXPIRATION_MINUTES=60

# External Services
TWILIO_ACCOUNT_SID=your-twilio-sid
TWILIO_AUTH_TOKEN=your-twilio-token
INTERSWITCH_MERCHANT_ID=your-merchant-id
```

**⚠️ Never commit `.env` files to version control!**

---

## 📊 Performance Targets

| Metric | Target | Service |
|--------|--------|---------|
| Booking latency (mean) | <150ms | Booking Service |
| Booking latency (P95) | <200ms | Booking Service |
| Matching latency (P95) | ≤200ms | Matchmaking Service |
| Payment success rate | >98% | Payments Service |
| API uptime | 99.95% | All services |

---

## 🧪 Testing Strategy

- **Unit Tests**: Test individual functions/methods
- **Integration Tests**: Test API endpoints end-to-end
- **Load Tests**: Validate performance targets (k6/JMeter)
- **Security Tests**: OWASP ZAP, Snyk, Trivy

**Coverage Requirements**:
- Minimum: 80% overall
- Critical paths (booking, payments): 95%+

---

## 🔒 Security

- **Authentication**: Phone-based OTP + JWT
- **Authorization**: Role-based access control (RIDER, DRIVER, ADMIN)
- **Encryption**: AES-256 for sensitive data (BVN, license numbers)
- **Rate Limiting**: Redis-based distributed rate limiting
- **Secrets Management**: HashiCorp Vault (production)

See [DEVELOPMENT.md](DEVELOPMENT.md) for detailed security guidelines.

---

## 📚 Documentation

- **[DEVELOPMENT.md](DEVELOPMENT.md)**: Development guidelines and standards
- **[BACKEND_IMPLEMENTATION_PLAN.md](BACKEND_IMPLEMENTATION_PLAN.md)**: Detailed implementation plan
- **API Documentation**: Auto-generated via OpenAPI/Swagger
  - Java services: `http://localhost:{port}/swagger-ui.html`
  - Python services: `http://localhost:{port}/docs`

---

## 🚦 CI/CD Pipeline

Automated workflows (GitHub Actions):

- ✅ **Lint & Format Check**: Checkstyle, Black, Flake8
- ✅ **Unit & Integration Tests**: JUnit, pytest
- ✅ **Security Scanning**: Trivy, Snyk, Bandit
- ✅ **Docker Image Build**: Multi-stage builds
- ✅ **Coverage Report**: Codecov integration

---

## 📈 Monitoring & Observability

- **Logs**: Structured JSON logs with correlation IDs
- **Metrics**: Prometheus + Grafana dashboards
- **Tracing**: Jaeger/Tempo with OpenTelemetry
- **Alerts**: PagerDuty integration for critical issues

---

## 🗺️ Implementation Phases

| Phase | Description | Status |
|-------|-------------|--------|
| **Phase 0** | Foundation & Infrastructure | ✅ Complete |
| **Phase 1** | Auth & User Management | 🔄 In Progress |
| **Phase 2** | Driver & Route Management | 📅 Planned |
| **Phase 3** | Search & Matching Engine | 📅 Planned |
| **Phase 4** | Booking & Seat Inventory | 📅 Planned |
| **Phase 5** | Payment Processing | 📅 Planned |
| **Phase 6** | Ticketing & Blockchain | 📅 Planned |
| **Phase 7** | Real-Time Tracking | 📅 Planned |
| **Phase 8** | Notifications | 📅 Planned |
| **Phase 9** | Payouts & Financial | 📅 Planned |
| **Phase 10** | Analytics & Events | 📅 Planned |
| **Phase 11** | Admin Dashboard | 📅 Planned |
| **Phase 12** | Testing & Deployment | 📅 Planned |

See [BACKEND_IMPLEMENTATION_PLAN.md](BACKEND_IMPLEMENTATION_PLAN.md) for detailed phase breakdown.

---

## 🤝 Contributing

1. Read [DEVELOPMENT.md](DEVELOPMENT.md)
2. Create a feature branch
3. Write tests for your changes
4. Ensure CI passes
5. Submit a pull request

---

## 📧 Support

- **Email**: support@openride.com
- **Slack**: #backend-dev channel
- **Issues**: GitHub Issues

---

## 📝 License

Proprietary - OpenRide © 2024

---

## ⚡ Quick Commands

```bash
# Start all infrastructure
docker-compose up -d

# Stop all infrastructure
docker-compose down

# View logs
docker-compose logs -f postgres
docker-compose logs -f redis

# Install Java commons
cd shared/java-commons && mvn clean install

# Install Python commons
cd shared/python-commons && pip install -e .

# Run Java service tests
cd services/java/{service} && mvn test

# Run Python service tests
cd services/python/{service} && pytest

# Format Python code
black shared/python-commons services/python

# Lint Java code
cd shared/java-commons && mvn checkstyle:check

# Database migrations (Java)
cd services/java/{service} && mvn flyway:migrate

# Database migrations (Python)
cd services/python/{service} && alembic upgrade head

# Reset database (⚠️ destructive)
docker-compose down -v
docker-compose up -d
```

---

**Built with ❤️ by the OpenRide Team**
