-- Migration: V1__create_bookings_schema.sql
-- Description: Create bookings and related tables with seat inventory management
-- Author: OpenRide Platform Team
-- Date: 2025-01-13

-- Create booking status enum type
CREATE TYPE booking_status AS ENUM (
    'PENDING',
    'HELD',
    'PAYMENT_INITIATED',
    'PAID',
    'CONFIRMED',
    'CHECKED_IN',
    'COMPLETED',
    'CANCELLED',
    'REFUNDED',
    'EXPIRED'
);

-- Create payment status enum type
CREATE TYPE payment_status AS ENUM (
    'PENDING',
    'SUCCESS',
    'FAILED',
    'REFUNDED'
);

-- Create refund status enum type
CREATE TYPE refund_status AS ENUM (
    'NONE',
    'PENDING',
    'PROCESSED',
    'FAILED'
);

-- Create booking source enum type
CREATE TYPE booking_source AS ENUM (
    'WEB',
    'MOBILE',
    'API'
);

-- Create bookings table
CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_reference VARCHAR(20) UNIQUE NOT NULL,
    
    -- User references
    rider_id UUID NOT NULL,
    driver_id UUID NOT NULL,  -- Denormalized for quick access
    
    -- Route and journey details
    route_id UUID NOT NULL,
    origin_stop_id UUID NOT NULL,
    destination_stop_id UUID NOT NULL,
    travel_date DATE NOT NULL,
    departure_time TIME NOT NULL,
    
    -- Seat allocation
    seats_booked INT NOT NULL CHECK (seats_booked > 0 AND seats_booked <= 10),
    seat_numbers INT[] NOT NULL,
    
    -- Pricing
    price_per_seat DECIMAL(10, 2) NOT NULL CHECK (price_per_seat >= 0),
    total_price DECIMAL(10, 2) NOT NULL CHECK (total_price >= 0),
    platform_fee DECIMAL(10, 2) NOT NULL DEFAULT 0.00 CHECK (platform_fee >= 0),
    
    -- Status tracking
    status booking_status NOT NULL DEFAULT 'PENDING',
    payment_id UUID,
    payment_status payment_status DEFAULT 'PENDING',
    
    -- Cancellation and refund
    cancellation_reason TEXT,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    refund_amount DECIMAL(10, 2) CHECK (refund_amount >= 0),
    refund_status refund_status DEFAULT 'NONE',
    
    -- Metadata
    idempotency_key VARCHAR(100) UNIQUE,
    booking_source booking_source DEFAULT 'WEB',
    
    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE,
    confirmed_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit
    created_by UUID,
    updated_by UUID,
    
    -- Constraints
    CONSTRAINT chk_seat_numbers_not_empty CHECK (array_length(seat_numbers, 1) > 0),
    CONSTRAINT chk_seat_numbers_match_count CHECK (array_length(seat_numbers, 1) = seats_booked),
    CONSTRAINT chk_cancellation_reason CHECK (
        (status = 'CANCELLED' AND cancellation_reason IS NOT NULL) OR
        (status != 'CANCELLED')
    ),
    CONSTRAINT chk_confirmed_timestamp CHECK (
        (status IN ('CONFIRMED', 'CHECKED_IN', 'COMPLETED') AND confirmed_at IS NOT NULL) OR
        (status NOT IN ('CONFIRMED', 'CHECKED_IN', 'COMPLETED'))
    )
);

