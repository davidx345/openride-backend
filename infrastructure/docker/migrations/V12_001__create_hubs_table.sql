-- Migration: V12_001__create_hubs_table.sql
-- Description: Create hubs table for transportation hub infrastructure
-- Author: OpenRide Platform Team
-- Date: 2025-11-14
-- Phase: 1.1 - Create Hubs Infrastructure

-- Create hubs table
CREATE TABLE hubs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    lat NUMERIC(10, 8) NOT NULL,
    lon NUMERIC(11, 8) NOT NULL,
    location GEOMETRY(POINT, 4326) NOT NULL,
    area_id VARCHAR(50),  -- e.g., 'VI', 'Lekki', 'Mainland'
    zone VARCHAR(100),    -- e.g., 'Island', 'Mainland'
    is_active BOOLEAN DEFAULT true NOT NULL,
    address VARCHAR(500),
    landmark VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT uq_hub_coordinates UNIQUE (lat, lon)
);

-- Spatial index for hub location (GiST index for PostGIS)
CREATE INDEX idx_hubs_location ON hubs USING GIST (location);

-- Indexes for filtering and queries
CREATE INDEX idx_hubs_area ON hubs(area_id);
CREATE INDEX idx_hubs_zone ON hubs(zone);
CREATE INDEX idx_hubs_active ON hubs(is_active);
CREATE INDEX idx_hubs_area_active ON hubs(area_id, is_active);

-- Trigger function to auto-update location geometry from lat/lon
CREATE OR REPLACE FUNCTION update_hub_location()
RETURNS TRIGGER AS $$
BEGIN
    NEW.location = ST_SetSRID(ST_MakePoint(NEW.lon, NEW.lat), 4326);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to auto-update location on insert or update
CREATE TRIGGER trg_hubs_location
    BEFORE INSERT OR UPDATE OF lat, lon ON hubs
    FOR EACH ROW
    EXECUTE FUNCTION update_hub_location();

-- Trigger function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_hubs_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to auto-update updated_at on change
CREATE TRIGGER trg_hubs_updated_at
    BEFORE UPDATE ON hubs
    FOR EACH ROW
    EXECUTE FUNCTION update_hubs_updated_at();

-- Table and column comments
COMMENT ON TABLE hubs IS 'Transportation hubs for route origin and demand/supply aggregation';
COMMENT ON COLUMN hubs.id IS 'Unique hub identifier';
COMMENT ON COLUMN hubs.name IS 'Hub display name (e.g., "Victoria Island Hub")';
COMMENT ON COLUMN hubs.lat IS 'Latitude coordinate (decimal degrees)';
COMMENT ON COLUMN hubs.lon IS 'Longitude coordinate (decimal degrees)';
COMMENT ON COLUMN hubs.location IS 'PostGIS POINT geometry for spatial queries';
COMMENT ON COLUMN hubs.area_id IS 'Area/region identifier (e.g., "VI", "Lekki", "Ikeja")';
COMMENT ON COLUMN hubs.zone IS 'Zone grouping (e.g., "Island", "Mainland")';
COMMENT ON COLUMN hubs.is_active IS 'Whether hub is currently active and accepting routes';
COMMENT ON COLUMN hubs.address IS 'Full street address of hub';
COMMENT ON COLUMN hubs.landmark IS 'Notable landmark near hub';

-- Seed initial hubs for Lagos, Nigeria
INSERT INTO hubs (name, lat, lon, area_id, zone, address, landmark) VALUES
    ('Victoria Island Hub', 6.4281, 3.4219, 'VI', 'Island', 'Ahmadu Bello Way, Victoria Island', 'Eko Hotel'),
    ('Lekki Phase 1 Hub', 6.4474, 3.4780, 'Lekki', 'Island', 'Admiralty Way, Lekki Phase 1', 'Lekki Toll Gate'),
    ('Ikeja Hub', 6.5964, 3.3374, 'Ikeja', 'Mainland', 'Allen Avenue, Ikeja', 'Computer Village'),
    ('Yaba Hub', 6.5158, 3.3696, 'Yaba', 'Mainland', 'Herbert Macaulay Way, Yaba', 'Yaba Tech'),
    ('Surulere Hub', 6.4969, 3.3603, 'Surulere', 'Mainland', 'Adeniran Ogunsanya Street', 'National Stadium'),
    ('Ikoyi Hub', 6.4541, 3.4316, 'Ikoyi', 'Island', 'Awolowo Road, Ikoyi', 'Falomo Roundabout'),
    ('Maryland Hub', 6.5773, 3.3598, 'Maryland', 'Mainland', 'Ikorodu Road, Maryland', 'Maryland Mall'),
    ('Ajah Hub', 6.4698, 3.5651, 'Ajah', 'Island', 'Lekki-Epe Expressway, Ajah', 'Ajah Roundabout'),
    ('Obalende Hub', 6.4487, 3.3951, 'Obalende', 'Island', 'Obalende Road', 'Obalende Bus Stop'),
    ('Oshodi Hub', 6.5450, 3.3340, 'Oshodi', 'Mainland', 'Agege Motor Road, Oshodi', 'Oshodi Interchange');

-- Grant permissions (adjust based on your environment)
-- GRANT SELECT, INSERT, UPDATE ON hubs TO driver_service;
-- GRANT SELECT ON hubs TO matchmaking_service;
-- GRANT SELECT ON hubs TO search_service;
