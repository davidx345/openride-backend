-- ========================================
-- Flyway Checksum Repair Script
-- ========================================
-- This script repairs Flyway migration checksum mismatches
-- Run this on your Supabase database

-- OPTION 1: Delete the schema history and let Flyway rebuild it
-- WARNING: Only use if you're sure all migrations have been applied

-- For auth-service
DELETE FROM flyway_schema_history WHERE version = '1' AND script = 'V1__Create_otp_requests_table.sql';

-- For payments-service  
DELETE FROM flyway_schema_history WHERE version IN ('1', '2') AND script IN ('V1__create_payments_tables.sql', 'V2__update_reconciliation_schema.sql');

-- For ticketing-service
DELETE FROM flyway_schema_history WHERE version = '1' AND script = 'V1__initial_schema.sql';

-- OPTION 2: Update checksums directly (if you know the new checksum values)
-- You'll need to calculate the actual checksums - this is just an example
-- UPDATE flyway_schema_history SET checksum = NEW_CHECKSUM WHERE version = '1' AND script = 'V1__Create_otp_requests_table.sql';

-- ========================================
-- After running this, restart your services
-- Flyway will re-apply migrations with correct checksums
-- ========================================
