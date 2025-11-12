-- OpenRide Database Initialization Script
-- This script sets up the database with PostGIS extension and creates shared tables

-- Enable PostGIS extension for geospatial queries
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Verify PostGIS installation
SELECT PostGIS_version();

-- Create audit_logs table for system-wide audit trail
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor_id UUID,
    actor_type VARCHAR(50),
    changes JSONB,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);

-- Create enum types (will be used by services)
CREATE TYPE user_role AS ENUM ('RIDER', 'DRIVER', 'ADMIN');
CREATE TYPE kyc_status AS ENUM ('NONE', 'PENDING', 'VERIFIED', 'REJECTED');

-- Note: Individual service tables will be created by Flyway/Alembic migrations
-- This script only sets up the database foundation

-- Grant privileges to openride_user
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO openride_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO openride_user;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO openride_user;

-- Success message
DO $$
BEGIN
    RAISE NOTICE 'OpenRide database initialized successfully with PostGIS extension';
END $$;
