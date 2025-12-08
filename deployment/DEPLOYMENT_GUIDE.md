# OpenRIDE Backend - Deployment Guide

Complete guide for deploying OpenRIDE backend to DigitalOcean with 2 Droplets.

## 📋 Table of Contents

- [Architecture Overview](#architecture-overview)
- [Cost Breakdown](#cost-breakdown)
- [Prerequisites](#prerequisites)
- [Step 1: Supabase Setup](#step-1-supabase-setup)
- [Step 2: Create DigitalOcean Droplets](#step-2-create-digitalocean-droplets)
- [Step 3: Initial Droplet Setup](#step-3-initial-droplet-setup)
- [Step 4: Configure Environment Variables](#step-4-configure-environment-variables)
- [Step 5: Deploy Services](#step-5-deploy-services)
- [Step 6: Configure Nginx & SSL](#step-6-configure-nginx--ssl)
- [Step 7: Set Up CI/CD](#step-7-set-up-cicd)
- [Monitoring & Maintenance](#monitoring--maintenance)
- [Troubleshooting](#troubleshooting)

---

## 🏗️ Architecture Overview

### Two-Droplet Setup

**Droplet 1: Java Services** (2GB RAM, $12/month)
- auth-service (8081)
- user-service (8082)
- booking-service (8083)
- payments-service (8084)
- payouts-service (8085)
- ticketing-service (8086)
- admin-service (8087)

**Droplet 2: Python Services** (2GB RAM, $12/month)
- analytics-service (8097)
- driver-service (8090)
- fleet-service (8096)
- matchmaking-service (8091)
- notification-service (8095)
- search-service (8092)

**External Services**
- Supabase: PostgreSQL database (free tier or $25/month)
- Optional: Redis (self-hosted or managed $5-10/month)

---

## 💰 Cost Breakdown

### Minimal Setup (Budget-Friendly)
- 2 × 2GB Droplets: **$24/month**
- Supabase (free tier): **$0/month**
- **Total: $24/month** → $200 credit lasts **8+ months**

### Recommended for 1,000+ Users
- 1 × 4GB Java Droplet: **$24/month**
- 1 × 2GB Python Droplet: **$12/month**
- Supabase Starter: **$25/month**
- **Total: $61/month** → $200 credit lasts **3.3 months**

---

## ✅ Prerequisites

### Required Accounts
- [ ] GitHub account
- [ ] DigitalOcean account with $200 credit
- [ ] Supabase account (free)
- [ ] Domain name (optional but recommended)

### Required on Your Local Machine
- Git
- SSH key pair (`ssh-keygen -t ed25519 -C "your_email@example.com"`)
- Basic terminal/bash knowledge

### Third-Party Services (Optional)
- Twilio account (SMS verification)
- Stripe account (payments)
- SendGrid account (emails)
- Firebase project (push notifications)

---

## 📝 Step 1: Supabase Setup

### 1.1 Create Supabase Project

```bash
# Go to: https://supabase.com/dashboard
# Click: New Project
# Fill in:
#   - Project name: openride-backend
#   - Database password: (generate strong password)
#   - Region: Choose closest to your users
```

### 1.2 Get Database Connection Details

```bash
# Dashboard → Settings → Database
# Copy these values (you'll need them later):
DB_HOST=db.xxxxxxxxx.supabase.co
DB_PORT=5432
DB_NAME=postgres
DB_USER=postgres
DB_PASSWORD=your-generated-password
```

### 1.3 Run Database Migrations

```bash
# From your local machine
# Install Flyway or use Supabase SQL Editor

# Option A: Using Supabase SQL Editor
# 1. Go to SQL Editor in Supabase dashboard
# 2. Run migration files from infrastructure/docker/migrations/ in order

# Option B: Using Flyway locally
flyway -url=jdbc:postgresql://db.xxx.supabase.co:5432/postgres \
       -user=postgres \
       -password=your-password \
       -locations=filesystem:./infrastructure/docker/migrations \
       migrate
```

---

## 🖥️ Step 2: Create DigitalOcean Droplets

### 2.1 Create Java Droplet

```bash
# Go to: https://cloud.digitalocean.com/droplets/new

# Configuration:
Choose an image: Ubuntu 22.04 LTS x64
Plan: Basic
CPU options: Regular Intel - $12/month (2GB RAM, 1 vCPU, 50GB SSD)
Datacenter region: Choose closest to users (e.g., New York, San Francisco)
Authentication: SSH keys → Add your public key
Hostname: openride-java
Tags: java, backend, production

# Click: Create Droplet
# Note the IP address: e.g., 192.168.1.100
```

### 2.2 Create Python Droplet

```bash
# Same steps as above, but:
Hostname: openride-python
Tags: python, backend, production

# Note the IP address: e.g., 192.168.1.101
```

### 2.3 Configure DNS (Optional but Recommended)

```bash
# In your domain registrar (e.g., Namecheap, GoDaddy):
# Add A records:
api-java.yourdomain.com    →  192.168.1.100 (Java Droplet IP)
api-python.yourdomain.com  →  192.168.1.101 (Python Droplet IP)

# Wait 5-10 minutes for DNS propagation
# Test: ping api-java.yourdomain.com
```

---

## ⚙️ Step 3: Initial Droplet Setup

### 3.1 Set Up Java Droplet

```bash
# SSH into Java Droplet
ssh root@192.168.1.100

# Create non-root user (recommended)
adduser openride
usermod -aG sudo openride
mkdir -p /home/openride/.ssh
cp ~/.ssh/authorized_keys /home/openride/.ssh/
chown -R openride:openride /home/openride/.ssh
chmod 700 /home/openride/.ssh
chmod 600 /home/openride/.ssh/authorized_keys

# Exit and reconnect as openride user
exit
ssh openride@192.168.1.100

# Clone repository
git clone https://github.com/davidx345/openride-backend.git
cd openride-backend

# Run setup script
cd deployment/scripts
chmod +x *.sh
./setup-java-droplet.sh

# Log out and back in for Docker group to take effect
exit
ssh openride@192.168.1.100
```

### 3.2 Set Up Python Droplet

```bash
# SSH into Python Droplet
ssh root@192.168.1.101

# Create non-root user
adduser openride
usermod -aG sudo openride
mkdir -p /home/openride/.ssh
cp ~/.ssh/authorized_keys /home/openride/.ssh/
chown -R openride:openride /home/openride/.ssh
chmod 700 /home/openride/.ssh
chmod 600 /home/openride/.ssh/authorized_keys

# Exit and reconnect
exit
ssh openride@192.168.1.101

# Clone repository
git clone https://github.com/davidx345/openride-backend.git
cd openride-backend

# Run setup script
cd deployment/scripts
chmod +x *.sh
./setup-python-droplet.sh

# Log out and back in
exit
ssh openride@192.168.1.101
```

---

## 🔐 Step 4: Configure Environment Variables

### 4.1 On Java Droplet

```bash
ssh openride@192.168.1.100
cd ~/openride-backend/deployment

# Copy environment template
cp .env.example .env

# Edit with your values
nano .env
```

**Fill in these critical values:**

```bash
# Database (from Supabase)
DB_HOST=db.xxxxxxxxx.supabase.co
DB_PORT=5432
DB_NAME=postgres
DB_USER=postgres
DB_PASSWORD=your-supabase-password

# JWT Secret (generate with: openssl rand -hex 32)
JWT_SECRET=your-generated-secret-key

# Redis (if using)
REDIS_HOST=localhost  # or managed Redis URL
REDIS_PORT=6379

# Twilio (for SMS)
TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxx
TWILIO_AUTH_TOKEN=your-auth-token
TWILIO_PHONE_NUMBER=+1234567890

# Stripe (for payments)
STRIPE_API_KEY=sk_test_xxxxx
STRIPE_WEBHOOK_SECRET=whsec_xxxxx
```

Save and exit (Ctrl+X, Y, Enter)

### 4.2 On Python Droplet

```bash
ssh openride@192.168.1.101
cd ~/openride-backend/deployment

# Copy environment template
cp .env.example .env

# Edit with same values as Java droplet
nano .env
```

Fill in the same database, Redis, and service credentials.

---

## 🚀 Step 5: Deploy Services

### 5.1 Deploy Java Services

```bash
# On Java Droplet
ssh openride@192.168.1.100
cd ~/openride-backend/deployment

# Build and deploy
./scripts/deploy-java.sh

# Check status
docker-compose -f docker-compose.java.yml ps

# View logs
docker-compose -f docker-compose.java.yml logs -f auth-service
```

### 5.2 Deploy Python Services

```bash
# On Python Droplet
ssh openride@192.168.1.101
cd ~/openride-backend/deployment

# Build and deploy
./scripts/deploy-python.sh

# Check status
docker-compose -f docker-compose.python.yml ps

# View logs
docker-compose -f docker-compose.python.yml logs -f analytics-service
```

### 5.3 Verify Services are Running

```bash
# On each droplet, test internal endpoints:

# Java Droplet
curl http://localhost:8081/actuator/health  # auth-service
curl http://localhost:8082/actuator/health  # user-service

# Python Droplet
curl http://localhost:8097/health  # analytics-service
curl http://localhost:8090/health  # driver-service
```

---

## 🌐 Step 6: Configure Nginx & SSL

### 6.1 Set Up Nginx on Java Droplet

```bash
ssh openride@192.168.1.100

# Copy Nginx configuration
sudo cp ~/openride-backend/deployment/nginx/java-droplet.conf \
        /etc/nginx/sites-available/openride-java

# Update domain in config
sudo nano /etc/nginx/sites-available/openride-java
# Replace 'api-java.yourdomain.com' with your actual domain

# Enable site
sudo ln -s /etc/nginx/sites-available/openride-java \
            /etc/nginx/sites-enabled/

# Remove default site
sudo rm /etc/nginx/sites-enabled/default

# Test configuration
sudo nginx -t

# Reload Nginx
sudo systemctl reload nginx
```

### 6.2 Set Up SSL Certificate (Java Droplet)

```bash
# Run SSL setup script
cd ~/openride-backend/deployment/scripts
./setup-ssl.sh api-java.yourdomain.com your-email@example.com

# Update Nginx config with SSL settings (already in template)
sudo nginx -t
sudo systemctl reload nginx

# Test HTTPS
curl https://api-java.yourdomain.com/health
```

### 6.3 Repeat for Python Droplet

```bash
ssh openride@192.168.1.101

# Copy and configure Nginx
sudo cp ~/openride-backend/deployment/nginx/python-droplet.conf \
        /etc/nginx/sites-available/openride-python

sudo nano /etc/nginx/sites-available/openride-python
# Update domain

sudo ln -s /etc/nginx/sites-available/openride-python \
            /etc/nginx/sites-enabled/
sudo rm /etc/nginx/sites-enabled/default

# Set up SSL
cd ~/openride-backend/deployment/scripts
./setup-ssl.sh api-python.yourdomain.com your-email@example.com

sudo nginx -t
sudo systemctl reload nginx

# Test
curl https://api-python.yourdomain.com/health
```

---

## 🔄 Step 7: Set Up CI/CD

### 7.1 Add GitHub Secrets

Go to: `https://github.com/davidx345/openride-backend/settings/secrets/actions`

Add these secrets:

```
JAVA_DROPLET_HOST=192.168.1.100 (or api-java.yourdomain.com)
PYTHON_DROPLET_HOST=192.168.1.101
DROPLET_USER=openride
DROPLET_SSH_KEY=<paste your private SSH key>
```

### 7.2 Test GitHub Actions

```bash
# On your local machine
git add .
git commit -m "Set up deployment infrastructure"
git push origin main

# Watch workflow at:
# https://github.com/davidx345/openride-backend/actions
```

The workflow will:
1. Build all Docker images
2. Push to GitHub Container Registry
3. Deploy to both droplets automatically

---

## 📊 Monitoring & Maintenance

### Daily Checks

```bash
# Check service health
docker-compose -f docker-compose.java.yml ps
docker-compose -f docker-compose.python.yml ps

# Check resource usage
htop
docker stats

# Check logs for errors
docker-compose -f docker-compose.java.yml logs --tail=100 | grep ERROR
```

### Weekly Tasks

```bash
# Update system packages
sudo apt update && sudo apt upgrade -y

# Clean up Docker
docker system prune -f

# Check disk space
df -h
```

### Monitor Resource Usage

Enable DigitalOcean Monitoring (free):
- Go to Droplet → Monitoring tab
- View CPU, Memory, Disk, Network graphs
- Set up alerts for high resource usage

### Backups

**Database Backups (Supabase)**
- Supabase automatically backs up your database
- Enable Point-in-Time Recovery (PITR) in Pro plan

**Droplet Snapshots**
```bash
# Via DigitalOcean dashboard:
# Droplet → Snapshots → Take Snapshot
# Schedule weekly snapshots (costs $1-2/month per droplet)
```

---

## 🐛 Troubleshooting

### Service Won't Start

```bash
# Check logs
docker-compose -f docker-compose.java.yml logs [service-name]

# Common issues:
# 1. Database connection error
#    → Check DB_HOST, DB_PASSWORD in .env
#    → Verify Supabase is accessible: telnet db.xxx.supabase.co 5432

# 2. Port already in use
#    → Check what's using the port: sudo lsof -i :8081
#    → Stop conflicting service or change port

# 3. Out of memory
#    → Check memory: free -h
#    → Restart services: docker-compose restart
#    → Consider upgrading droplet
```

### Can't Connect to Service

```bash
# 1. Check service is running
docker-compose ps

# 2. Check firewall
sudo ufw status
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# 3. Check Nginx
sudo nginx -t
sudo systemctl status nginx
sudo tail -f /var/log/nginx/error.log

# 4. Test internal connectivity
curl http://localhost:8081/actuator/health
```

### Database Connection Issues

```bash
# Test database connection from droplet
psql "postgresql://postgres:password@db.xxx.supabase.co:5432/postgres"

# If connection fails:
# 1. Check Supabase dashboard → Settings → Database → Connection Pooling
# 2. Verify password is correct
# 3. Check if IP is whitelisted (Supabase allows all by default)
```

### High Memory Usage

```bash
# Check memory per service
docker stats

# If a Java service is using too much:
# 1. Edit docker-compose file, reduce mem_limit
# 2. Tune JVM: Add to environment:
#    JAVA_OPTS="-Xmx256m -Xms128m"
# 3. Restart: docker-compose restart [service-name]
```

### Deployment Failed

```bash
# Check GitHub Actions logs for errors

# Common fixes:
# 1. Verify secrets are set correctly
# 2. Check SSH key has no passphrase
# 3. Ensure user has docker permissions: groups openride
# 4. Manual deploy: ssh and run deploy script
```

---

## 🎯 Quick Reference

### Common Commands

```bash
# View all services
docker-compose -f docker-compose.java.yml ps

# Restart a service
docker-compose -f docker-compose.java.yml restart auth-service

# View logs
docker-compose -f docker-compose.java.yml logs -f --tail=100 auth-service

# Stop all services
docker-compose -f docker-compose.java.yml down

# Update and restart
git pull origin main
./scripts/deploy-java.sh

# Check resource usage
docker stats
htop
df -h
```

### Service Endpoints

**Java Services (port 443 via Nginx):**
- Auth: `https://api-java.yourdomain.com/auth/`
- Users: `https://api-java.yourdomain.com/users/`
- Bookings: `https://api-java.yourdomain.com/bookings/`
- Payments: `https://api-java.yourdomain.com/payments/`

**Python Services (port 443 via Nginx):**
- Analytics: `https://api-python.yourdomain.com/analytics/`
- Drivers: `https://api-python.yourdomain.com/drivers/`
- Fleet: `https://api-python.yourdomain.com/fleet/`

---

## 📞 Support

- GitHub Issues: https://github.com/davidx345/openride-backend/issues
- Documentation: See `/docs` folder
- API Reference: See `/docs/api` folder

---

**🎉 Congratulations! Your OpenRIDE backend is now deployed and running on DigitalOcean!**
