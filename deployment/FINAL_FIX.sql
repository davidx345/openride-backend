-- ========================================
-- FINAL FLYWAY REPAIR SQL - RUN THIS IN SUPABASE
-- ========================================
-- Go to: https://supabase.com/dashboard -> Your Project -> SQL Editor
-- Copy and paste ALL of this SQL and click RUN

-- Step 1: Check what will be deleted (optional preview)
SELECT 
    version, 
    script, 
    checksum as current_checksum_in_db,
    installed_on
FROM flyway_schema_history 
WHERE (version = '1' AND script = 'V1__Create_otp_requests_table.sql')
   OR (version IN ('1', '2') AND script IN ('V1__create_payments_tables.sql', 'V2__update_reconciliation_schema.sql'))
   OR (version = '1' AND script = 'V1__initial_schema.sql')
   OR (version = '3' AND script = 'V3__Add_cancellation_rate_to_driver_profiles.sql')
ORDER BY version::int, script;

-- Step 2: Delete problematic Flyway entries (THE ACTUAL FIX)

-- Auth service (V1 checksum mismatch)
DELETE FROM flyway_schema_history 
WHERE version = '1' 
AND script = 'V1__Create_otp_requests_table.sql';

-- Payments service (V1 and V2 checksum mismatch)
DELETE FROM flyway_schema_history 
WHERE version IN ('1', '2') 
AND script IN ('V1__create_payments_tables.sql', 'V2__update_reconciliation_schema.sql');

-- Ticketing service (V1 checksum mismatch)
DELETE FROM flyway_schema_history 
WHERE version = '1' 
AND script = 'V1__initial_schema.sql';

-- User service (V3 checksum mismatch)
DELETE FROM flyway_schema_history 
WHERE version = '3' 
AND script = 'V3__Add_cancellation_rate_to_driver_profiles.sql';

-- Step 3: Verify deletions
SELECT 
    'After deletion - remaining migrations:' as status,
    version, 
    script, 
    checksum,
    installed_on
FROM flyway_schema_history 
ORDER BY version::int, script;

-- ========================================
-- AFTER RUNNING THIS SQL:
-- 1. Go back to your server terminal
-- 2. Run: cd /root/openride-backend/deployment
-- 3. Run: docker-compose -f docker-compose.java.yml restart
-- 4. Wait 2-3 minutes for all services to start
-- 5. Check health: curl http://localhost:8081/actuator/health (and ports 8082-8087)
-- ========================================
