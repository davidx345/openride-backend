-- ========================================
-- Flyway Checksum Repair Script
-- ========================================
-- Run this on your Supabase database to fix migration checksum mismatches
-- Connect via: https://supabase.com/dashboard -> SQL Editor

-- Delete problematic flyway history entries
-- They will be recreated with correct checksums when services restart

-- For auth-service (version 1 mismatch)
DELETE FROM flyway_schema_history 
WHERE version = '1' 
AND script = 'V1__Create_otp_requests_table.sql';

-- For payments-service (versions 1 and 2 mismatch)
DELETE FROM flyway_schema_history 
WHERE version IN ('1', '2') 
AND script IN ('V1__create_payments_tables.sql', 'V2__update_reconciliation_schema.sql');

-- For ticketing-service (version 1 mismatch)
DELETE FROM flyway_schema_history 
WHERE version = '1' 
AND script = 'V1__initial_schema.sql';

-- Verify what will be deleted (run this FIRST to check):
-- SELECT * FROM flyway_schema_history 
-- WHERE (version = '1' AND script = 'V1__Create_otp_requests_table.sql')
--    OR (version IN ('1', '2') AND script IN ('V1__create_payments_tables.sql', 'V2__update_reconciliation_schema.sql'))
--    OR (version = '1' AND script = 'V1__initial_schema.sql');

-- ========================================
-- After running this SQL:
-- 1. Rebuild your Docker images (code has been fixed)
-- 2. Restart your services
-- 3. Flyway will re-baseline with correct checksums
-- ========================================
