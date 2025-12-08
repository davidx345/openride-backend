# OpenRIDE Deployment

This directory contains all deployment configurations and scripts for deploying OpenRIDE backend to DigitalOcean with a 2-Droplet architecture.

## 📁 Directory Structure

```
deployment/
├── DEPLOYMENT_GUIDE.md          # Complete step-by-step deployment guide
├── .env.example                  # Environment variables template
├── docker-compose.java.yml       # Java services orchestration
├── docker-compose.python.yml     # Python services orchestration
├── nginx/
│   ├── java-droplet.conf        # Nginx config for Java services
│   └── python-droplet.conf      # Nginx config for Python services
└── scripts/
    ├── setup-java-droplet.sh    # Initial Java droplet setup
    ├── setup-python-droplet.sh  # Initial Python droplet setup
    ├── deploy-java.sh           # Deploy Java services
    ├── deploy-python.sh         # Deploy Python services
    └── setup-ssl.sh             # SSL certificate setup
```

## 🚀 Quick Start

### 1. Prerequisites

- DigitalOcean account with $200 credit
- Supabase account (free)
- Domain name (optional)
- SSH key pair

### 2. Create Droplets

Create two Ubuntu 22.04 droplets:
- **Java Droplet**: 2GB RAM ($12/month) for Java microservices
- **Python Droplet**: 2GB RAM ($12/month) for Python microservices

### 3. Set Up Droplets

```bash
# On Java Droplet
ssh root@your-java-droplet-ip
git clone https://github.com/davidx345/openride-backend.git
cd openride-backend/deployment/scripts
chmod +x *.sh
./setup-java-droplet.sh

# On Python Droplet
ssh root@your-python-droplet-ip
git clone https://github.com/davidx345/openride-backend.git
cd openride-backend/deployment/scripts
chmod +x *.sh
./setup-python-droplet.sh
```

### 4. Configure Environment

```bash
# On each droplet
cd ~/openride-backend/deployment
cp .env.example .env
nano .env  # Fill in your values
```

### 5. Deploy Services

```bash
# On Java Droplet
./scripts/deploy-java.sh

# On Python Droplet
./scripts/deploy-python.sh
```

### 6. Set Up SSL (Optional)

```bash
# On each droplet
./scripts/setup-ssl.sh your-domain.com your-email@example.com
```

## 📊 Architecture

### Cost Breakdown

- **Minimal**: $24/month (2 droplets) → $200 lasts **8+ months**
- **Recommended**: $36-61/month (larger droplets + Supabase) → $200 lasts **3-6 months**

### Service Distribution

**Java Droplet** (Port mapping via Nginx):
- auth-service → /auth/
- user-service → /users/
- booking-service → /bookings/
- payments-service → /payments/
- payouts-service → /payouts/
- ticketing-service → /tickets/
- admin-service → /admin/

**Python Droplet** (Port mapping via Nginx):
- analytics-service → /analytics/
- driver-service → /drivers/
- fleet-service → /fleet/
- matchmaking-service → /matchmaking/
- notification-service → /notifications/
- search-service → /search/

## 🔧 Common Commands

```bash
# View running services
docker-compose -f docker-compose.java.yml ps

# View logs
docker-compose -f docker-compose.java.yml logs -f [service-name]

# Restart a service
docker-compose -f docker-compose.java.yml restart [service-name]

# Stop all services
docker-compose -f docker-compose.java.yml down

# Update and redeploy
git pull origin main
./scripts/deploy-java.sh
```

## 📖 Full Documentation

For complete deployment instructions, troubleshooting, and best practices, see:
**[DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)**

## 🔄 CI/CD

GitHub Actions workflow automatically:
1. Builds Docker images on push to `main`
2. Pushes images to GitHub Container Registry
3. Deploys to both droplets via SSH

Required GitHub Secrets:
- `JAVA_DROPLET_HOST`
- `PYTHON_DROPLET_HOST`
- `DROPLET_USER`
- `DROPLET_SSH_KEY`

## 🐛 Troubleshooting

### Service won't start
```bash
docker-compose logs [service-name]
```

### Check resource usage
```bash
docker stats
htop
```

### Test database connection
```bash
curl http://localhost:8081/actuator/health
```

See [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) for more troubleshooting steps.

## 📞 Support

- Full Guide: [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)
- API Docs: `/docs/api/`
- Issues: https://github.com/davidx345/openride-backend/issues
