"""Time window optimization service for route matching."""

import logging
from datetime import datetime, time, timedelta
from typing import Optional

from app.core.config import get_settings

settings = get_settings()
logger = logging.getLogger(__name__)


class TimeWindowOptimizer:
    """Service for optimizing time window matching."""

    def __init__(self):
        """Initialize time window optimizer."""
        pass

    def calculate_time_match_score(
        self,
        route_time: time,
        desired_time: time,
        window_minutes: int = 15,
    ) -> tuple[float, int]:
        """
        Calculate time match score with optimized window.

        Scoring:
        - Exact match (0 min diff): 1.0
        - Within 5 min: 0.9-1.0 (linear decay)
        - Within 10 min: 0.7-0.9 (linear decay)
        - Within 15 min: 0.5-0.7 (linear decay)
        - Beyond 15 min: 0.0

        Args:
            route_time: Route departure time
            desired_time: Rider's desired time
            window_minutes: Maximum acceptable time difference

        Returns:
            tuple: (score: float 0-1, time_diff_minutes: int)
        """
        # Convert times to minutes since midnight
        route_minutes = route_time.hour * 60 + route_time.minute
        desired_minutes = desired_time.hour * 60 + desired_time.minute

        # Calculate difference (handle wrap-around at midnight)
        diff = abs(route_minutes - desired_minutes)

        # Handle midnight wrap-around (e.g., 23:50 to 00:10 = 20 min, not 1430 min)
        if diff > 720:  # More than 12 hours
            diff = 1440 - diff  # Subtract from 24 hours

        # Calculate score based on time difference
        if diff == 0:
            score = 1.0
        elif diff <= 5:
            # Within 5 minutes: 0.9-1.0
            score = 1.0 - (diff / 5.0) * 0.1
        elif diff <= 10:
            # Within 10 minutes: 0.7-0.9
            score = 0.9 - ((diff - 5) / 5.0) * 0.2
        elif diff <= window_minutes:
            # Within window: 0.5-0.7
            score = 0.7 - ((diff - 10) / (window_minutes - 10)) * 0.2
        else:
            # Beyond window: 0.0
            score = 0.0

        return score, diff

    def calculate_optimal_window(
        self,
        current_time: datetime,
        desired_time: time,
        route_departure: time,
    ) -> dict:
        """
        Calculate optimal time window considering current time.

        If rider is searching close to departure time, adjust window.

        Args:
            current_time: Current datetime
            desired_time: Rider's desired departure time
            route_departure: Route's departure time

        Returns:
            dict: Window optimization details
        """
        # Calculate time until departure
        today = current_time.date()
        desired_datetime = datetime.combine(today, desired_time)
        route_datetime = datetime.combine(today, route_departure)

        # Handle next-day scenarios
        if route_datetime < current_time:
            route_datetime += timedelta(days=1)
        if desired_datetime < current_time:
            desired_datetime += timedelta(days=1)

        time_until_departure = (route_datetime - current_time).total_seconds() / 60

        # Determine urgency level
        if time_until_departure < 10:
            urgency = "critical"  # Leaving very soon
            window_adjustment = 1.5  # Expand window by 50%
        elif time_until_departure < 30:
            urgency = "high"  # Leaving soon
            window_adjustment = 1.3  # Expand window by 30%
        elif time_until_departure < 60:
            urgency = "medium"  # Leaving within an hour
            window_adjustment = 1.1  # Expand window by 10%
        else:
            urgency = "low"  # Plenty of time
            window_adjustment = 1.0  # No adjustment

        return {
            "urgency": urgency,
            "time_until_departure_minutes": time_until_departure,
            "window_adjustment": window_adjustment,
            "recommended_window_minutes": int(
                settings.time_window_minutes * window_adjustment
            ),
        }

    def is_within_booking_window(
        self,
        current_time: datetime,
        route_departure: time,
        min_advance_minutes: int = 5,
        max_advance_hours: int = 24,
    ) -> tuple[bool, str]:
        """
        Check if route can be booked within booking window.

        Args:
            current_time: Current datetime
            route_departure: Route departure time
            min_advance_minutes: Minimum minutes before departure
            max_advance_hours: Maximum hours in advance

        Returns:
            tuple: (is_bookable: bool, reason: str)
        """
        today = current_time.date()
        route_datetime = datetime.combine(today, route_departure)

        # Handle next-day routes
        if route_datetime < current_time:
            route_datetime += timedelta(days=1)

        time_diff = (route_datetime - current_time).total_seconds()
        time_diff_minutes = time_diff / 60
        time_diff_hours = time_diff / 3600

        # Too soon
        if time_diff_minutes < min_advance_minutes:
            return False, f"Route departs in {int(time_diff_minutes)} min (minimum {min_advance_minutes} min required)"

        # Too far in advance
        if time_diff_hours > max_advance_hours:
            return False, f"Route departs in {int(time_diff_hours)} hours (maximum {max_advance_hours} hours allowed)"

        return True, "Bookable"

    def calculate_departure_flexibility_score(
        self,
        route_time: time,
        desired_time: time,
        rider_flexibility_minutes: int = 30,
    ) -> float:
        """
        Calculate score based on rider's time flexibility.

        If rider is flexible, routes slightly outside preferred time get higher scores.

        Args:
            route_time: Route departure time
            desired_time: Rider's desired time
            rider_flexibility_minutes: Rider's flexibility window

        Returns:
            float: Flexibility score 0-1
        """
        # Convert to minutes
        route_minutes = route_time.hour * 60 + route_time.minute
        desired_minutes = desired_time.hour * 60 + desired_time.minute

        diff = abs(route_minutes - desired_minutes)

        # Handle midnight wrap-around
        if diff > 720:
            diff = 1440 - diff

        # Score based on flexibility window
        if diff <= rider_flexibility_minutes:
            # Linear decay within flexibility window
            score = 1.0 - (diff / rider_flexibility_minutes) * 0.5
        else:
            # Outside flexibility window
            score = 0.0

        return score

    def get_peak_hours_multiplier(self, departure_time: time) -> float:
        """
        Get score multiplier for peak hours.

        Peak hours (higher demand, better availability):
        - Morning: 6:00-9:00
        - Evening: 17:00-20:00

        Args:
            departure_time: Route departure time

        Returns:
            float: Peak hours multiplier (1.0-1.2)
        """
        hour = departure_time.hour

        # Morning peak
        if 6 <= hour < 9:
            return 1.2

        # Evening peak
        if 17 <= hour < 20:
            return 1.2

        # Off-peak
        return 1.0

    def calculate_wait_time_penalty(
        self,
        current_time: datetime,
        route_departure: time,
    ) -> float:
        """
        Calculate penalty for long wait times.

        Riders prefer routes leaving soon over long waits.

        Args:
            current_time: Current datetime
            route_departure: Route departure time

        Returns:
            float: Penalty 0-1 (0 = high penalty, 1 = no penalty)
        """
        today = current_time.date()
        route_datetime = datetime.combine(today, route_departure)

        if route_datetime < current_time:
            route_datetime += timedelta(days=1)

        wait_minutes = (route_datetime - current_time).total_seconds() / 60

        # Optimal wait: 10-30 minutes (no penalty)
        if 10 <= wait_minutes <= 30:
            return 1.0

        # Very short wait: 0-10 minutes (slight penalty for rush)
        if wait_minutes < 10:
            return 0.9 + (wait_minutes / 10.0) * 0.1

        # Long wait: 30-120 minutes (linear penalty)
        if wait_minutes <= 120:
            return 1.0 - ((wait_minutes - 30) / 90.0) * 0.3

        # Very long wait: >120 minutes (high penalty)
        return 0.7
