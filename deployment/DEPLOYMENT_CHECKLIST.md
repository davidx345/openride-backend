# ✅ OpenRIDE DigitalOcean Deployment Checklist

Use this checklist to track your deployment progress.

---

## 🎯 Pre-Deployment (Before You Start)

- [ ] DigitalOcean account created
- [ ] DigitalOcean $200 credit applied
- [ ] Supabase account created (free tier)
- [ ] Domain name purchased (optional but recommended)
- [ ] SSH key pair generated (`ssh-keygen -t ed25519`)
- [ ] GitHub account with repository access
- [ ] Git installed locally
- [ ] 60-90 minutes available for initial setup

---

## 📋 Phase 1: Database Setup (10 minutes)

- [ ] Supabase project created
- [ ] Database connection details saved:
  - [ ] DB_HOST
  - [ ] DB_PORT
  - [ ] DB_NAME
  - [ ] DB_USER
  - [ ] DB_PASSWORD
- [ ] Database migrations executed
- [ ] Test connection from local machine: `psql "postgresql://..."`

---

## 🖥️ Phase 2: Droplet Creation (15 minutes)

### Java Droplet
- [ ] Ubuntu 22.04 LTS selected
- [ ] 2GB RAM / 1 vCPU / 50GB SSD plan chosen
- [ ] Datacenter region selected (closest to users)
- [ ] SSH key added
- [ ] Hostname set: `openride-java`
- [ ] Tags added: `java, backend, production`
- [ ] Droplet created
- [ ] IP address noted: `_________________`
- [ ] Can SSH into droplet: `ssh root@<ip>`

### Python Droplet
- [ ] Ubuntu 22.04 LTS selected
- [ ] 2GB RAM / 1 vCPU / 50GB SSD plan chosen
- [ ] Same datacenter region as Java droplet
- [ ] SSH key added
- [ ] Hostname set: `openride-python`
- [ ] Tags added: `python, backend, production`
- [ ] Droplet created
- [ ] IP address noted: `_________________`
- [ ] Can SSH into droplet: `ssh root@<ip>`

---

## 🔧 Phase 3: Droplet Initialization (25 minutes)

### Java Droplet Setup
- [ ] SSH'd into Java droplet
- [ ] Non-root user created: `openride`
- [ ] User added to sudo group
- [ ] SSH keys copied to new user
- [ ] Reconnected as `openride` user
- [ ] Repository cloned
- [ ] Setup script made executable: `chmod +x setup-java-droplet.sh`
- [ ] Setup script executed successfully
- [ ] Docker installed and running: `docker --version`
- [ ] Docker Compose installed: `docker-compose --version`
- [ ] Nginx installed: `nginx -v`
- [ ] Certbot installed: `certbot --version`
- [ ] Firewall configured: `sudo ufw status`
- [ ] Logged out and back in (Docker group changes)

### Python Droplet Setup
- [ ] SSH'd into Python droplet
- [ ] Non-root user created: `openride`
- [ ] User added to sudo group
- [ ] SSH keys copied to new user
- [ ] Reconnected as `openride` user
- [ ] Repository cloned
- [ ] Setup script made executable: `chmod +x setup-python-droplet.sh`
- [ ] Setup script executed successfully
- [ ] Docker installed and running: `docker --version`
- [ ] Docker Compose installed: `docker-compose --version`
- [ ] Nginx installed: `nginx -v`
- [ ] Certbot installed: `certbot --version`
- [ ] Firewall configured: `sudo ufw status`
- [ ] Logged out and back in (Docker group changes)

---

## 🔐 Phase 4: Configuration (20 minutes)

### Environment Variables - Java Droplet
- [ ] Navigated to `~/openride-backend/deployment`
- [ ] Copied `.env.example` to `.env`
- [ ] Filled in database credentials (Supabase)
- [ ] Generated JWT secret: `openssl rand -hex 32`
- [ ] Added Twilio credentials (if available)
- [ ] Added Stripe credentials (if available)
- [ ] Added SendGrid API key (if available)
- [ ] Added Firebase credentials (if available)
- [ ] Redis configuration added
- [ ] Environment set to `production`
- [ ] File saved and verified: `cat .env | grep DB_HOST`

### Environment Variables - Python Droplet
- [ ] Navigated to `~/openride-backend/deployment`
- [ ] Copied `.env.example` to `.env`
- [ ] Copied same values from Java droplet
- [ ] Verified all required fields filled
- [ ] File saved

---

