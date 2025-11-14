-- Migration: Create materialized views for admin dashboard metrics
-- Phase 11: Admin Dashboard & Support APIs

-- Daily booking metrics
CREATE MATERIALIZED VIEW mv_daily_booking_metrics AS
SELECT 
    DATE(created_at) as metric_date,
    COUNT(*) as total_bookings,
    COUNT(*) FILTER (WHERE status = 'CONFIRMED') as confirmed_bookings,
    COUNT(*) FILTER (WHERE status = 'CANCELLED') as cancelled_bookings,
    COUNT(*) FILTER (WHERE status = 'COMPLETED') as completed_bookings,
    SUM(total_price) as total_revenue,
    AVG(total_price) as avg_booking_value,
    COUNT(DISTINCT rider_id) as unique_riders,
    COUNT(DISTINCT driver_id) as unique_drivers
FROM bookings
GROUP BY DATE(created_at)
ORDER BY metric_date DESC;

CREATE UNIQUE INDEX idx_mv_daily_booking_metrics_date ON mv_daily_booking_metrics(metric_date);

-- Daily payment metrics
CREATE MATERIALIZED VIEW mv_daily_payment_metrics AS
SELECT 
    DATE(created_at) as metric_date,
    COUNT(*) as total_payments,
    COUNT(*) FILTER (WHERE status = 'SUCCESS') as successful_payments,
    COUNT(*) FILTER (WHERE status = 'FAILED') as failed_payments,
    COUNT(*) FILTER (WHERE status = 'REFUNDED') as refunded_payments,
    SUM(amount) as total_amount,
    SUM(amount) FILTER (WHERE status = 'SUCCESS') as successful_amount,
    ROUND(
        (COUNT(*) FILTER (WHERE status = 'SUCCESS')::NUMERIC / NULLIF(COUNT(*), 0)) * 100, 
        2
    ) as success_rate
FROM payments
GROUP BY DATE(created_at)
ORDER BY metric_date DESC;

CREATE UNIQUE INDEX idx_mv_daily_payment_metrics_date ON mv_daily_payment_metrics(metric_date);

-- User growth metrics
CREATE MATERIALIZED VIEW mv_user_growth_metrics AS
SELECT 
    DATE(created_at) as metric_date,
    COUNT(*) as new_users,
    COUNT(*) FILTER (WHERE role = 'RIDER') as new_riders,
    COUNT(*) FILTER (WHERE role = 'DRIVER') as new_drivers,
    SUM(COUNT(*)) OVER (ORDER BY DATE(created_at)) as cumulative_users
FROM users
GROUP BY DATE(created_at)
ORDER BY metric_date DESC;

CREATE UNIQUE INDEX idx_mv_user_growth_metrics_date ON mv_user_growth_metrics(metric_date);

-- Real-time system health view (refreshed frequently)
CREATE MATERIALIZED VIEW mv_system_health AS
SELECT 
    NOW() as last_updated,
    (SELECT COUNT(*) FROM users WHERE is_active = true) as active_users,
    (SELECT COUNT(*) FROM users WHERE role = 'DRIVER' AND kyc_status = 'VERIFIED') as verified_drivers,
    (SELECT COUNT(*) FROM bookings WHERE status = 'PENDING' AND created_at > NOW() - INTERVAL '1 hour') as pending_bookings_1h,
    (SELECT COUNT(*) FROM bookings WHERE status = 'CONFIRMED' AND travel_date = CURRENT_DATE) as today_confirmed_bookings,
    (SELECT COUNT(*) FROM payments WHERE status = 'PENDING' AND created_at > NOW() - INTERVAL '1 hour') as pending_payments_1h,
    (SELECT COUNT(*) FROM disputes WHERE status = 'OPEN') as open_disputes,
    (SELECT COUNT(*) FROM user_suspensions WHERE is_active = true) as active_suspensions,
    (SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'SUCCESS' AND created_at > NOW() - INTERVAL '24 hours') as revenue_24h,
    (SELECT COUNT(*) FROM audit_logs WHERE created_at > NOW() - INTERVAL '1 hour') as audit_events_1h;

-- Function to refresh materialized views (called by scheduled job)
CREATE OR REPLACE FUNCTION refresh_admin_metrics()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_booking_metrics;
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_payment_metrics;
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_user_growth_metrics;
    REFRESH MATERIALIZED VIEW mv_system_health;
END;
$$ LANGUAGE plpgsql;

COMMENT ON MATERIALIZED VIEW mv_daily_booking_metrics IS 'Daily aggregated booking metrics for admin dashboard';
COMMENT ON MATERIALIZED VIEW mv_daily_payment_metrics IS 'Daily aggregated payment metrics for admin dashboard';
COMMENT ON MATERIALIZED VIEW mv_user_growth_metrics IS 'Daily user growth and registration metrics';
COMMENT ON MATERIALIZED VIEW mv_system_health IS 'Real-time system health indicators';
COMMENT ON FUNCTION refresh_admin_metrics IS 'Refreshes all materialized views for admin metrics (run every 5-15 minutes)';
