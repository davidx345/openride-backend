# Complete Java Services Fix - Deployment Guide

## Problems Fixed ✅

### 1. **Booking Service** - Redis Configuration Issue
**Problem:** Code was using `@Value("${spring.redis.host}")` which doesn't exist
```
IllegalArgumentException: Could not resolve placeholder 'spring.redis.host'
```

**Fix Applied:**
- ✅ Modified `RateLimitingConfig.java` to use `spring.redis.url` directly
- ✅ Modified `RedissonConfig.java` to parse full REDIS_URL instead of separate host/port/password
- Now works with: `redis://default:password@host:port/db`

---

### 2. **Auth, Payments, Ticketing Services** - Flyway Checksum Mismatch
**Problem:** Migration files were modified after being applied to database
```
FlywayValidateException: Migration checksum mismatch for migration version 1
-> Applied to database : 1716449606
-> Resolved locally    : 1331456518
```

**Fix Required:** Run SQL in Supabase (see Step 1 below)

---

### 3. **User Service** - Missing Database Column
**Problem:** Entity expects `cancelled_trips` but only `cancellation_rate` was being created
```
SchemaManagementException: missing column [cancelled_trips] in table [driver_profiles]
```

**Fix Applied:**
- ✅ Updated migration `V3__Add_cancellation_rate_to_driver_profiles.sql`
- Now adds BOTH `cancellation_rate` AND `cancelled_trips` columns

---

### 4. **All Services** - Database Connection Pool Exhaustion
**Problem:** Supabase free tier limits connections, too many services requesting too many connections
```
PSQLException: FATAL: MaxClientsInSessionMode: max clients reached
```

**Fix Applied:**
- ✅ Reduced `maximum-pool-size` from 10-50 to 3-5 per service
- ✅ Reduced `minimum-idle` from 5-10 to 1-2 per service
- Total connections: ~35 max (well under Supabase limits)

---

## Deployment Steps

### Step 1: Fix Flyway Checksums in Supabase Database

**CRITICAL:** Do this FIRST before rebuilding/redeploying!

1. Go to Supabase Dashboard: https://supabase.com/dashboard
2. Select your project
3. Go to **SQL Editor**
4. Run this SQL:

```sql
-- Verify what will be deleted (optional check)
SELECT * FROM flyway_schema_history 
WHERE (version = '1' AND script = 'V1__Create_otp_requests_table.sql')
   OR (version IN ('1', '2') AND script IN ('V1__create_payments_tables.sql', 'V2__update_reconciliation_schema.sql'))
   OR (version = '1' AND script = 'V1__initial_schema.sql');

-- Delete problematic entries (actual fix)
DELETE FROM flyway_schema_history 
WHERE version = '1' 
AND script = 'V1__Create_otp_requests_table.sql';

DELETE FROM flyway_schema_history 
WHERE version IN ('1', '2') 
AND script IN ('V1__create_payments_tables.sql', 'V2__update_reconciliation_schema.sql');

DELETE FROM flyway_schema_history 
WHERE version = '1' 
AND script = 'V1__initial_schema.sql';
```

---

### Step 2: Rebuild Docker Images (Local)

From your Windows machine:

```bash
cd C:\Users\USER\Documents\projects\openride-backend

# Build all Java services
docker-compose -f deployment/docker-compose.java.yml build

# This will take 10-15 minutes - Maven will download dependencies
```

---

### Step 3: Deploy to Server

#### Option A: Push to GitHub and Pull on Server

```bash
# On Windows
git add .
git commit -m "Fix all Java services: Redis config, Flyway, DB columns, connection pools"
git push origin main

# On Server (SSH)
ssh -i "C:\Users\USER\.ssh\id_ed25519" root@161.35.70.27

cd /root/openride-backend
git pull origin main

# Rebuild on server
cd deployment
docker-compose -f docker-compose.java.yml build
```

#### Option B: Copy Files Directly

```bash
# From Windows PowerShell
scp -i "C:\Users\USER\.ssh\id_ed25519" -r services/java root@161.35.70.27:/root/openride-backend/services/

# Then rebuild on server
ssh -i "C:\Users\USER\.ssh\id_ed25519" root@161.35.70.27
cd /root/openride-backend/deployment
docker-compose -f docker-compose.java.yml build
```

---

### Step 4: Stop Old Services

```bash
# On server
cd /root/openride-backend/deployment
docker-compose -f docker-compose.java.yml down

# Optional: Remove old images to save space
docker system prune -f
```

---

### Step 5: Start Fixed Services

