-- Phase 2.4: Cache Invalidation Strategy
-- Implement database triggers to automatically invalidate Redis cache on data changes

-- ============================================================================
-- TRIGGER FUNCTION: Invalidate route cache on route changes
-- ============================================================================

CREATE OR REPLACE FUNCTION invalidate_route_cache()
RETURNS TRIGGER AS $$
DECLARE
    v_origin_hub_id uuid;
    v_dest_hub_id uuid;
BEGIN
    -- Determine which route record to use (NEW for INSERT/UPDATE, OLD for DELETE)
    IF TG_OP = 'DELETE' THEN
        v_origin_hub_id := OLD.origin_hub_id;
        v_dest_hub_id := OLD.destination_hub_id;
    ELSE
        v_origin_hub_id := NEW.origin_hub_id;
        v_dest_hub_id := NEW.destination_hub_id;
    END IF;

    -- Log the invalidation (in production, this would send to Redis or queue)
    -- For now, we'll use pg_notify to signal the application
    PERFORM pg_notify(
        'cache_invalidation',
        json_build_object(
            'type', 'route',
            'operation', TG_OP,
            'route_id', COALESCE(NEW.id, OLD.id),
            'origin_hub_id', v_origin_hub_id,
            'destination_hub_id', v_dest_hub_id,
            'timestamp', NOW()
        )::text
    );

    -- Return appropriate record
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    ELSE
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- TRIGGER: Route changes (INSERT, UPDATE, DELETE)
-- ============================================================================

DROP TRIGGER IF EXISTS trigger_invalidate_route_cache ON routes;
CREATE TRIGGER trigger_invalidate_route_cache
AFTER INSERT OR UPDATE OR DELETE ON routes
FOR EACH ROW
EXECUTE FUNCTION invalidate_route_cache();

-- ============================================================================
-- TRIGGER FUNCTION: Invalidate cache on route availability changes
-- ============================================================================

CREATE OR REPLACE FUNCTION invalidate_route_cache_on_availability()
RETURNS TRIGGER AS $$
BEGIN
    -- Only invalidate if seats_available or status changed
    IF (TG_OP = 'UPDATE' AND (
        OLD.seats_available IS DISTINCT FROM NEW.seats_available OR
        OLD.status IS DISTINCT FROM NEW.status
    )) THEN
        PERFORM pg_notify(
            'cache_invalidation',
            json_build_object(
                'type', 'route_availability',
                'route_id', NEW.id,
                'origin_hub_id', NEW.origin_hub_id,
                'destination_hub_id', NEW.destination_hub_id,
                'old_seats', OLD.seats_available,
                'new_seats', NEW.seats_available,
                'old_status', OLD.status,
                'new_status', NEW.status,
                'timestamp', NOW()
            )::text
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- TRIGGER: Route availability changes
-- ============================================================================

DROP TRIGGER IF EXISTS trigger_invalidate_route_availability ON routes;
CREATE TRIGGER trigger_invalidate_route_availability
AFTER UPDATE ON routes
FOR EACH ROW
WHEN (
    OLD.seats_available IS DISTINCT FROM NEW.seats_available OR
    OLD.status IS DISTINCT FROM NEW.status
)
EXECUTE FUNCTION invalidate_route_cache_on_availability();

-- ============================================================================
-- TRIGGER FUNCTION: Invalidate cache on hub changes
-- ============================================================================

CREATE OR REPLACE FUNCTION invalidate_hub_cache()
RETURNS TRIGGER AS $$
BEGIN
    -- Notify application of hub change
    PERFORM pg_notify(
        'cache_invalidation',
        json_build_object(
            'type', 'hub',
            'operation', TG_OP,
            'hub_id', COALESCE(NEW.id, OLD.id),
            'hub_name', COALESCE(NEW.name, OLD.name),
            'timestamp', NOW()
        )::text
    );

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    ELSE
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- TRIGGER: Hub changes
-- ============================================================================

DROP TRIGGER IF EXISTS trigger_invalidate_hub_cache ON hubs;
CREATE TRIGGER trigger_invalidate_hub_cache
AFTER INSERT OR UPDATE OR DELETE ON hubs
FOR EACH ROW
EXECUTE FUNCTION invalidate_hub_cache();

-- ============================================================================
-- TRIGGER FUNCTION: Invalidate cache on stop changes
-- ============================================================================

CREATE OR REPLACE FUNCTION invalidate_stop_cache()
RETURNS TRIGGER AS $$
BEGIN
    -- Only invalidate if stop's hub or active status changed
    IF (TG_OP = 'UPDATE' AND (
        OLD.hub_id IS DISTINCT FROM NEW.hub_id OR
        OLD.is_active IS DISTINCT FROM NEW.is_active
    )) OR TG_OP IN ('INSERT', 'DELETE') THEN
        PERFORM pg_notify(
            'cache_invalidation',
            json_build_object(
                'type', 'stop',
                'operation', TG_OP,
                'stop_id', COALESCE(NEW.id, OLD.id),
                'hub_id', COALESCE(NEW.hub_id, OLD.hub_id),
                'timestamp', NOW()
            )::text
        );
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    ELSE
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- TRIGGER: Stop changes
-- ============================================================================

DROP TRIGGER IF EXISTS trigger_invalidate_stop_cache ON stops;
CREATE TRIGGER trigger_invalidate_stop_cache
AFTER INSERT OR UPDATE OR DELETE ON stops
FOR EACH ROW
EXECUTE FUNCTION invalidate_stop_cache();

-- ============================================================================
-- TRIGGER FUNCTION: Refresh driver stats after profile updates
-- ============================================================================

CREATE OR REPLACE FUNCTION schedule_driver_stats_refresh()
RETURNS TRIGGER AS $$
BEGIN
    -- Notify application to refresh materialized view
    PERFORM pg_notify(
        'stats_refresh',
        json_build_object(
            'type', 'driver_profile',
            'driver_id', COALESCE(NEW.driver_id, OLD.driver_id),
            'timestamp', NOW()
        )::text
    );

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    ELSE
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- TRIGGER: Driver profile changes
-- ============================================================================

DROP TRIGGER IF EXISTS trigger_schedule_driver_stats_refresh ON driver_profiles;
CREATE TRIGGER trigger_schedule_driver_stats_refresh
AFTER INSERT OR UPDATE OR DELETE ON driver_profiles
FOR EACH ROW
EXECUTE FUNCTION schedule_driver_stats_refresh();

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON FUNCTION invalidate_route_cache() IS 'Sends pg_notify to invalidate route caches when routes change';
COMMENT ON FUNCTION invalidate_route_cache_on_availability() IS 'Invalidates cache when route availability or status changes';
COMMENT ON FUNCTION invalidate_hub_cache() IS 'Invalidates cache when hubs change';
COMMENT ON FUNCTION invalidate_stop_cache() IS 'Invalidates cache when stops change';
COMMENT ON FUNCTION schedule_driver_stats_refresh() IS 'Notifies app to refresh driver stats materialized view';

-- ============================================================================
-- TEST NOTIFICATIONS
-- ============================================================================

-- To test, in a separate session run:
-- LISTEN cache_invalidation;
-- LISTEN stats_refresh;

-- Then run these test operations:
-- UPDATE routes SET seats_available = seats_available - 1 WHERE id = (SELECT id FROM routes LIMIT 1);
-- UPDATE hubs SET is_active = is_active WHERE id = (SELECT id FROM hubs LIMIT 1);
