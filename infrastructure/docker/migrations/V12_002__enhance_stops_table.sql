-- Migration: V12_002__enhance_stops_table.sql
-- Description: Add hub_id, area_id, and is_active columns to stops table
-- Author: OpenRide Platform Team
-- Date: 2025-11-14
-- Phase: 1.2 - Enhance Stops Table

-- Add new columns to stops table
ALTER TABLE stops
    ADD COLUMN hub_id UUID,
    ADD COLUMN area_id VARCHAR(50),
    ADD COLUMN is_active BOOLEAN DEFAULT true NOT NULL;

-- Add foreign key constraint to hubs table
ALTER TABLE stops
    ADD CONSTRAINT fk_stops_hub
    FOREIGN KEY (hub_id) REFERENCES hubs(id)
    ON DELETE SET NULL;

-- Create indexes for filtering and joins
CREATE INDEX idx_stops_hub_id ON stops(hub_id);
CREATE INDEX idx_stops_area_id ON stops(area_id);
CREATE INDEX idx_stops_is_active ON stops(is_active);
CREATE INDEX idx_stops_hub_active ON stops(hub_id, is_active);

-- Trigger function to auto-populate area_id from hub
CREATE OR REPLACE FUNCTION update_stop_area_from_hub()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.hub_id IS NOT NULL THEN
        SELECT area_id INTO NEW.area_id
        FROM hubs
        WHERE id = NEW.hub_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to auto-populate area_id when hub is assigned
CREATE TRIGGER trg_stops_area_from_hub
    BEFORE INSERT OR UPDATE OF hub_id ON stops
    FOR EACH ROW
    EXECUTE FUNCTION update_stop_area_from_hub();

-- Column comments
COMMENT ON COLUMN stops.hub_id IS 'Reference to parent hub for route aggregation';
COMMENT ON COLUMN stops.area_id IS 'Area identifier inherited from hub (e.g., "VI", "Lekki")';
COMMENT ON COLUMN stops.is_active IS 'Whether stop is currently active and accepting routes';

-- Data migration: Assign each stop to nearest active hub within 1km
UPDATE stops s
SET hub_id = (
    SELECT h.id
    FROM hubs h
    WHERE h.is_active = true
    ORDER BY ST_Distance(
        ST_Transform(s.location::geometry, 4326)::geography,
        ST_Transform(h.location::geometry, 4326)::geography
    )
    LIMIT 1
)
WHERE s.hub_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM hubs h
      WHERE h.is_active = true
        AND ST_DWithin(
            ST_Transform(s.location::geometry, 4326)::geography,
            ST_Transform(h.location::geometry, 4326)::geography,
            1000  -- 1km radius
        )
  );

-- Update area_id for stops that got assigned to hubs
UPDATE stops s
SET area_id = h.area_id
FROM hubs h
WHERE s.hub_id = h.id AND s.area_id IS NULL;
