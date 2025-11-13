# Load Testing Guide for Booking Service

## Prerequisites

- Apache JMeter 5.6+
- Running booking service
- PostgreSQL, Redis, Kafka infrastructure

## Performance Targets

| Metric | Target |
|--------|--------|
| P50 Latency | < 100ms |
| P95 Latency | < 200ms |
| P99 Latency | < 500ms |
| Throughput | > 1000 bookings/sec |
| Error Rate | < 0.1% |

## JMeter Test Plan

### 1. Setup

```bash
# Download JMeter
wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.6.3.tgz
tar -xzf apache-jmeter-5.6.3.tgz
cd apache-jmeter-5.6.3

# Install plugins
cd lib/ext
wget https://repo1.maven.org/maven2/kg/apc/jmeter-plugins-manager/1.9/jmeter-plugins-manager-1.9.jar
```

### 2. Test Scenarios

#### Scenario 1: Create Booking (70% of traffic)

- **Threads**: 100
- **Ramp-up**: 30 seconds
- **Duration**: 5 minutes
- **Endpoint**: POST /api/v1/bookings

**Request Body**:
```json
{
  "routeId": "${routeId}",
  "originStopId": "STOP1",
  "destinationStopId": "STOP2",
  "travelDate": "${travelDate}",
  "seatsBooked": 2
}
```

#### Scenario 2: Get Bookings (20% of traffic)

- **Threads**: 30
- **Ramp-up**: 20 seconds
- **Duration**: 5 minutes
- **Endpoint**: GET /api/v1/bookings

#### Scenario 3: Cancel Booking (10% of traffic)

- **Threads**: 10
- **Ramp-up**: 15 seconds
- **Duration**: 5 minutes
- **Endpoint**: POST /api/v1/bookings/{id}/cancel

### 3. Run Tests

```bash
# Run in non-GUI mode for better performance
./bin/jmeter -n -t booking-service-load-test.jmx \
  -l results.jtl \
  -e -o ./report

# Generate HTML report
./bin/jmeter -g results.jtl -o ./html-report
```

### 4. Analyze Results

```bash
# View summary
tail -f jmeter.log

# Check P95/P99 latency
awk -F',' '{print $2}' results.jtl | sort -n | awk 'BEGIN{c=0} {total[c]=$1; c++} END{print "P95:", total[int(c*0.95)], "P99:", total[int(c*0.99)]}'
```

## Gatling Alternative

### Install Gatling

```bash
wget https://repo1.maven.org/maven2/io/gatling/highcharts/gatling-charts-highcharts-bundle/3.10.3/gatling-charts-highcharts-bundle-3.10.3.zip
unzip gatling-charts-highcharts-bundle-3.10.3.zip
cd gatling-charts-highcharts-bundle-3.10.3
```

### Create Gatling Simulation

```scala
package bookingservice

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class BookingServiceLoadTest extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8083/api")
    .acceptHeader("application/json")
    .authorizationHeader("Bearer ${jwtToken}")

  val createBooking = scenario("Create Booking")
    .exec(http("Create Booking")
      .post("/v1/bookings")
      .body(StringBody("""{
        "routeId": "c0a80121-7ac0-190b-817a-c08ab0a12345",
        "originStopId": "STOP1",
        "destinationStopId": "STOP2",
        "travelDate": "2024-12-15",
        "seatsBooked": 2
      }""")).asJson
      .check(status.is(201))
    )

  setUp(
    createBooking.inject(
      rampUsersPerSec(10) to (100) during (30 seconds),
      constantUsersPerSec(100) during (5 minutes)
    )
  ).protocols(httpProtocol)
}
```

### Run Gatling

```bash
./bin/gatling.sh -s bookingservice.BookingServiceLoadTest
```

## Monitoring During Load Tests

### 1. Application Metrics

```bash
# View Prometheus metrics
curl http://localhost:8083/api/actuator/prometheus | grep booking

# Key metrics:
# - http_server_requests_seconds
# - booking_creation_total
# - distributed_lock_wait_time_seconds
# - seat_hold_duration_seconds
```

### 2. Database Performance

```sql
-- Active connections
SELECT count(*) FROM pg_stat_activity WHERE datname = 'openride_booking';

-- Slow queries
SELECT pid, now() - query_start as duration, query
FROM pg_stat_activity
WHERE state = 'active' AND now() - query_start > interval '1 second';

-- Lock contention
SELECT * FROM pg_locks WHERE NOT granted;
```

### 3. Redis Performance

```bash
# Monitor Redis
redis-cli --stat

# Check key count
redis-cli DBSIZE

# Monitor commands
redis-cli MONITOR
```

### 4. JVM Metrics

```bash
# Heap usage
curl http://localhost:8083/api/actuator/metrics/jvm.memory.used | jq

# GC stats
curl http://localhost:8083/api/actuator/metrics/jvm.gc.pause | jq

# Thread count
curl http://localhost:8083/api/actuator/metrics/jvm.threads.live | jq
```

## Optimization Checklist

### Database
- [ ] All indexes created and used
- [ ] Connection pool sized correctly (50 max, 10 min idle)
- [ ] Statement caching enabled
- [ ] Query execution plans verified with EXPLAIN ANALYZE
- [ ] Table statistics up to date (ANALYZE)

### Redis
- [ ] Connection pool sized correctly (50 max, 10 min idle)
- [ ] TTL set on all seat holds
- [ ] Pipeline commands where possible
- [ ] Monitor memory usage

### Application
- [ ] Distributed lock timeout tuned (5s wait, 10s lease)
- [ ] Async event publishing enabled
- [ ] Thread pool sized correctly
- [ ] No N+1 queries
- [ ] Proper exception handling

### Infrastructure
- [ ] Sufficient CPU/memory allocated
- [ ] Network latency < 1ms between services
- [ ] Kafka partitions = thread count
- [ ] Auto-scaling configured

## Expected Results

After optimizations, you should see:

- **P50 Latency**: 50-80ms
- **P95 Latency**: 120-180ms
- **P99 Latency**: 250-400ms
- **Throughput**: 1000-1500 bookings/sec
- **Error Rate**: < 0.05%
- **Database CPU**: < 70%
- **Application CPU**: < 60%
- **Redis CPU**: < 30%

## Troubleshooting

### High Latency
- Check database slow query log
- Verify index usage with EXPLAIN
- Check distributed lock wait times
- Monitor GC pauses

### Low Throughput
- Increase connection pool size
- Scale horizontally (add instances)
- Check Redis performance
- Verify Kafka consumer lag

### High Error Rate
- Check lock acquisition failures
- Verify seat availability logic
- Monitor database connection pool exhaustion
- Check for deadlocks

## Continuous Performance Testing

Integrate load tests into CI/CD:

```yaml
# .github/workflows/performance-test.yml
name: Performance Test

on:
  pull_request:
    branches: [main]

jobs:
  load-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Start infrastructure
        run: docker-compose up -d
      - name: Run load test
        run: ./run-gatling.sh
      - name: Check P95 < 200ms
        run: |
          P95=$(cat target/gatling/*/simulation.log | awk ...)
          if [ $P95 -gt 200 ]; then exit 1; fi
```
