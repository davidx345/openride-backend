# GitHub Actions Deployment Setup

## Required GitHub Secrets

Go to your repository → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

Add these 3 secrets:

### 1. `JAVA_DROPLET_HOST`
```
161.35.70.27
```
(Your Java droplet IP from openride-java.solivolt.live)

### 2. `PYTHON_DROPLET_HOST`
```
178.128.204.243
```
(Your Python droplet IP from openride-python.solivolt.live)

### 3. `DROPLET_SSH_KEY`
Copy your entire private SSH key:
```bash
# On Windows
cat C:\Users\USER\.ssh\id_ed25519
```

Copy the output (including `-----BEGIN OPENSSH PRIVATE KEY-----` and `-----END OPENSSH PRIVATE KEY-----`)

### 4. `DROPLET_USER`
```
root
```

## How the Workflow Works

### Smart Change Detection:
1. **Push to main** → Workflow detects which services changed
2. **Only changed services** are rebuilt and redeployed
3. **Docker cache** makes rebuilds fast (30s-2min vs 5-10min)

### Examples:

**Scenario 1: Edit auth-service only**
```
✅ Detects: services/java/auth-service/
✅ SSHs to Java droplet
✅ Builds: auth-service only
✅ Restarts: auth-service only
⏭️  Skips: Python droplet (no changes)
```

**Scenario 2: Edit shared Java code**
```
✅ Detects: shared/java-commons/
✅ Rebuilds: ALL 7 Java services
⏭️  Skips: Python services
```

**Scenario 3: Edit matchmaking + driver services**
```
✅ Detects: services/python/matchmaking-service/ and driver-service/
✅ Rebuilds: matchmaking-service + driver-service only
✅ Other 4 Python services: Keep running
```

## Manual Trigger

To redeploy everything:
1. Go to **Actions** tab
2. Click **Smart Deploy to DigitalOcean Droplets**
3. Click **Run workflow** → **Run workflow**

## Testing the Workflow

1. Make a small change to any service
2. Commit and push:
   ```bash
   git add .
   git commit -m "test: trigger deployment"
   git push origin main
   ```
3. Go to **Actions** tab to watch deployment
4. Check droplet logs:
   ```bash
   ssh root@161.35.70.27  # or Python droplet
   cd ~/openride-backend/deployment
   docker-compose -f docker-compose.java.yml logs -f <service-name>
   ```

## Build Cache Benefits

With BuildKit caching enabled:
- **First build**: 5-10 minutes (downloads dependencies)
- **Subsequent builds**: 30 seconds - 2 minutes (uses cache)
- **No code changes**: Instant (Docker detects no rebuild needed)

## Troubleshooting

### Deployment failed?
1. Check Actions logs for errors
2. SSH to droplet and check:
   ```bash
   cd ~/openride-backend/deployment
   docker-compose -f docker-compose.java.yml ps
   docker-compose -f docker-compose.java.yml logs <service-name>
   ```

### Service not updating?
Force rebuild:
```bash
cd ~/openride-backend/deployment
docker-compose -f docker-compose.java.yml build --no-cache <service-name>
docker-compose -f docker-compose.java.yml up -d <service-name>
```

### Clear Docker cache (if disk full):
```bash
docker system prune -a --volumes
```
