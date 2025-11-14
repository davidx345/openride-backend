"""Metrics aggregation service using ClickHouse."""

from datetime import datetime, timedelta
from decimal import Decimal
from typing import Any, Dict, List, Optional

from clickhouse_connect.driver import Client

from app.core.clickhouse import get_clickhouse
from app.core.logging import get_logger
from app.schemas.metrics import (
    BookingMetricsResponse,
    DriverEarningsResponse,
    GeographicMetricsResponse,
    PaymentMetricsResponse,
    PopularRouteResponse,
    RealtimeMetricsResponse,
    TimeSeriesDataPoint,
    TripMetricsResponse,
    UserMetricsResponse,
)

logger = get_logger(__name__)


class MetricsAggregationService:
    """Service for aggregating metrics from ClickHouse."""

    def __init__(self, clickhouse: Client):
        """Initialize service with ClickHouse client."""
        self.ch = clickhouse

    async def get_user_metrics(
        self,
        start_date: datetime,
        end_date: datetime,
        include_time_series: bool = False
    ) -> UserMetricsResponse:
        """Get user metrics for date range."""
        try:
            # Query daily aggregations
            query = """
            SELECT
                sum(new_registrations) as total_registrations,
                sum(kyc_verified_count) as total_kyc_verified,
                sum(driver_upgrades) as total_driver_upgrades
            FROM openride_analytics.mv_daily_user_metrics
            WHERE date >= {start_date:Date} AND date <= {end_date:Date}
            """
            
            result = self.ch.query(query, parameters={
                "start_date": start_date.date(),
                "end_date": end_date.date()
            })
            
            row = result.first_row if result.row_count > 0 else [0, 0, 0]
            
            # Calculate DAU/WAU/MAU from user_events
            dau = self._calculate_active_users(start_date, end_date, 1)
            wau = self._calculate_active_users(start_date, end_date, 7)
            mau = self._calculate_active_users(start_date, end_date, 30)
            
            # Get total users count
            total_users = self._get_total_users()
            
            # Calculate rates
            kyc_rate = (row[1] / row[0] * 100) if row[0] > 0 else 0.0
            driver_rate = (row[2] / row[0] * 100) if row[0] > 0 else 0.0
            
            return UserMetricsResponse(
                date_range=f"{start_date.date()} to {end_date.date()}",
                dau=dau,
                wau=wau,
                mau=mau,
                new_registrations=row[0],
                total_users=total_users,
                kyc_completion_rate=round(kyc_rate, 2),
                driver_activation_rate=round(driver_rate, 2),
            )
            
        except Exception as e:
            logger.error("get_user_metrics_failed", error=str(e))
            raise

    def _calculate_active_users(self, start_date: datetime, end_date: datetime, days: int) -> int:
        """Calculate active users for period."""
        lookback_date = end_date - timedelta(days=days)
        query = """
        SELECT uniqExact(user_id) as active_users
        FROM openride_analytics.user_events
        WHERE event_timestamp >= {lookback_date:DateTime} 
        AND event_timestamp <= {end_date:DateTime}
        """
        result = self.ch.query(query, parameters={
            "lookback_date": lookback_date,
            "end_date": end_date
        })
        return result.first_row[0] if result.row_count > 0 else 0

    def _get_total_users(self) -> int:
        """Get total registered users count."""
        query = "SELECT count(DISTINCT user_id) FROM openride_analytics.user_events WHERE event_type = 'user.registered'"
        result = self.ch.query(query)
        return result.first_row[0] if result.row_count > 0 else 0

    async def get_booking_metrics(
        self,
        start_date: datetime,
        end_date: datetime
    ) -> BookingMetricsResponse:
        """Get booking metrics for date range."""
        try:
            query = """
            SELECT
                sum(bookings_created) as created,
                sum(bookings_confirmed) as confirmed,
                sum(bookings_cancelled) as cancelled,
                sum(bookings_completed) as completed,
                sum(total_booking_value) as total_value,
                avg(avg_booking_value) as avg_value
            FROM openride_analytics.mv_daily_booking_metrics
            WHERE date >= {start_date:Date} AND date <= {end_date:Date}
            """
            
            result = self.ch.query(query, parameters={
                "start_date": start_date.date(),
                "end_date": end_date.date()
            })
            
            row = result.first_row if result.row_count > 0 else [0, 0, 0, 0, 0, 0]
            
            created, confirmed, cancelled, completed, total_value, avg_value = row
            
            conversion_rate = (confirmed / created * 100) if created > 0 else 0.0
            cancellation_rate = (cancelled / created * 100) if created > 0 else 0.0
            
            return BookingMetricsResponse(
                date_range=f"{start_date.date()} to {end_date.date()}",
                bookings_created=created,
                bookings_confirmed=confirmed,
                bookings_cancelled=cancelled,
                bookings_completed=completed,
                conversion_rate=round(conversion_rate, 2),
                cancellation_rate=round(cancellation_rate, 2),
                avg_booking_value=Decimal(str(avg_value)) if avg_value else Decimal("0.00"),
                total_booking_value=Decimal(str(total_value)) if total_value else Decimal("0.00"),
            )
            
        except Exception as e:
            logger.error("get_booking_metrics_failed", error=str(e))
            raise

    async def get_payment_metrics(
        self,
        start_date: datetime,
        end_date: datetime
    ) -> PaymentMetricsResponse:
        """Get payment metrics for date range."""
        try:
            query = """
            SELECT
                sum(payments_initiated) as initiated,
                sum(payments_success) as success,
                sum(payments_failed) as failed,
                sum(payments_refunded) as refunded,
                sum(total_revenue) as revenue,
                avg(avg_processing_time_ms) as avg_time
            FROM openride_analytics.mv_daily_payment_metrics
            WHERE date >= {start_date:Date} AND date <= {end_date:Date}
            """
            
            result = self.ch.query(query, parameters={
                "start_date": start_date.date(),
                "end_date": end_date.date()
            })
            
            row = result.first_row if result.row_count > 0 else [0, 0, 0, 0, 0, 0]
            
            initiated, success, failed, refunded, revenue, avg_time = row
            
            success_rate = (success / initiated * 100) if initiated > 0 else 0.0
            
            return PaymentMetricsResponse(
                date_range=f"{start_date.date()} to {end_date.date()}",
                payments_initiated=initiated,
                payments_success=success,
                payments_failed=failed,
                payments_refunded=refunded,
                success_rate=round(success_rate, 2),
                avg_processing_time_ms=round(avg_time, 2) if avg_time else 0.0,
                total_revenue=Decimal(str(revenue)) if revenue else Decimal("0.00"),
            )
            
        except Exception as e:
            logger.error("get_payment_metrics_failed", error=str(e))
            raise

    async def get_trip_metrics(
        self,
        start_date: datetime,
        end_date: datetime
    ) -> TripMetricsResponse:
        """Get trip metrics for date range."""
        try:
            query = """
            SELECT
                sum(trips_started) as started,
                sum(trips_completed) as completed,
                sum(trips_cancelled) as cancelled,
                avg(avg_duration_minutes) as avg_duration,
                avg(avg_distance_km) as avg_distance,
                sum(total_driver_earnings) as driver_earnings,
                sum(total_platform_commission) as commission,
                sum(on_time_trips) as on_time,
                max(active_drivers) as drivers
            FROM openride_analytics.mv_daily_trip_metrics
            WHERE date >= {start_date:Date} AND date <= {end_date:Date}
            """
            
            result = self.ch.query(query, parameters={
                "start_date": start_date.date(),
                "end_date": end_date.date()
            })
            
            row = result.first_row if result.row_count > 0 else [0, 0, 0, 0, 0, 0, 0, 0, 0]
            
            started, completed, cancelled, avg_dur, avg_dist, earnings, comm, on_time, drivers = row
            
            completion_rate = (completed / started * 100) if started > 0 else 0.0
            on_time_perf = (on_time / completed * 100) if completed > 0 else 0.0
            
            return TripMetricsResponse(
                date_range=f"{start_date.date()} to {end_date.date()}",
                trips_started=started,
                trips_completed=completed,
                trips_cancelled=cancelled,
                completion_rate=round(completion_rate, 2),
                avg_duration_minutes=round(avg_dur, 2) if avg_dur else 0.0,
                avg_distance_km=round(avg_dist, 2) if avg_dist else 0.0,
                on_time_performance=round(on_time_perf, 2),
                total_driver_earnings=Decimal(str(earnings)) if earnings else Decimal("0.00"),
                total_platform_commission=Decimal(str(comm)) if comm else Decimal("0.00"),
                active_drivers=drivers,
            )
            
        except Exception as e:
            logger.error("get_trip_metrics_failed", error=str(e))
            raise

    async def get_realtime_metrics(self) -> RealtimeMetricsResponse:
        """Get real-time dashboard metrics."""
        try:
            # Last hour metrics from mv_hourly_realtime_metrics
            now = datetime.utcnow()
            one_hour_ago = now - timedelta(hours=1)
            
            query = """
            SELECT
                sumIf(active_users, metric_type = 'user') as active_users,
                sumIf(metric_value, metric_type = 'booking') as bookings,
                sumIf(metric_value, metric_type = 'payment') as revenue
            FROM openride_analytics.mv_hourly_realtime_metrics
            WHERE hour = {hour:DateTime}
            """
            
            result = self.ch.query(query, parameters={"hour": one_hour_ago.replace(minute=0, second=0)})
            row = result.first_row if result.row_count > 0 else [0, 0, 0]
            
            # Active trips (trips started but not completed in last hour)
            active_trips_query = """
            SELECT count() FROM openride_analytics.trip_events
            WHERE event_type = 'trip.started' 
            AND event_timestamp >= {one_hour_ago:DateTime}
            AND trip_id NOT IN (
                SELECT trip_id FROM openride_analytics.trip_events
                WHERE event_type IN ('trip.completed', 'trip.cancelled')
                AND event_timestamp >= {one_hour_ago:DateTime}
            )
            """
            active_trips_result = self.ch.query(active_trips_query, parameters={"one_hour_ago": one_hour_ago})
            active_trips = active_trips_result.first_row[0] if active_trips_result.row_count > 0 else 0
            
            # Active drivers (drivers with location updates in last 15 minutes)
            active_drivers_query = """
            SELECT uniqExact(driver_id) FROM openride_analytics.location_events
            WHERE event_timestamp >= {fifteen_min_ago:DateTime}
            """
            fifteen_min_ago = now - timedelta(minutes=15)
            active_drivers_result = self.ch.query(active_drivers_query, parameters={"fifteen_min_ago": fifteen_min_ago})
            active_drivers = active_drivers_result.first_row[0] if active_drivers_result.row_count > 0 else 0
            
            return RealtimeMetricsResponse(
                timestamp=now,
                active_users=row[0],
                active_drivers=active_drivers,
                active_trips=active_trips,
                bookings_last_hour=row[1],
                revenue_last_hour=Decimal(str(row[2])) if row[2] else Decimal("0.00"),
                system_health="healthy",
            )
            
        except Exception as e:
            logger.error("get_realtime_metrics_failed", error=str(e))
            raise

    async def get_driver_metrics(
        self,
        start_date: datetime,
        end_date: datetime,
        limit: int = 100
    ) -> List[DriverEarningsResponse]:
        """Get top driver earnings and performance metrics."""
        try:
            query = """
            SELECT
                driver_id,
                sum(total_trips) as trips,
                sum(total_earnings) as earnings,
                avg(avg_earnings_per_trip) as avg_per_trip,
                sum(total_distance_km) as distance,
                max(rating_avg) as rating
            FROM openride_analytics.agg_driver_earnings
            WHERE date >= {start_date:Date} AND date <= {end_date:Date}
            GROUP BY driver_id
            ORDER BY earnings DESC
            LIMIT {limit:UInt32}
            """
            
            result = self.ch.query(query, parameters={
                "start_date": start_date.date(),
                "end_date": end_date.date(),
                "limit": limit
            })
            
            drivers = []
            for row in result.result_rows:
                driver_id, trips, earnings, avg_per_trip, distance, rating = row
                drivers.append(DriverEarningsResponse(
                    driver_id=driver_id,
                    total_trips=trips,
                    total_earnings=Decimal(str(earnings)) if earnings else Decimal("0.00"),
                    avg_earnings_per_trip=Decimal(str(avg_per_trip)) if avg_per_trip else Decimal("0.00"),
                    total_distance_km=round(distance, 2) if distance else 0.0,
                    avg_rating=round(rating, 2) if rating else 0.0,
                ))
            
            return drivers
            
        except Exception as e:
            logger.error("get_driver_metrics_failed", error=str(e))
            raise

    async def get_route_metrics(
        self,
        start_date: datetime,
        end_date: datetime,
        limit: int = 50
    ) -> List[PopularRouteResponse]:
        """Get popular routes by bookings and revenue."""
        try:
            query = """
            SELECT
                route_id,
                any(route_name) as route_name,
                any(origin_city) as origin_city,
                any(destination_city) as destination_city,
                any(driver_id) as driver_id,
                sum(total_bookings) as bookings,
                sum(total_revenue) as revenue,
                avg(avg_occupancy) as occupancy,
                any(seats_total) as seats
            FROM openride_analytics.agg_popular_routes
            WHERE date >= {start_date:Date} AND date <= {end_date:Date}
            GROUP BY route_id
            ORDER BY bookings DESC
            LIMIT {limit:UInt32}
            """
            
            result = self.ch.query(query, parameters={
                "start_date": start_date.date(),
                "end_date": end_date.date(),
                "limit": limit
            })
            
            routes = []
            for row in result.result_rows:
                route_id, name, origin, dest, driver_id, bookings, revenue, occupancy, seats = row
                routes.append(PopularRouteResponse(
                    route_id=route_id,
                    route_name=name,
                    origin_city=origin,
                    destination_city=dest,
                    driver_id=driver_id,
                    total_bookings=bookings,
                    total_revenue=Decimal(str(revenue)) if revenue else Decimal("0.00"),
                    avg_occupancy_rate=round(occupancy * 100, 2) if occupancy else 0.0,
                    seats_total=seats,
                ))
            
            return routes
            
        except Exception as e:
            logger.error("get_route_metrics_failed", error=str(e))
            raise

    async def get_geographic_metrics(
        self,
        start_date: datetime,
        end_date: datetime,
        metric_type: str = "bookings"
    ) -> List[GeographicMetricsResponse]:
        """Get geographic distribution metrics."""
        try:
            query = """
            SELECT
                state,
                city,
                sum(metric_value) as total
            FROM openride_analytics.agg_geographic_metrics
            WHERE date >= {start_date:Date} 
            AND date <= {end_date:Date}
            AND metric_type = {metric_type:String}
            GROUP BY state, city
            ORDER BY total DESC
            LIMIT 100
            """
            
            result = self.ch.query(query, parameters={
                "start_date": start_date.date(),
                "end_date": end_date.date(),
                "metric_type": metric_type
            })
            
            locations = []
            for row in result.result_rows:
                state, city, total = row
                locations.append(GeographicMetricsResponse(
                    state=state,
                    city=city,
                    metric_type=metric_type,
                    metric_value=float(total) if total else 0.0,
                ))
            
            return locations
            
        except Exception as e:
            logger.error("get_geographic_metrics_failed", error=str(e))
            raise
