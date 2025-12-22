# Java Services - Issues Fixed

## Root Cause Analysis

### 1. **Flyway Migration Checksum Mismatches** (Auth, Payments, Ticketing Services)
**Problem:** Migration files were modified after being applied to the database. Flyway validates checksums to ensure migration integrity.

**Error:**
```
Migration checksum mismatch for migration version 1
-> Applied to database : 1716449606
-> Resolved locally    : 1331456518
```

**Solution:** Delete the old Flyway history entries to allow re-baselining
```sql
-- Run in your Supabase database
DELETE FROM flyway_schema_history WHERE version = '1' AND script = 'V1__Create_otp_requests_table.sql';
DELETE FROM flyway_schema_history WHERE version IN ('1', '2') AND script IN ('V1__create_payments_tables.sql', 'V2__update_reconciliation_schema.sql');
DELETE FROM flyway_schema_history WHERE version = '1' AND script = 'V1__initial_schema.sql';
```

Or use Flyway repair (preferred):
```bash
# From deployment folder on your server
docker exec -it auth-service ./flyway repair
docker exec -it payments-service ./flyway repair
docker exec -it ticketing-service ./flyway repair
```

---

### 2. **Missing Tables** (Admin Service)
**Problem:** Admin service had `flyway.enabled=false` but expected `audit_logs`, `disputes`, and `user_suspensions` tables.

**Error:**
```
Schema-validation: missing table [audit_logs]
```

**Fixed:**
- ✅ Created migration file: `services/java/admin-service/src/main/resources/db/migration/V1__create_admin_tables.sql`
- ✅ Enabled Flyway in `application.yml`
- ✅ Changed port from 8088 to 8087 to match docker-compose

---

### 3. **Missing Database Column** (User Service)
**Problem:** `driver_profiles` table missing `cancellation_rate` column that the entity expects.

**Error:**
```
Schema-validation: missing column [cancellation_rate] in table [driver_profiles]
```

**Fixed:**
- ✅ Created migration file: `services/java/user-service/src/main/resources/db/migration/V3__Add_cancellation_rate_to_driver_profiles.sql`

---

### 4. **Redis Configuration Issue** (Booking Service)
**Problem:** Booking service configuration used `${REDIS_HOST}`, `${REDIS_PORT}`, `${REDIS_PASSWORD}` but only `REDIS_URL` was provided.

**Error:**
```
Could not resolve placeholder 'spring.redis.host' in value "${spring.redis.host}"
```

**Fixed:**
- ✅ Updated Redisson configuration to use `REDIS_URL` directly instead of separate host/port/password
- ✅ Redisson now parses the full Redis URL: `redis://default:password@host:port/db`

---

### 5. **Missing Tables** (Payouts Service)
**Problem:** Payouts service expected `bank_accounts` table.

**Error:**
```
Schema-validation: missing table [bank_accounts]
```

**Status:** Migration file exists (`V1__create_payouts_schema.sql`) but may not have run. Will run on restart.

---

### 6. **Port Conflicts Fixed**
- Admin service: 8088 → 8087 ✅
- Payouts service: 8087 → 8085 ✅

---

## Files Modified

### Configuration Files
1. `services/java/admin-service/src/main/resources/application.yml`
   - Enabled Flyway
   - Fixed port to 8087

2. `services/java/booking-service/src/main/resources/application.yml`
   - Fixed Redisson to use REDIS_URL instead of separate params

3. `services/java/payouts-service/src/main/resources/application.yml`
   - Fixed port to 8085

### New Migration Files Created
1. `services/java/admin-service/src/main/resources/db/migration/V1__create_admin_tables.sql`
   - Creates audit_logs table
   - Creates disputes table
   - Creates user_suspensions table

2. `services/java/user-service/src/main/resources/db/migration/V3__Add_cancellation_rate_to_driver_profiles.sql`
   - Adds cancellation_rate column to driver_profiles

---

## Next Steps

### 1. Fix Flyway Checksums in Database
Run this SQL in your Supabase database:

```sql
-- Delete old checksum entries (they'll be recreated with correct values)
DELETE FROM flyway_schema_history WHERE version = '1' AND script = 'V1__Create_otp_requests_table.sql';
DELETE FROM flyway_schema_history WHERE version IN ('1', '2') AND script IN ('V1__create_payments_tables.sql', 'V2__update_reconciliation_schema.sql');
DELETE FROM flyway_schema_history WHERE version = '1' AND script = 'V1__initial_schema.sql';
```

### 2. Rebuild Docker Images
From your local machine:
```bash
cd deployment
docker-compose -f docker-compose.java.yml build
```

### 3. Deploy to Server
```bash
# Copy to server
scp docker-compose.java.yml root@your-server:/root/openride-backend/deployment/

# SSH to server
ssh root@your-server

cd /root/openride-backend/deployment

# Stop all services
docker-compose -f docker-compose.java.yml down

# Remove old images (optional but recommended)
docker-compose -f docker-compose.java.yml rm -f

# Pull/rebuild and start
docker-compose -f docker-compose.java.yml up -d

# Watch logs
docker-compose -f docker-compose.java.yml logs -f
```

### 4. Verify Each Service
```bash
# Check auth-service
curl http://localhost:8081/actuator/health

# Check user-service
curl http://localhost:8082/actuator/health

# Check booking-service
curl http://localhost:8083/api/actuator/health

# Check payments-service
curl http://localhost:8084/actuator/health

# Check payouts-service
curl http://localhost:8085/actuator/health

# Check ticketing-service
curl http://localhost:8086/actuator/health

# Check admin-service
curl http://localhost:8087/actuator/health
```

---

## Summary of All Issues

| Service | Issue | Status |
|---------|-------|--------|
| Admin | Missing tables (audit_logs, disputes, user_suspensions) | ✅ Fixed - migration created |
| Admin | Flyway disabled | ✅ Fixed - enabled in config |
| Admin | Port 8088 vs 8087 | ✅ Fixed - changed to 8087 |
| Auth | Flyway checksum mismatch | ⚠️ Needs DB repair |
| Booking | Missing Redis host/port/password | ✅ Fixed - using REDIS_URL |
| Payments | Flyway checksum mismatch | ⚠️ Needs DB repair |
| Payouts | Port 8087 vs 8085 | ✅ Fixed - changed to 8085 |
| Ticketing | Flyway checksum mismatch | ⚠️ Needs DB repair |
| User | Missing cancellation_rate column | ✅ Fixed - migration created |

---

## Database Changes Required

Run this SQL in Supabase before restarting services:

```sql
-- Fix Flyway checksum mismatches
DELETE FROM flyway_schema_history WHERE version = '1' AND script = 'V1__Create_otp_requests_table.sql';
DELETE FROM flyway_schema_history WHERE version IN ('1', '2') AND script IN ('V1__create_payments_tables.sql', 'V2__update_reconciliation_schema.sql');
DELETE FROM flyway_schema_history WHERE version = '1' AND script = 'V1__initial_schema.sql';
```

After running this SQL and restarting the services, Flyway will:
1. Detect that the tables already exist
2. Create new baseline entries with correct checksums
3. Run any new migrations (V3 for user-service, V1 for admin-service)

All services should start successfully!
