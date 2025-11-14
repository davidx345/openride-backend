"""Advanced analytics service for funnel, cohort, and retention analysis."""

from datetime import datetime, timedelta
from decimal import Decimal
from typing import Any, Dict, List, Optional

from clickhouse_connect.driver import Client

from app.core.logging import get_logger
from app.schemas.analytics import (
    CohortAnalysisResponse,
    CohortData,
    FunnelAnalysisResponse,
    FunnelStep,
    GeographicDataPoint,
    GeographicDistributionResponse,
    RetentionAnalysisResponse,
    RetentionCohort,
    TimeSeriesDataPoint,
)

logger = get_logger(__name__)


class AnalyticsService:
    """Service for advanced analytics queries."""

    def __init__(self, clickhouse: Client):
        """Initialize service with ClickHouse client."""
        self.ch = clickhouse

    async def get_funnel_analysis(
        self,
        start_date: datetime,
        end_date: datetime
    ) -> FunnelAnalysisResponse:
        """
        Calculate conversion funnel metrics.
        
        Funnel stages:
        1. User Registration
        2. Route Search (inferred from booking creation)
        3. Booking Created
        4. Payment Success
        5. Trip Completed
        """
        try:
            # Get counts for each funnel step
            query = """
            WITH user_cohort AS (
                SELECT DISTINCT user_id
                FROM openride_analytics.user_events
                WHERE event_type = 'user.registered'
                AND event_timestamp >= {start_date:DateTime}
                AND event_timestamp <= {end_date:DateTime}
            )
            SELECT
                (SELECT count() FROM user_cohort) as registrations,
                (SELECT uniqExact(rider_id) FROM openride_analytics.booking_events 
                 WHERE rider_id IN (SELECT user_id FROM user_cohort) 
                 AND event_timestamp >= {start_date:DateTime}) as searchers,
                (SELECT uniqExact(rider_id) FROM openride_analytics.booking_events 
                 WHERE rider_id IN (SELECT user_id FROM user_cohort) 
                 AND event_type = 'booking.created' 
                 AND event_timestamp >= {start_date:DateTime}) as booking_created,
                (SELECT uniqExact(rider_id) FROM openride_analytics.payment_events 
                 WHERE rider_id IN (SELECT user_id FROM user_cohort) 
                 AND event_type = 'payment.success' 
                 AND event_timestamp >= {start_date:DateTime}) as payment_success,
                (SELECT uniqExact(rider_id) FROM openride_analytics.trip_events 
                 WHERE rider_id IN (SELECT user_id FROM user_cohort) 
                 AND event_type = 'trip.completed' 
                 AND event_timestamp >= {start_date:DateTime}) as trip_completed
            """
            
            result = self.ch.query(query, parameters={
                "start_date": start_date,
                "end_date": end_date
            })
            
            row = result.first_row if result.row_count > 0 else [0, 0, 0, 0, 0]
            registrations, searchers, bookings, payments, trips = row
            
            # Build funnel steps
            steps = [
                FunnelStep(
                    step_name="User Registration",
                    step_number=1,
                    count=registrations,
                    conversion_rate=100.0,
                    drop_off_rate=0.0
                ),
                FunnelStep(
                    step_name="Route Search",
                    step_number=2,
                    count=searchers,
                    conversion_rate=round((searchers / registrations * 100), 2) if registrations > 0 else 0.0,
                    drop_off_rate=round(((registrations - searchers) / registrations * 100), 2) if registrations > 0 else 0.0
                ),
                FunnelStep(
                    step_name="Booking Created",
                    step_number=3,
                    count=bookings,
                    conversion_rate=round((bookings / registrations * 100), 2) if registrations > 0 else 0.0,
                    drop_off_rate=round(((searchers - bookings) / searchers * 100), 2) if searchers > 0 else 0.0
                ),
                FunnelStep(
                    step_name="Payment Success",
                    step_number=4,
                    count=payments,
                    conversion_rate=round((payments / registrations * 100), 2) if registrations > 0 else 0.0,
                    drop_off_rate=round(((bookings - payments) / bookings * 100), 2) if bookings > 0 else 0.0
                ),
                FunnelStep(
                    step_name="Trip Completed",
                    step_number=5,
                    count=trips,
                    conversion_rate=round((trips / registrations * 100), 2) if registrations > 0 else 0.0,
                    drop_off_rate=round(((payments - trips) / payments * 100), 2) if payments > 0 else 0.0
                ),
            ]
            
            overall_conversion = round((trips / registrations * 100), 2) if registrations > 0 else 0.0
            
            return FunnelAnalysisResponse(
                date_range=f"{start_date.date()} to {end_date.date()}",
                funnel_steps=steps,
                total_users_entered=registrations,
                total_users_converted=trips,
                overall_conversion_rate=overall_conversion,
            )
            
        except Exception as e:
            logger.error("get_funnel_analysis_failed", error=str(e))
            raise

    async def get_cohort_analysis(
        self,
        cohort_type: str,
        start_date: datetime,
        periods: int
    ) -> CohortAnalysisResponse:
        """
        Calculate cohort retention analysis.
        
        Groups users by registration period and tracks their activity in subsequent periods.
        """
        try:
            # Determine period size
            if cohort_type == "daily":
                period_interval = "1 DAY"
                format_str = "%Y-%m-%d"
            elif cohort_type == "weekly":
                period_interval = "7 DAY"
                format_str = "%Y-W%U"
            else:  # monthly
                period_interval = "1 MONTH"
                format_str = "%Y-%m"
            
            # Build cohort query
            query = f"""
            WITH cohorts AS (
                SELECT
                    user_id,
                    toStartOfInterval(event_timestamp, INTERVAL {period_interval}) as cohort_period
                FROM openride_analytics.user_events
                WHERE event_type = 'user.registered'
                AND cohort_period >= {{start_date:DateTime}}
                AND cohort_period < {{end_date:DateTime}}
            ),
            activity AS (
                SELECT DISTINCT
                    user_id,
                    toStartOfInterval(event_timestamp, INTERVAL {period_interval}) as activity_period
                FROM openride_analytics.booking_events
                WHERE event_timestamp >= {{start_date:DateTime}}
            )
            SELECT
                cohort_period,
                activity_period,
                count(DISTINCT c.user_id) as active_users,
                (SELECT count(DISTINCT user_id) FROM cohorts WHERE cohort_period = c.cohort_period) as cohort_size
            FROM cohorts c
            LEFT JOIN activity a ON c.user_id = a.user_id
            WHERE activity_period IS NOT NULL
            GROUP BY cohort_period, activity_period
            ORDER BY cohort_period, activity_period
            """
            
            end_date = start_date + timedelta(days=periods * (7 if cohort_type == "weekly" else 30 if cohort_type == "monthly" else 1))
            
            result = self.ch.query(query, parameters={
                "start_date": start_date,
                "end_date": end_date
            })
            
            # Process cohort data
            cohorts_dict: Dict[str, CohortData] = {}
            
            for row in result.result_rows:
                cohort_period, activity_period, active_users, cohort_size = row
                cohort_key = cohort_period.strftime(format_str)
                
                if cohort_key not in cohorts_dict:
                    cohorts_dict[cohort_key] = CohortData(
                        cohort_period=cohort_key,
                        cohort_size=cohort_size,
                        retention_by_period={}
                    )
                
                # Calculate period offset
                period_offset = (activity_period - cohort_period).days
                if cohort_type == "weekly":
                    period_offset = period_offset // 7
                elif cohort_type == "monthly":
                    period_offset = period_offset // 30
                
                retention_rate = round((active_users / cohort_size * 100), 2) if cohort_size > 0 else 0.0
                cohorts_dict[cohort_key].retention_by_period[f"period_{period_offset}"] = retention_rate
            
            cohorts = list(cohorts_dict.values())
            
            return CohortAnalysisResponse(
                cohort_type=cohort_type,
                start_date=start_date,
                periods_analyzed=periods,
                cohorts=cohorts,
            )
            
        except Exception as e:
            logger.error("get_cohort_analysis_failed", error=str(e))
            raise

    async def get_retention_analysis(
        self,
        user_type: str,
        start_date: datetime,
        end_date: datetime
    ) -> RetentionAnalysisResponse:
        """
        Calculate user retention metrics (D1, D7, D30).
        """
        try:
            # Filter by user type if specified
            role_filter = ""
            if user_type == "rider":
                role_filter = "AND role = 'RIDER'"
            elif user_type == "driver":
                role_filter = "AND role = 'DRIVER'"
            
            query = f"""
            WITH new_users AS (
                SELECT
                    user_id,
                    event_timestamp as registration_date
                FROM openride_analytics.user_events
                WHERE event_type = 'user.registered'
                AND event_timestamp >= {{start_date:DateTime}}
                AND event_timestamp <= {{end_date:DateTime}}
                {role_filter}
            ),
            user_activity AS (
                SELECT DISTINCT
                    user_id,
                    toDate(event_timestamp) as activity_date
                FROM openride_analytics.booking_events
            )
            SELECT
                count(DISTINCT nu.user_id) as total_users,
                countIf(ua1.user_id IS NOT NULL) as d1_retained,
                countIf(ua7.user_id IS NOT NULL) as d7_retained,
                countIf(ua30.user_id IS NOT NULL) as d30_retained
            FROM new_users nu
            LEFT JOIN user_activity ua1 ON nu.user_id = ua1.user_id 
                AND ua1.activity_date = toDate(nu.registration_date) + INTERVAL 1 DAY
            LEFT JOIN user_activity ua7 ON nu.user_id = ua7.user_id 
                AND ua7.activity_date = toDate(nu.registration_date) + INTERVAL 7 DAY
            LEFT JOIN user_activity ua30 ON nu.user_id = ua30.user_id 
                AND ua30.activity_date = toDate(nu.registration_date) + INTERVAL 30 DAY
            """
            
            result = self.ch.query(query, parameters={
                "start_date": start_date,
                "end_date": end_date
            })
            
            row = result.first_row if result.row_count > 0 else [0, 0, 0, 0]
            total, d1, d7, d30 = row
            
            d1_rate = round((d1 / total * 100), 2) if total > 0 else 0.0
            d7_rate = round((d7 / total * 100), 2) if total > 0 else 0.0
            d30_rate = round((d30 / total * 100), 2) if total > 0 else 0.0
            
            return RetentionAnalysisResponse(
                user_type=user_type,
                date_range=f"{start_date.date()} to {end_date.date()}",
                total_users=total,
                d1_retention_rate=d1_rate,
                d7_retention_rate=d7_rate,
                d30_retention_rate=d30_rate,
                d1_retained_users=d1,
                d7_retained_users=d7,
                d30_retained_users=d30,
            )
            
        except Exception as e:
            logger.error("get_retention_analysis_failed", error=str(e))
            raise

    async def get_geographic_distribution(
        self,
        start_date: datetime,
        end_date: datetime,
        metric: str
    ) -> GeographicDistributionResponse:
        """Get geographic distribution of activity."""
        try:
            metric_mapping = {
                "bookings": "bookings",
                "revenue": "revenue",
                "users": "users"
            }
            
            metric_type = metric_mapping.get(metric, "bookings")
            
            query = """
            SELECT
                state,
                city,
                sum(metric_value) as value
            FROM openride_analytics.agg_geographic_metrics
            WHERE date >= {start_date:Date}
            AND date <= {end_date:Date}
            AND metric_type = {metric_type:String}
            GROUP BY state, city
            ORDER BY value DESC
            LIMIT 100
            """
            
            result = self.ch.query(query, parameters={
                "start_date": start_date.date(),
                "end_date": end_date.date(),
                "metric_type": metric_type
            })
            
            data_points = []
            for row in result.result_rows:
                state, city, value = row
                data_points.append(GeographicDataPoint(
                    state=state,
                    city=city,
                    value=float(value) if value else 0.0,
                ))
            
            total_value = sum(dp.value for dp in data_points)
            
            return GeographicDistributionResponse(
                metric_type=metric,
                date_range=f"{start_date.date()} to {end_date.date()}",
                data_points=data_points,
                total_value=total_value,
            )
            
        except Exception as e:
            logger.error("get_geographic_distribution_failed", error=str(e))
            raise

    async def get_time_series_trends(
        self,
        metric: str,
        start_date: datetime,
        end_date: datetime,
        granularity: str
    ) -> Dict[str, Any]:
        """Get time-series trends for any metric."""
        try:
            # Determine grouping
            if granularity == "hourly":
                time_group = "toStartOfHour(event_timestamp)"
            elif granularity == "daily":
                time_group = "toDate(event_timestamp)"
            elif granularity == "weekly":
                time_group = "toMonday(event_timestamp)"
            else:  # monthly
                time_group = "toStartOfMonth(event_timestamp)"
            
            # Build query based on metric
            table = self._get_table_for_metric(metric)
            agg_function = self._get_aggregation_for_metric(metric)
            
            query = f"""
            SELECT
                {time_group} as period,
                {agg_function} as value
            FROM {table}
            WHERE event_timestamp >= {{start_date:DateTime}}
            AND event_timestamp <= {{end_date:DateTime}}
            GROUP BY period
            ORDER BY period
            """
            
            result = self.ch.query(query, parameters={
                "start_date": start_date,
                "end_date": end_date
            })
            
            data_points = []
            for row in result.result_rows:
                period, value = row
                data_points.append(TimeSeriesDataPoint(
                    timestamp=period if isinstance(period, datetime) else datetime.combine(period, datetime.min.time()),
                    value=float(value) if value else 0.0,
                ))
            
            return {
                "metric": metric,
                "granularity": granularity,
                "date_range": f"{start_date.date()} to {end_date.date()}",
                "data_points": [dp.model_dump() for dp in data_points],
            }
            
        except Exception as e:
            logger.error("get_time_series_trends_failed", metric=metric, error=str(e))
            raise

    def _get_table_for_metric(self, metric: str) -> str:
        """Get ClickHouse table for metric."""
        mapping = {
            "bookings": "openride_analytics.booking_events",
            "payments": "openride_analytics.payment_events",
            "trips": "openride_analytics.trip_events",
            "users": "openride_analytics.user_events",
            "revenue": "openride_analytics.payment_events",
        }
        return mapping.get(metric, "openride_analytics.booking_events")

    def _get_aggregation_for_metric(self, metric: str) -> str:
        """Get aggregation function for metric."""
        if metric == "revenue":
            return "sumIf(amount, event_type = 'payment.success')"
        elif metric == "users":
            return "uniqExact(user_id)"
        else:
            return "count()"
