-- Migration: Enhance audit_logs table for comprehensive admin tracking
-- Phase 11: Admin Dashboard & Support APIs

-- Drop the existing basic audit_logs table if it exists
DROP TABLE IF EXISTS audit_logs CASCADE;

-- Recreate with enhanced schema
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor_id UUID,
    actor_type VARCHAR(50),
    actor_role VARCHAR(20),
    changes JSONB,
    ip_address INET,
    user_agent TEXT,
    request_id VARCHAR(100),
    service_name VARCHAR(100),
    endpoint VARCHAR(255),
    http_method VARCHAR(10),
    status_code INTEGER,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Partitioning by month for performance (optional for future)
-- CREATE TABLE audit_logs_2024_11 PARTITION OF audit_logs FOR VALUES FROM ('2024-11-01') TO ('2024-12-01');

-- Indexes for efficient admin queries
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_entity_type ON audit_logs(entity_type);
CREATE INDEX idx_audit_logs_service ON audit_logs(service_name);
CREATE INDEX idx_audit_logs_request_id ON audit_logs(request_id);

-- GIN index for JSONB changes field for advanced filtering
CREATE INDEX idx_audit_logs_changes ON audit_logs USING GIN (changes);

-- Function to log admin actions
CREATE OR REPLACE FUNCTION log_admin_action(
    p_entity_type VARCHAR,
    p_entity_id VARCHAR,
    p_action VARCHAR,
    p_actor_id UUID,
    p_changes JSONB DEFAULT NULL,
    p_ip_address INET DEFAULT NULL,
    p_service_name VARCHAR DEFAULT NULL
)
RETURNS UUID AS $$
DECLARE
    v_audit_id UUID;
BEGIN
    INSERT INTO audit_logs (
        entity_type,
        entity_id,
        action,
        actor_id,
        actor_type,
        changes,
        ip_address,
        service_name
    ) VALUES (
        p_entity_type,
        p_entity_id,
        p_action,
        p_actor_id,
        'ADMIN',
        p_changes,
        p_ip_address,
        p_service_name
    ) RETURNING id INTO v_audit_id;
    
    RETURN v_audit_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON TABLE audit_logs IS 'Comprehensive audit trail for all system operations, especially admin actions';
COMMENT ON FUNCTION log_admin_action IS 'Helper function to log admin actions with standardized format';