```bash
cd /root/openride-backend/deployment
docker-compose -f docker-compose.java.yml up -d

# Watch logs for all services
docker-compose -f docker-compose.java.yml logs -f
```

---

### Step 6: Verify Each Service

Wait 2-3 minutes for services to fully start, then check:

```bash
# Auth Service (port 8081)
curl http://localhost:8081/actuator/health
# Expected: {"status":"UP"}

# User Service (port 8082)
curl http://localhost:8082/actuator/health

# Booking Service (port 8083)
curl http://localhost:8083/api/actuator/health

# Payments Service (port 8084)
curl http://localhost:8084/actuator/health

# Payouts Service (port 8085)
curl http://localhost:8085/actuator/health

# Ticketing Service (port 8086)
curl http://localhost:8086/actuator/health

# Admin Service (port 8087)
curl http://localhost:8087/actuator/health
```

---

## Expected Startup Logs (Success Indicators)

### ✅ Auth Service
```
Flyway: Successfully validated 1 migration
Current version of schema "public": 1
Schema "public" is up to date
Started AuthServiceApplication in X seconds
```

### ✅ User Service
```
Flyway: Migrating schema "public" to version "3 - Add cancellation rate to driver profiles"
Successfully applied 1 migration to schema "public", now at version v3
Started UserServiceApplication in X seconds
```

### ✅ Booking Service
```
# No more "Could not resolve placeholder 'spring.redis.host'" error
Started BookingServiceApplication in X seconds
```

### ✅ Payments Service
```
Flyway: Successfully validated 2 migrations
Schema "public" is up to date
Started PaymentsServiceApplication in X seconds
```

### ✅ Ticketing Service
```
Flyway: Successfully validated 1 migration
Schema "public" is up to date
Started TicketingServiceApplication in X seconds
```

---

## Troubleshooting

### If Auth/Payments/Ticketing Still Show Checksum Error

You forgot Step 1! Go back and run the SQL in Supabase.

### If Booking Service Still Shows Redis Error

Check logs:
```bash
docker logs booking-service 2>&1 | grep -i redis
```

Verify REDIS_URL in `.env`:
```bash
cat /root/openride-backend/deployment/.env | grep REDIS
```

Should be:
```
REDIS_URL=redis://default:yjgLl5SUWYxcRhgzWJOVpMkkhGYDpAzO@redis-16685.c85.us-east-1-2.ec2.redns.redis-cloud.com:16685/0
```

### If Services Keep Restarting with "max clients reached"

Check how many connections are active:
```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
```

If all 7 services are trying to connect at once during startup, Supabase might still hit limits temporarily. This is normal - they'll retry and eventually all connect.

### Check Individual Service Status

```bash
# See which services are running
docker-compose -f docker-compose.java.yml ps

# Check specific service logs
docker logs auth-service --tail 50
docker logs booking-service --tail 50
docker logs user-service --tail 50

# Follow logs in real-time
docker logs -f booking-service
```

---

## What Changed (Summary)

| File | Change |
|------|--------|
| `booking-service/config/RateLimitingConfig.java` | Use `spring.redis.url` instead of separate host/port |
| `booking-service/config/RedissonConfig.java` | Parse full REDIS_URL instead of separate params |
| `user-service/V3__Add_cancellation_rate.sql` | Add BOTH `cancellation_rate` AND `cancelled_trips` |
| All `application.yml` files | Reduce `maximum-pool-size` to 3-5 per service |
| Database (via SQL script) | Delete problematic Flyway history entries |

---

## Post-Deployment Health Check

All services should show:
```json
{"status":"UP"}
```

If any service shows `{"status":"DOWN"}`, check its logs:
```bash
docker logs <service-name> --tail 100
```

---

## Connection Pool Math

With new settings:
- Auth: 5 connections max
- User: 5 connections max
- Booking: 5 connections max
- Payments: 5 connections max
- Payouts: 5 connections max
- Ticketing: 3 connections max
- Admin: 3 connections max

**Total: ~31-35 connections maximum**

Supabase free tier typically allows 50-100 connections in session mode, so we're well within limits.

---

## Success Criteria

✅ All 7 services show "UP" in health checks
✅ No Flyway checksum errors in logs
✅ No Redis configuration errors
✅ No "max clients reached" errors
✅ User service migration V3 applied successfully
✅ All services stay running (no restart loops)

---

## If You Need to Rollback

```bash
cd /root/openride-backend/deployment
docker-compose -f docker-compose.java.yml down
git checkout HEAD~1  # Go back to previous commit
docker-compose -f docker-compose.java.yml up -d
```

But you shouldn't need to - these fixes address the root causes!
