# OpenRide Backend - Quick Reference Card

## 🚀 Quick Commands

### Setup & Installation
```bash
# Automated setup (Windows)
setup.bat

# Automated setup (Linux/Mac)
chmod +x setup.sh && ./setup.sh

# Manual setup
docker-compose up -d
cd shared/java-commons && mvn clean install
cd shared/python-commons && pip install -e .
```

### Infrastructure Management
```bash
# Start all services
docker-compose up -d

# Stop all services
docker-compose down

# View logs
docker-compose logs -f postgres
docker-compose logs -f redis

# Restart a service
docker-compose restart postgres

# Reset everything (⚠️ DESTRUCTIVE)
docker-compose down -v
docker-compose up -d
```

### Database
```bash
# Connect to PostgreSQL
docker exec -it openride-postgres psql -U openride_user -d openride

# Check PostGIS
docker exec -it openride-postgres psql -U openride_user -d openride -c "SELECT PostGIS_version();"

# Run SQL script
docker exec -i openride-postgres psql -U openride_user -d openride < script.sql
```

### Redis
```bash
# Connect to Redis CLI
docker exec -it openride-redis redis-cli -a openride_redis_password

# Check Redis
docker exec -it openride-redis redis-cli -a openride_redis_password ping

# Get all keys
docker exec -it openride-redis redis-cli -a openride_redis_password KEYS '*'
```

### Java Development
```bash
# Build Java Commons
cd shared/java-commons && mvn clean install

# Run tests
mvn test

# Run with coverage
mvn test jacoco:report

# Format code
mvn spotless:apply

# Lint code
mvn checkstyle:check

# Run Flyway migration
mvn flyway:migrate
```

### Python Development
```bash
# Install Python Commons
cd shared/python-commons && pip install -e .

# Run tests
pytest

# Run tests with coverage
pytest --cov=openride_commons --cov-report=html

# Format code
black .

# Lint code
flake8 .

# Type check
mypy .

# Run Alembic migration
alembic upgrade head
```

## 📊 Service Ports

| Service | Port | URL |
|---------|------|-----|
| PostgreSQL | 5432 | - |
| Redis | 6379 | - |
| pgAdmin | 5050 | http://localhost:5050 |
| Redis Commander | 8081 | http://localhost:8081 |
| Auth Service | 8081 | http://localhost:8081 |
| User Service | 8082 | http://localhost:8082 |
| Booking Service | 8083 | http://localhost:8083 |
| Payments Service | 8084 | http://localhost:8084 |
| Ticketing Service | 8085 | http://localhost:8085 |
| Payouts Service | 8086 | http://localhost:8086 |
| Driver Service | 8091 | http://localhost:8091 |
| Matchmaking Service | 8092 | http://localhost:8092 |
| Search Service | 8093 | http://localhost:8093 |
| Notification Service | 8094 | http://localhost:8094 |
| Analytics Service | 8095 | http://localhost:8095 |
| Fleet Service | 8096 | http://localhost:8096 |

## 🔑 Default Credentials

### pgAdmin
- URL: http://localhost:5050
- Email: `admin@openride.com`
- Password: `admin`

### PostgreSQL
- Host: `localhost:5432`
- Database: `openride`
- Username: `openride_user`
- Password: `openride_password`

### Redis
- Host: `localhost:6379`
- Password: `openride_redis_password`

## 📁 Project Structure

```
openride-backend/
├── services/               # Microservices
│   ├── java/              # Spring Boot services
│   └── python/            # FastAPI services
├── shared/                # Shared libraries
│   ├── java-commons/      # Java utilities
│   └── python-commons/    # Python utilities
├── infrastructure/        # Infrastructure configs
│   ├── docker/           # Docker files
│   ├── kubernetes/       # K8s manifests
│   └── terraform/        # IaC scripts
├── .github/workflows/     # CI/CD pipelines
├── docs/                  # Documentation
├── docker-compose.yml     # Local dev setup
├── .env.example          # Environment template
└── README.md             # Main documentation
```

## 🧪 Testing

### Java
```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Coverage report
mvn test jacoco:report
# Report: target/site/jacoco/index.html
```

### Python
```bash
# All tests
pytest

# Specific test file
pytest tests/test_security.py

# With coverage
pytest --cov=app --cov-report=html
# Report: htmlcov/index.html

# Verbose
pytest -v
```

## 🔒 Environment Variables

### Required
```bash
DATABASE_URL=postgresql://user:pass@host:5432/db
REDIS_URL=redis://:password@host:6379
JWT_SECRET_KEY=your-secret-key
```

### Optional
```bash
LOG_LEVEL=INFO
ENVIRONMENT=development
CORS_ORIGINS=http://localhost:3000
```

See `.env.example` for full list.

## 🚦 Git Workflow

### Branch Naming
```bash
feature/OR-123-description
bugfix/OR-456-description
hotfix/OR-789-description
```

### Commit Format
```bash
feat(scope): description
fix(scope): description
docs(scope): description
```

### Create PR
```bash
git checkout -b feature/OR-123-add-booking
# Make changes
git add .
git commit -m "feat(booking): add seat hold mechanism"
git push origin feature/OR-123-add-booking
# Create PR on GitHub
```

## 📚 Documentation Links

- **Main README**: [README.md](../README.md)
- **Development Guidelines**: [DEVELOPMENT.md](../DEVELOPMENT.md)
- **Implementation Plan**: [BACKEND_IMPLEMENTATION_PLAN.md](../BACKEND_IMPLEMENTATION_PLAN.md)
- **Phase 0 Summary**: [docs/PHASE_0_SUMMARY.md](PHASE_0_SUMMARY.md)
- **Java Commons**: [shared/java-commons/README.md](../shared/java-commons/README.md)
- **Python Commons**: [shared/python-commons/README.md](../shared/python-commons/README.md)

## 🐛 Troubleshooting

### Database connection failed
```bash
# Check if PostgreSQL is running
docker ps | grep postgres

# Restart PostgreSQL
docker-compose restart postgres

# Check logs
docker-compose logs postgres
```

### Redis connection failed
```bash
# Check if Redis is running
docker ps | grep redis

# Restart Redis
docker-compose restart redis

# Test connection
docker exec -it openride-redis redis-cli -a openride_redis_password ping
```

### Port already in use
```bash
# Find process using port (Linux/Mac)
lsof -i :5432

# Find process using port (Windows)
netstat -ano | findstr :5432

# Kill process
kill -9 <PID>  # Linux/Mac
taskkill /PID <PID> /F  # Windows
```

### Clean start
```bash
# Remove all containers and volumes
docker-compose down -v

# Remove Docker images
docker-compose down --rmi all

# Start fresh
docker-compose up -d
```

## ✨ Tips

- Always update `.env` before running services
- Run tests before committing
- Use correlation IDs for debugging
- Check logs when debugging: `docker-compose logs -f <service>`
- Use pgAdmin for database queries
- Use Redis Commander for cache inspection

## 🆘 Getting Help

- **Slack**: #backend-dev
- **Email**: support@openride.com
- **Docs**: Read [DEVELOPMENT.md](../DEVELOPMENT.md)
- **Issues**: GitHub Issues

---

**Last Updated**: November 12, 2025