## 🚀 Phase 5: Service Deployment (20 minutes)

### Deploy Java Services
- [ ] On Java droplet: `cd ~/openride-backend/deployment`
- [ ] Deploy script executed: `./scripts/deploy-java.sh`
- [ ] All 7 services started successfully
- [ ] Services showing as healthy: `docker-compose -f docker-compose.java.yml ps`
- [ ] No errors in logs: `docker-compose -f docker-compose.java.yml logs --tail=50`
- [ ] Health checks passing:
  - [ ] auth-service: `curl http://localhost:8081/actuator/health`
  - [ ] user-service: `curl http://localhost:8082/actuator/health`
  - [ ] booking-service: `curl http://localhost:8083/api/actuator/health`
  - [ ] payments-service: `curl http://localhost:8084/actuator/health`
  - [ ] payouts-service: `curl http://localhost:8085/actuator/health`
  - [ ] ticketing-service: `curl http://localhost:8086/actuator/health`
  - [ ] admin-service: `curl http://localhost:8087/actuator/health`

### Deploy Python Services
- [ ] On Python droplet: `cd ~/openride-backend/deployment`
- [ ] Deploy script executed: `./scripts/deploy-python.sh`
- [ ] All 6 services started successfully
- [ ] Services showing as healthy: `docker-compose -f docker-compose.python.yml ps`
- [ ] No errors in logs: `docker-compose -f docker-compose.python.yml logs --tail=50`
- [ ] Health checks passing:
  - [ ] analytics-service: `curl http://localhost:8097/health`
  - [ ] driver-service: `curl http://localhost:8090/health`
  - [ ] fleet-service: `curl http://localhost:8096/health`
  - [ ] matchmaking-service: `curl http://localhost:8091/health`
  - [ ] notification-service: `curl http://localhost:8095/health`
  - [ ] search-service: `curl http://localhost:8092/health`

---

## 🌐 Phase 6: Domain & SSL Setup (30 minutes, OPTIONAL)

### DNS Configuration
- [ ] Domain purchased/available
- [ ] A record created: `api-java.yourdomain.com` → Java droplet IP
- [ ] A record created: `api-python.yourdomain.com` → Python droplet IP
- [ ] DNS propagation verified: `ping api-java.yourdomain.com`
- [ ] Both domains resolving to correct IPs

### Java Droplet - Nginx & SSL
- [ ] Nginx config copied: `sudo cp ~/openride-backend/deployment/nginx/java-droplet.conf /etc/nginx/sites-available/openride-java`
- [ ] Config edited with actual domain: `sudo nano /etc/nginx/sites-available/openride-java`
- [ ] Symlink created: `sudo ln -s /etc/nginx/sites-available/openride-java /etc/nginx/sites-enabled/`
- [ ] Default site removed: `sudo rm /etc/nginx/sites-enabled/default`
- [ ] Config tested: `sudo nginx -t`
- [ ] Nginx reloaded: `sudo systemctl reload nginx`
- [ ] SSL certificate obtained: `./scripts/setup-ssl.sh api-java.yourdomain.com your@email.com`
- [ ] HTTPS working: `curl https://api-java.yourdomain.com/health`

### Python Droplet - Nginx & SSL
- [ ] Nginx config copied: `sudo cp ~/openride-backend/deployment/nginx/python-droplet.conf /etc/nginx/sites-available/openride-python`
- [ ] Config edited with actual domain: `sudo nano /etc/nginx/sites-available/openride-python`
- [ ] Symlink created: `sudo ln -s /etc/nginx/sites-available/openride-python /etc/nginx/sites-enabled/`
- [ ] Default site removed: `sudo rm /etc/nginx/sites-enabled/default`
- [ ] Config tested: `sudo nginx -t`
- [ ] Nginx reloaded: `sudo systemctl reload nginx`
- [ ] SSL certificate obtained: `./scripts/setup-ssl.sh api-python.yourdomain.com your@email.com`
- [ ] HTTPS working: `curl https://api-python.yourdomain.com/health`

---

## 🔄 Phase 7: CI/CD Setup (15 minutes, OPTIONAL)

### GitHub Secrets Configuration
- [ ] Navigated to: `https://github.com/davidx345/openride-backend/settings/secrets/actions`
- [ ] Added secret: `JAVA_DROPLET_HOST` (IP or domain)
- [ ] Added secret: `PYTHON_DROPLET_HOST` (IP or domain)
- [ ] Added secret: `DROPLET_USER` (e.g., `openride`)
- [ ] Added secret: `DROPLET_SSH_KEY` (private key content)