-- Indexes for bookings
CREATE INDEX idx_bookings_rider ON bookings(rider_id);
CREATE INDEX idx_bookings_driver ON bookings(driver_id);
CREATE INDEX idx_bookings_route ON bookings(route_id);
CREATE INDEX idx_bookings_route_date ON bookings(route_id, travel_date);
CREATE INDEX idx_bookings_route_date_status ON bookings(route_id, travel_date, status);
CREATE INDEX idx_bookings_status ON bookings(status);
CREATE INDEX idx_bookings_travel_date ON bookings(travel_date);
CREATE INDEX idx_bookings_reference ON bookings(booking_reference);
CREATE INDEX idx_bookings_payment ON bookings(payment_id) WHERE payment_id IS NOT NULL;
CREATE INDEX idx_bookings_idempotency ON bookings(idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_bookings_expires_at ON bookings(expires_at) 
    WHERE status IN ('PENDING', 'HELD') AND expires_at IS NOT NULL;
CREATE INDEX idx_bookings_created_at ON bookings(created_at);

-- Create booking status history table
CREATE TABLE booking_status_history (
    id BIGSERIAL PRIMARY KEY,
    booking_id UUID NOT NULL,
    from_status booking_status,
    to_status booking_status NOT NULL,
    reason TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    
    CONSTRAINT fk_booking FOREIGN KEY (booking_id) 
        REFERENCES bookings(id) ON DELETE CASCADE
);

-- Indexes for booking status history
CREATE INDEX idx_booking_status_history_booking ON booking_status_history(booking_id);
CREATE INDEX idx_booking_status_history_created_at ON booking_status_history(created_at);
CREATE INDEX idx_booking_status_history_to_status ON booking_status_history(to_status);

-- Create seat holds table (backup for Redis)
CREATE TABLE seat_holds (
    id BIGSERIAL PRIMARY KEY,
    route_id UUID NOT NULL,
    travel_date DATE NOT NULL,
    seat_number INT NOT NULL,
    booking_id UUID NOT NULL,
    held_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    released_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT unique_seat_hold UNIQUE (route_id, travel_date, seat_number, booking_id)
);

-- Indexes for seat holds
CREATE INDEX idx_seat_holds_route_date ON seat_holds(route_id, travel_date);
CREATE INDEX idx_seat_holds_booking ON seat_holds(booking_id);
CREATE INDEX idx_seat_holds_expires_at ON seat_holds(expires_at) 
    WHERE released_at IS NULL;

-- Function: Generate booking reference
CREATE OR REPLACE FUNCTION generate_booking_reference()
RETURNS VARCHAR(20) AS $$
DECLARE
    ref VARCHAR(20);
    exists_count INT;
BEGIN
    LOOP
        -- Format: BK + YYYYMMDD + 6 random alphanumeric chars
        ref := 'BK' || 
               TO_CHAR(NOW(), 'YYYYMMDD') || 
               UPPER(SUBSTRING(MD5(RANDOM()::TEXT || CLOCK_TIMESTAMP()::TEXT) FROM 1 FOR 6));
        
        -- Check if reference already exists
        SELECT COUNT(*) INTO exists_count
        FROM bookings
        WHERE booking_reference = ref;
        
        EXIT WHEN exists_count = 0;
    END LOOP;
    
    RETURN ref;
END;
$$ LANGUAGE plpgsql;

-- Function: Update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger: Update bookings.updated_at on change
CREATE TRIGGER trg_bookings_updated_at
    BEFORE UPDATE ON bookings
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Function: Record booking status change
CREATE OR REPLACE FUNCTION record_booking_status_change()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        INSERT INTO booking_status_history (
            booking_id,
            from_status,
            to_status,
            created_by
        ) VALUES (
            NEW.id,
            OLD.status,
            NEW.status,
            NEW.updated_by
        );
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger: Auto-record status changes
CREATE TRIGGER trg_booking_status_change
    AFTER UPDATE ON bookings
    FOR EACH ROW
    WHEN (OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION record_booking_status_change();

-- Grant permissions (adjust as needed for your environment)
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO booking_service;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO booking_service;

-- Comments
COMMENT ON TABLE bookings IS 'Core bookings table with seat inventory and state machine';
COMMENT ON TABLE booking_status_history IS 'Audit trail for booking status transitions';
COMMENT ON TABLE seat_holds IS 'Backup table for Redis seat holds for reconciliation';
COMMENT ON COLUMN bookings.booking_reference IS 'Unique human-readable booking reference (e.g., BK20250113AB12CD)';
COMMENT ON COLUMN bookings.seat_numbers IS 'Array of allocated seat numbers (e.g., {1, 2, 3})';
COMMENT ON COLUMN bookings.idempotency_key IS 'Client-provided idempotency key for duplicate prevention';
COMMENT ON COLUMN bookings.expires_at IS 'Expiration time for PENDING/HELD bookings (created_at + 10 minutes)';
