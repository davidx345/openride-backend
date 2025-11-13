"""Geospatial utility functions using PostGIS and Shapely."""

import math
from decimal import Decimal

from shapely.geometry import Point


def calculate_bearing(
    lat1: float | Decimal, lon1: float | Decimal, lat2: float | Decimal, lon2: float | Decimal
) -> float:
    """
    Calculate bearing between two points in degrees.

    Args:
        lat1: Start latitude
        lon1: Start longitude
        lat2: End latitude
        lon2: End longitude

    Returns:
        float: Bearing in degrees (0-360)
    """
    lat1_rad = math.radians(float(lat1))
    lat2_rad = math.radians(float(lat2))
    lon1_rad = math.radians(float(lon1))
    lon2_rad = math.radians(float(lon2))

    dlon = lon2_rad - lon1_rad

    y = math.sin(dlon) * math.cos(lat2_rad)
    x = math.cos(lat1_rad) * math.sin(lat2_rad) - math.sin(lat1_rad) * math.cos(
        lat2_rad
    ) * math.cos(dlon)

    bearing_rad = math.atan2(y, x)
    bearing_deg = math.degrees(bearing_rad)

    # Normalize to 0-360
    return (bearing_deg + 360) % 360


def calculate_distance_haversine(
    lat1: float | Decimal, lon1: float | Decimal, lat2: float | Decimal, lon2: float | Decimal
) -> float:
    """
    Calculate great-circle distance between two points using Haversine formula.

    Args:
        lat1: Start latitude
        lon1: Start longitude
        lat2: End latitude
        lon2: End longitude

    Returns:
        float: Distance in meters
    """
    R = 6371000  # Earth radius in meters

    lat1_rad = math.radians(float(lat1))
    lat2_rad = math.radians(float(lat2))
    dlat = math.radians(float(lat2) - float(lat1))
    dlon = math.radians(float(lon2) - float(lon1))

    a = math.sin(dlat / 2) ** 2 + math.cos(lat1_rad) * math.cos(lat2_rad) * math.sin(dlon / 2) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

    return R * c


def check_directionality(
    route_start_lat: Decimal,
    route_start_lon: Decimal,
    route_end_lat: Decimal,
    route_end_lon: Decimal,
    rider_origin_lat: float,
    rider_origin_lon: float,
    rider_dest_lat: float,
    rider_dest_lon: float,
    tolerance_degrees: float = 45.0,
) -> bool:
    """
    Check if rider's journey is roughly aligned with route direction.

    Compares bearing from route start to end with bearing from rider origin to destination.

    Args:
        route_start_lat: Route start latitude
        route_start_lon: Route start longitude
        route_end_lat: Route end latitude
        route_end_lon: Route end longitude
        rider_origin_lat: Rider origin latitude
        rider_origin_lon: Rider origin longitude
        rider_dest_lat: Rider destination latitude
        rider_dest_lon: Rider destination longitude
        tolerance_degrees: Acceptable bearing difference in degrees

    Returns:
        bool: True if directions are aligned within tolerance
    """
    route_bearing = calculate_bearing(
        route_start_lat, route_start_lon, route_end_lat, route_end_lon
    )
    rider_bearing = calculate_bearing(
        rider_origin_lat, rider_origin_lon, rider_dest_lat, rider_dest_lon
    )

    # Calculate absolute difference
    diff = abs(route_bearing - rider_bearing)

    # Handle wrap-around at 360/0 degrees
    if diff > 180:
        diff = 360 - diff

    return diff <= tolerance_degrees


def find_closest_stop_index(
    stops_lat_lon: list[tuple[Decimal, Decimal]],
    target_lat: float,
    target_lon: float,
) -> int | None:
    """
    Find index of closest stop to target coordinates.

    Args:
        stops_lat_lon: List of (lat, lon) tuples for stops
        target_lat: Target latitude
        target_lon: Target longitude

    Returns:
        int | None: Index of closest stop or None if no stops
    """
    if not stops_lat_lon:
        return None

    min_distance = float("inf")
    closest_index = 0

    for i, (lat, lon) in enumerate(stops_lat_lon):
        distance = calculate_distance_haversine(lat, lon, target_lat, target_lon)
        if distance < min_distance:
            min_distance = distance
            closest_index = i

    return closest_index


def point_to_line_distance(
    point_lat: float,
    point_lon: float,
    line_start_lat: Decimal,
    line_start_lon: Decimal,
    line_end_lat: Decimal,
    line_end_lon: Decimal,
) -> float:
    """
    Calculate perpendicular distance from point to line segment.

    Uses Shapely for accurate geometric calculation.

    Args:
        point_lat: Point latitude
        point_lon: Point longitude
        line_start_lat: Line start latitude
        line_start_lon: Line start longitude
        line_end_lat: Line end latitude
        line_end_lon: Line end longitude

    Returns:
        float: Distance in degrees (approximate)
    """
    from shapely.geometry import LineString

    point = Point(point_lon, point_lat)
    line = LineString(
        [(float(line_start_lon), float(line_start_lat)), (float(line_end_lon), float(line_end_lat))]
    )

    return point.distance(line)
