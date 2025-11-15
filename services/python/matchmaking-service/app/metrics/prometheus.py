"""Prometheus metrics for monitoring."""

from prometheus_client import Counter, Histogram, Gauge, Info

# Request metrics
http_requests_total = Counter(
    'matchmaking_http_requests_total',
    'Total HTTP requests',
    ['method', 'endpoint', 'status']
)

http_request_duration_seconds = Histogram(
    'matchmaking_http_request_duration_seconds',
    'HTTP request duration in seconds',
    ['method', 'endpoint'],
    buckets=[0.01, 0.025, 0.05, 0.075, 0.1, 0.15, 0.2, 0.3, 0.5, 1.0, 2.0]
)

# Matching metrics
matching_duration_seconds = Histogram(
    'matchmaking_matching_duration_seconds',
    'Time to match routes',
    ['scoring_mode'],
    buckets=[0.02, 0.05, 0.075, 0.1, 0.125, 0.15, 0.2, 0.3, 0.5, 1.0]
)

matching_candidates_total = Histogram(
    'matchmaking_candidates_total',
    'Number of candidate routes found',
    buckets=[0, 5, 10, 20, 30, 50, 75, 100, 150, 200]
)

matching_results_total = Histogram(
    'matchmaking_results_total',
    'Number of matching routes returned',
    buckets=[0, 1, 3, 5, 10, 15, 20, 30, 50]
)

# Cache metrics
cache_hits_total = Counter(
    'matchmaking_cache_hits_total',
    'Total cache hits',
    ['cache_type']  # route, driver, hub
)

cache_misses_total = Counter(
    'matchmaking_cache_misses_total',
    'Total cache misses',
    ['cache_type']
)

cache_operation_duration_seconds = Histogram(
    'matchmaking_cache_operation_duration_seconds',
    'Cache operation duration',
    ['cache_type', 'operation'],  # get, set, delete
    buckets=[0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1]
)

# Database metrics
db_query_duration_seconds = Histogram(
    'matchmaking_db_query_duration_seconds',
    'Database query duration',
    ['query_type'],  # route_lookup, hub_discovery, driver_stats, etc.
    buckets=[0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.15, 0.2, 0.5]
)

db_pool_connections = Gauge(
    'matchmaking_db_pool_connections',
    'Number of database pool connections',
    ['pool', 'state']  # primary/replica, checked_in/checked_out/overflow
)

db_pool_size = Gauge(
    'matchmaking_db_pool_size',
    'Database pool size',
    ['pool']
)

# Feature extraction metrics (Phase 4)
feature_extraction_duration_seconds = Histogram(
    'matchmaking_feature_extraction_duration_seconds',
    'Feature extraction duration',
    buckets=[0.005, 0.01, 0.015, 0.02, 0.03, 0.05, 0.1]
)

ml_scoring_duration_seconds = Histogram(
    'matchmaking_ml_scoring_duration_seconds',
    'ML scoring duration',
    ['mode'],  # ml-based, hybrid
    buckets=[0.001, 0.002, 0.005, 0.01, 0.02, 0.05]
)

# Service health
service_info = Info(
    'matchmaking_service',
    'Matchmaking service information'
)

service_up = Gauge(
    'matchmaking_service_up',
    'Service availability (1 = up, 0 = down)'
)

# Performance target violations
performance_target_violations_total = Counter(
    'matchmaking_performance_target_violations_total',
    'Number of requests exceeding performance target',
    ['endpoint']
)

# Hub compatibility (Phase 3)
hub_filtering_duration_seconds = Histogram(
    'matchmaking_hub_filtering_duration_seconds',
    'Hub compatibility filtering duration',
    buckets=[0.001, 0.002, 0.005, 0.01, 0.02, 0.05]
)

stop_validation_duration_seconds = Histogram(
    'matchmaking_stop_validation_duration_seconds',
    'Stop sequence validation duration',
    buckets=[0.001, 0.002, 0.005, 0.01, 0.02, 0.05]
)

# Initialize service info
service_info.info({
    'version': '1.0.0',
    'service': 'matchmaking-service',
    'phase': '5'
})

# Set service as up
service_up.set(1)