### Workflow Testing
- [ ] Made a small change to code
- [ ] Committed and pushed to `main` branch
- [ ] GitHub Actions workflow triggered
- [ ] All jobs (build-java, build-python, deploy-java, deploy-python) succeeded
- [ ] Images pushed to GitHub Container Registry
- [ ] Services redeployed on both droplets
- [ ] Verified services still healthy after auto-deployment

---

## 📊 Phase 8: Monitoring Setup (10 minutes)

### DigitalOcean Monitoring
- [ ] Java droplet: Monitoring enabled in dashboard
- [ ] Python droplet: Monitoring enabled in dashboard
- [ ] Alert created: CPU > 80% for 5 minutes
- [ ] Alert created: Memory > 90% for 5 minutes
- [ ] Alert created: Disk > 85%
- [ ] Email alerts configured

### Resource Verification
- [ ] Checked current resource usage: `htop` on both droplets
- [ ] Checked Docker stats: `docker stats`
- [ ] Checked disk space: `df -h`
- [ ] Verified logs are rotating properly

---

## 🔒 Phase 9: Security Hardening (10 minutes)

### Java Droplet
- [ ] Firewall rules verified: `sudo ufw status`
- [ ] Only ports 22, 80, 443 open
- [ ] Root login disabled (using `openride` user)
- [ ] Fail2ban installed (optional): `sudo apt install fail2ban`
- [ ] Automatic security updates enabled: `sudo dpkg-reconfigure -plow unattended-upgrades`

### Python Droplet
- [ ] Firewall rules verified: `sudo ufw status`
- [ ] Only ports 22, 80, 443 open
- [ ] Root login disabled (using `openride` user)
- [ ] Fail2ban installed (optional): `sudo apt install fail2ban`
- [ ] Automatic security updates enabled: `sudo dpkg-reconfigure -plow unattended-upgrades`

---

## 💾 Phase 10: Backup Configuration (10 minutes)

### Supabase Backups
- [ ] Supabase automatic backups verified (included in all plans)
- [ ] Point-in-Time Recovery enabled (if using Pro plan)
- [ ] Backup retention policy reviewed

### Droplet Snapshots
- [ ] Java droplet: Weekly snapshot schedule enabled
- [ ] Python droplet: Weekly snapshot schedule enabled
- [ ] Snapshot retention set (4 weeks recommended)
- [ ] First manual snapshot taken as baseline

---

## ✅ Phase 11: Final Verification (15 minutes)

### End-to-End Tests
- [ ] Auth flow works: Register user, login, get JWT
- [ ] User service: Create/read/update user profile
- [ ] Booking flow: Create booking, fetch booking
- [ ] Payment flow: Process test payment (Stripe test mode)
- [ ] Notification: Send test notification
- [ ] All services communicating with database
- [ ] No database connection errors
- [ ] Response times acceptable (< 500ms for simple queries)

### Documentation Check
- [ ] Read through `DIGITALOCEAN_DEPLOYMENT.md`
- [ ] Understand how to view logs
- [ ] Know how to restart services
- [ ] Know how to check resource usage
- [ ] Bookmarked troubleshooting section

### Handoff Preparation
- [ ] `.env` files backed up securely (NOT in Git!)
- [ ] Droplet IPs documented
- [ ] Domain names documented
- [ ] Database credentials documented (secure location)
- [ ] API keys documented (secure location)
- [ ] SSH keys backed up
- [ ] Admin access verified

---

## 🎉 Deployment Complete!

Congratulations! Your OpenRIDE backend is now deployed on DigitalOcean.

### Quick Reference

**Droplet IPs:**
- Java: `_________________`
- Python: `_________________`

**Domains:**
- Java: `api-java.yourdomain.com`
- Python: `api-python.yourdomain.com`

**Monthly Cost:** $24 (lasts 8+ months with $200 credit)

**Next Steps:**
1. Monitor resource usage for first week
2. Test all API endpoints from frontend
3. Set up log aggregation (optional)
4. Configure external monitoring (optional)
5. Plan scaling strategy for growth

### Support Resources
- [ ] Saved link to `deployment/DEPLOYMENT_GUIDE.md`
- [ ] Saved link to `deployment/IMPLEMENTATION_SUMMARY.md`
- [ ] Joined DigitalOcean community (optional)
- [ ] Saved Supabase dashboard URL

---

**Date Completed:** __________________  
**Deployed By:** __________________  
**Notes:**
