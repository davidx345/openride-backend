-- Migration: V12_003__add_hub_to_routes.sql
-- Description: Add hub references, currency, and enhanced metadata to routes table
-- Author: OpenRide Platform Team
-- Date: 2025-11-14
-- Phase: 1.3 - Add Hub Support to Routes

-- Add new columns to routes table
ALTER TABLE routes
    ADD COLUMN origin_hub_id UUID,
    ADD COLUMN destination_hub_id UUID,
    ADD COLUMN currency VARCHAR(3) DEFAULT 'NGN' NOT NULL,
    ADD COLUMN estimated_duration_minutes INTEGER,
    ADD COLUMN route_template_id UUID;

-- Add foreign key constraints to hubs table
ALTER TABLE routes
    ADD CONSTRAINT fk_routes_origin_hub
    FOREIGN KEY (origin_hub_id) REFERENCES hubs(id)
    ON DELETE SET NULL;

ALTER TABLE routes
    ADD CONSTRAINT fk_routes_destination_hub
    FOREIGN KEY (destination_hub_id) REFERENCES hubs(id)
    ON DELETE SET NULL;

-- Create indexes for filtering and joins
CREATE INDEX idx_routes_origin_hub ON routes(origin_hub_id);
CREATE INDEX idx_routes_destination_hub ON routes(destination_hub_id);
CREATE INDEX idx_routes_hub_pair ON routes(origin_hub_id, destination_hub_id);
CREATE INDEX idx_routes_currency ON routes(currency);
CREATE INDEX idx_routes_template ON routes(route_template_id);

-- Trigger function to auto-assign origin_hub_id from first stop
CREATE OR REPLACE FUNCTION update_route_origin_hub()
RETURNS TRIGGER AS $$
BEGIN
    -- Only auto-assign if not explicitly set
    IF NEW.origin_hub_id IS NULL THEN
        SELECT s.hub_id INTO NEW.origin_hub_id
        FROM route_stops rs
        JOIN stops s ON rs.stop_id = s.id
        WHERE rs.route_id = NEW.id
        ORDER BY rs.stop_order ASC
        LIMIT 1;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger function to auto-assign destination_hub_id from last stop
CREATE OR REPLACE FUNCTION update_route_destination_hub()
RETURNS TRIGGER AS $$
BEGIN
    -- Only auto-assign if not explicitly set
    IF NEW.destination_hub_id IS NULL THEN
        SELECT s.hub_id INTO NEW.destination_hub_id
        FROM route_stops rs
        JOIN stops s ON rs.stop_id = s.id
        WHERE rs.route_id = NEW.id
        ORDER BY rs.stop_order DESC
        LIMIT 1;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Note: Triggers are not created automatically as they require route_stops to exist first
-- These will be created via application code or separate migration after route creation

-- Column comments
COMMENT ON COLUMN routes.origin_hub_id IS 'Hub where route originates (from first stop hub)';
COMMENT ON COLUMN routes.destination_hub_id IS 'Hub where route terminates (from last stop hub)';
COMMENT ON COLUMN routes.currency IS 'Currency code for base_price (ISO 4217, e.g., NGN, USD)';
COMMENT ON COLUMN routes.estimated_duration_minutes IS 'Estimated trip duration in minutes';
COMMENT ON COLUMN routes.route_template_id IS 'Reference to route template for recurring routes';

-- Data migration: Assign origin_hub_id from first stop in each route
UPDATE routes r
SET origin_hub_id = (
    SELECT s.hub_id
    FROM route_stops rs
    JOIN stops s ON rs.stop_id = s.id
    WHERE rs.route_id = r.id
    ORDER BY rs.stop_order ASC
    LIMIT 1
)
WHERE r.origin_hub_id IS NULL;

-- Data migration: Assign destination_hub_id from last stop in each route
UPDATE routes r
SET destination_hub_id = (
    SELECT s.hub_id
    FROM route_stops rs
    JOIN stops s ON rs.stop_id = s.id
    WHERE rs.route_id = r.id
    ORDER BY rs.stop_order DESC
    LIMIT 1
)
WHERE r.destination_hub_id IS NULL;

-- Set estimated_duration_minutes based on distance (rough estimate: 30km/h average speed)
-- This is a placeholder; real values should be computed from actual trip data
UPDATE routes
SET estimated_duration_minutes = CASE
    WHEN ST_Distance(
        ST_Transform(start_location::geometry, 4326)::geography,
        ST_Transform(end_location::geometry, 4326)::geography
    ) > 0 THEN
        ROUND(
            ST_Distance(
                ST_Transform(start_location::geometry, 4326)::geography,
                ST_Transform(end_location::geometry, 4326)::geography
            ) / 1000.0  -- meters to km
            / 30.0      -- average speed 30 km/h
            * 60.0      -- convert to minutes
        )::INTEGER
    ELSE
        30  -- default 30 minutes for routes without distance
    END
WHERE estimated_duration_minutes IS NULL;
