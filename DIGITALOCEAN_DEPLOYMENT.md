# OpenRIDE - DigitalOcean Deployment Quick Start

## 🎯 Overview

Deploy OpenRIDE backend to DigitalOcean using **2 Droplets** (one for Java services, one for Python services) with **Supabase** as the database.

**Cost**: $24/month → Your $200 credit lasts **8+ months**

## 📋 What You Need

- [ ] DigitalOcean account with $200 credit
- [ ] Supabase account (free tier)
- [ ] SSH key pair (`ssh-keygen -t ed25519`)
- [ ] 30-60 minutes

## 🚀 5-Step Deployment

### Step 1: Create Supabase Database (5 min)

1. Go to https://supabase.com/dashboard
2. Create new project → Note down connection details
3. Run database migrations from `infrastructure/docker/migrations/`

### Step 2: Create DigitalOcean Droplets (10 min)

Create two droplets:

**Java Droplet**:
- Ubuntu 22.04 LTS
- Basic plan: $12/month (2GB RAM, 1 vCPU)
- Add your SSH key
- Hostname: `openride-java`

**Python Droplet**:
- Same specs as above
- Hostname: `openride-python`

### Step 3: Set Up Droplets (15 min)

```bash
# Java Droplet
ssh root@<java-droplet-ip>
git clone https://github.com/davidx345/openride-backend.git
cd openride-backend/deployment/scripts
chmod +x *.sh
./setup-java-droplet.sh
exit

# Python Droplet
ssh root@<python-droplet-ip>
git clone https://github.com/davidx345/openride-backend.git
cd openride-backend/deployment/scripts
chmod +x *.sh
./setup-python-droplet.sh
exit
```

### Step 4: Configure Environment (10 min)

```bash
# On BOTH droplets
ssh openride@<droplet-ip>
cd ~/openride-backend/deployment
cp .env.example .env
nano .env

# Fill in:
# - Database credentials from Supabase
# - JWT secret (generate: openssl rand -hex 32)
# - API keys (Twilio, Stripe, etc.)
```

### Step 5: Deploy Services (10 min)

```bash
# On Java Droplet
cd ~/openride-backend/deployment
./scripts/deploy-java.sh

# On Python Droplet
cd ~/openride-backend/deployment
./scripts/deploy-python.sh
```

## ✅ Verify Deployment

```bash
# Check services are running
docker-compose -f docker-compose.java.yml ps
docker-compose -f docker-compose.python.yml ps

# Test health endpoints
curl http://localhost:8081/actuator/health  # auth-service
curl http://localhost:8097/health           # analytics-service
```

## 🌐 Optional: Set Up Domain & SSL (20 min)

1. Point your domain to droplet IPs:
   - `api-java.yourdomain.com` → Java Droplet IP
   - `api-python.yourdomain.com` → Python Droplet IP

2. Set up SSL on each droplet:
```bash
cd ~/openride-backend/deployment/scripts
./setup-ssl.sh api-java.yourdomain.com your-email@example.com
```

3. Configure and restart Nginx:
```bash
sudo cp ~/openride-backend/deployment/nginx/java-droplet.conf \
        /etc/nginx/sites-available/openride
sudo ln -s /etc/nginx/sites-available/openride /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

## 📊 What's Deployed

### Java Droplet Services
- Auth Service (8081) - User authentication
- User Service (8082) - User management
- Booking Service (8083) - Ride bookings
- Payments Service (8084) - Payment processing
- Payouts Service (8085) - Driver payouts
- Ticketing Service (8086) - Support tickets
- Admin Service (8087) - Admin operations

### Python Droplet Services
- Analytics Service (8097) - Analytics & reporting
- Driver Service (8090) - Driver management
- Fleet Service (8096) - Fleet operations
- Matchmaking Service (8091) - Ride matching
- Notification Service (8095) - Notifications
- Search Service (8092) - Search functionality

## 🔄 Automated Deployment (CI/CD)

Set up GitHub Actions for automatic deployments:

1. Add secrets to your GitHub repository:
   - `JAVA_DROPLET_HOST`
   - `PYTHON_DROPLET_HOST`
   - `DROPLET_USER`
   - `DROPLET_SSH_KEY`

2. Push to `main` branch → automatic deployment

## 📖 Full Documentation

**Comprehensive guide**: [deployment/DEPLOYMENT_GUIDE.md](deployment/DEPLOYMENT_GUIDE.md)

Includes:
- Detailed setup instructions
- Monitoring & maintenance
- Troubleshooting guide
- Security best practices
- Scaling strategies

## 🆘 Quick Help

### View logs
```bash
docker-compose -f docker-compose.java.yml logs -f [service-name]
```

### Restart service
```bash
docker-compose -f docker-compose.java.yml restart [service-name]
```

### Check resources
```bash
docker stats
htop
```

### Redeploy after code changes
```bash
git pull origin main
./scripts/deploy-java.sh
```

## 💰 Cost Management

- **Current setup**: $24/month (2 droplets)
- **Credit duration**: 8+ months with $200 credit
- **Scaling**: Upgrade droplets when you hit 1,000+ active users

## 🎉 You're Done!

Your OpenRIDE backend is now running on DigitalOcean!

**Next steps**:
1. Test API endpoints
2. Deploy frontend
3. Set up monitoring alerts
4. Configure backups

For questions or issues, see the full [DEPLOYMENT_GUIDE.md](deployment/DEPLOYMENT_GUIDE.md).
