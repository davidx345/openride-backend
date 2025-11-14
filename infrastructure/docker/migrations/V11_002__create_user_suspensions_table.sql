-- Migration: Create user_suspensions table for ban/suspension management
-- Phase 11: Admin Dashboard & Support APIs

CREATE TYPE suspension_type AS ENUM ('TEMPORARY', 'PERMANENT');

CREATE TABLE user_suspensions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    suspension_type suspension_type NOT NULL,
    reason VARCHAR(500) NOT NULL,
    notes TEXT,
    start_date TIMESTAMP NOT NULL DEFAULT NOW(),
    end_date TIMESTAMP,
    suspended_by UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_suspended_by FOREIGN KEY (suspended_by) REFERENCES users(id),
    CONSTRAINT end_date_check CHECK (
        (suspension_type = 'PERMANENT' AND end_date IS NULL) OR
        (suspension_type = 'TEMPORARY' AND end_date IS NOT NULL AND end_date > start_date)
    )
);

-- Indexes for efficient queries
CREATE INDEX idx_user_suspensions_user ON user_suspensions(user_id);
CREATE INDEX idx_user_suspensions_active ON user_suspensions(is_active);
CREATE INDEX idx_user_suspensions_type ON user_suspensions(suspension_type);
CREATE INDEX idx_user_suspensions_start_date ON user_suspensions(start_date DESC);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_user_suspensions_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_user_suspensions_updated_at
    BEFORE UPDATE ON user_suspensions
    FOR EACH ROW
    EXECUTE FUNCTION update_user_suspensions_updated_at();

-- Function to check if user is currently suspended
CREATE OR REPLACE FUNCTION is_user_suspended(p_user_id UUID)
RETURNS BOOLEAN AS $$
DECLARE
    is_suspended BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM user_suspensions
        WHERE user_id = p_user_id
        AND is_active = true
        AND start_date <= NOW()
        AND (
            suspension_type = 'PERMANENT' OR
            (suspension_type = 'TEMPORARY' AND end_date > NOW())
        )
    ) INTO is_suspended;
    
    RETURN is_suspended;
END;
$$ LANGUAGE plpgsql;

COMMENT ON TABLE user_suspensions IS 'Stores user suspension and ban records for admin enforcement';
COMMENT ON FUNCTION is_user_suspended IS 'Checks if a user is currently under active suspension';
