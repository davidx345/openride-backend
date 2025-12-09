# Environment File Setup Guide

## Quick Setup

The `.env` file should be placed in the **`deployment/`** directory.

### On Droplets (Java or Python)

```bash
cd ~/openride-backend/deployment
cp .env.example .env
nano .env  # Fill in your actual secrets
```

## What I Fixed

### Python Build Errors

1. **fleet-service Dockerfile** - Changed COPY order:
   - ❌ Before: Copied `pyproject.toml` first, then tried `pip install -e .` (failed because `app/` didn't exist)
   - ✅ After: Copy entire service directory first, then install

2. **notification-service pyproject.toml** - Removed broken dependency:
   - ❌ Before: `openride-commons = {path = "../../../shared/python-commons", develop = true}`
   - ✅ After: Commented out (path doesn't exist in Docker build context)

### Java .env Location

Updated both `deploy-java.sh` and `deploy-python.sh` to:
- Check `deployment/.env` first (correct location)
- Fall back to `PROJECT_ROOT/.env` if found
- Copy it to `deployment/.env` automatically
- Exit with clear error if neither exists

## Next Steps on Droplets

### Java Droplet
```bash
cd ~/openride-backend
git pull origin main
cd deployment
cp .env.example .env
nano .env  # Fill in secrets
./scripts/deploy-java.sh
```

### Python Droplet
```bash
cd ~/openride-backend
git pull origin main
cd deployment
cp .env.example .env
nano .env  # Fill in secrets

# Build one service at a time to avoid OOM
docker compose -f deployment/docker-compose.python.yml build analytics-service
docker compose -f deployment/docker-compose.python.yml build driver-service
docker compose -f deployment/docker-compose.python.yml build fleet-service
docker compose -f deployment/docker-compose.python.yml build matchmaking-service
docker compose -f deployment/docker-compose.python.yml build notification-service
docker compose -f deployment/docker-compose.python.yml build search-service

# Deploy all
docker compose -f deployment/docker-compose.python.yml up -d
```
