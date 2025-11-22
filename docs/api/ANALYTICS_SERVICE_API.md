# Analytics Service API Documentation

## Service Information
- **Service Name:** Analytics Service
- **Port:** 8097
- **Base URL:** `/v1`
- **Technology:** Python / FastAPI
- **Description:** Metrics aggregation, reporting, and business intelligence powered by ClickHouse for high-performance analytics

---

## Authentication

All endpoints require JWT authentication with appropriate role permissions.

**Request Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

---

## Field Naming Convention

**⚠️ IMPORTANT:** This is a Python service. All JSON fields use **snake_case** (not camelCase).

---

## Endpoints

### 1. Get Driver Analytics

Get comprehensive analytics for a driver.

**Endpoint:** `GET /analytics/driver/{driver_id}`

**Authentication:** Required (DRIVER - own data, ADMIN - all drivers)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| driver_id | UUID | Yes | Driver ID |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| start_date | date | No | -30 days | Start date (YYYY-MM-DD) |
| end_date | date | No | today | End date (YYYY-MM-DD) |
| granularity | string | No | day | day, week, month |

**Success Response (200 OK):**
```json
{
  "driver_id": "234e4567-e89b-12d3-a456-426614174001",
  "period": {
    "start_date": "2025-10-22",
    "end_date": "2025-11-21",
    "granularity": "day"
  },
  "summary": {
    "total_trips": 125,
    "completed_trips": 120,
    "cancelled_trips": 5,
    "total_earnings": 437500.00,
    "total_distance_km": 15625.5,
    "total_hours": 187.5,
    "average_rating": 4.8,
    "total_reviews": 98
  },
  "performance": {
    "completion_rate": 0.96,
    "cancellation_rate": 0.04,
    "on_time_rate": 0.94,
    "average_trip_duration_minutes": 90,
    "average_occupancy_rate": 0.71
  },
  "earnings_breakdown": {
    "base_fares": 393750.00,
    "tips": 12500.00,
    "bonuses": 6250.00,
    "platform_fees": -25000.00,
    "net_earnings": 437500.00
  },
  "time_series": [
    {
      "date": "2025-10-22",
      "trips": 4,
      "earnings": 14000.00,
      "distance_km": 500.0,
      "hours": 6.0
    },
    {
      "date": "2025-10-23",
      "trips": 5,
      "earnings": 17500.00,
      "distance_km": 625.0,
      "hours": 7.5
    }
  ],
  "popular_routes": [
    {
      "route_id": "456e4567-e89b-12d3-a456-426614174001",
      "route_name": "Lagos - Ibadan Express",
      "trip_count": 45,
      "total_earnings": 157500.00,
      "average_occupancy": 0.75
    }
  ],
  "ratings_distribution": {
    "5": 78,
    "4": 15,
    "3": 3,
    "2": 1,
    "1": 1
  }
}
```

**Error Responses:**

**403 Forbidden:**
```json
{
  "detail": "Not authorized to view this driver's analytics"
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8097/analytics/driver/234e4567-e89b-12d3-a456-426614174001?start_date=2025-10-22&end_date=2025-11-21&granularity=day" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 2. Get Platform Analytics

Get overall platform metrics (Admin only).

**Endpoint:** `GET /analytics/platform`

**Authentication:** Required (ADMIN role)

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| start_date | date | No | -30 days | Start date (YYYY-MM-DD) |
| end_date | date | No | today | End date (YYYY-MM-DD) |
| granularity | string | No | day | day, week, month |

**Success Response (200 OK):**
```json
{
  "period": {
    "start_date": "2025-10-22",
    "end_date": "2025-11-21",
    "granularity": "day"
  },
  "overview": {
    "total_users": 15240,
    "new_users": 450,
    "active_drivers": 1250,
    "active_riders": 8920,
    "total_bookings": 25680,
    "completed_trips": 24100,
    "cancelled_trips": 1580,
    "total_revenue": 89376000.00,
    "platform_fees": 8937600.00
  },
  "growth_metrics": {
    "user_growth_rate": 0.03,
    "booking_growth_rate": 0.15,
    "revenue_growth_rate": 0.18
  },
  "operational_metrics": {
    "average_trip_duration_minutes": 85,
    "average_trip_distance_km": 120.5,
    "average_fare": 3480.00,
    "completion_rate": 0.94,
    "cancellation_rate": 0.06,
    "average_occupancy_rate": 0.68
  },
  "time_series": [
    {
      "date": "2025-10-22",
      "bookings": 850,
      "completed_trips": 795,
      "revenue": 2769000.00,
      "new_users": 15,
      "active_users": 580
    }
  ],
  "popular_routes": [
    {
      "origin": "Lagos",
      "destination": "Ibadan",
      "booking_count": 5680,
      "average_fare": 3850.00
    }
  ],
  "top_drivers": [
    {
      "driver_id": "234e4567-e89b-12d3-a456-426614174001",
      "driver_name": "John Doe",
      "total_trips": 125,
      "total_earnings": 437500.00,
      "rating": 4.8
    }
  ]
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8097/analytics/platform?start_date=2025-10-22&end_date=2025-11-21" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 3. Get Route Analytics

Get analytics for a specific route.

**Endpoint:** `GET /analytics/route/{route_id}`

**Authentication:** Required (DRIVER - own routes, ADMIN - all routes)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| route_id | UUID | Yes | Route ID |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| start_date | date | No | -30 days | Start date (YYYY-MM-DD) |
| end_date | date | No | today | End date (YYYY-MM-DD) |

**Success Response (200 OK):**
```json
{
  "route_id": "456e4567-e89b-12d3-a456-426614174001",
  "route_name": "Lagos - Ibadan Express",
  "period": {
    "start_date": "2025-10-22",
    "end_date": "2025-11-21"
  },
  "summary": {
    "total_trips": 45,
    "completed_trips": 43,
    "cancelled_trips": 2,
    "total_bookings": 630,
    "total_revenue": 157500.00,
    "average_occupancy": 0.75,
    "average_rating": 4.8
  },
  "performance": {
    "completion_rate": 0.96,
    "on_time_rate": 0.93,
    "cancellation_rate": 0.04,
    "average_delay_minutes": 3.5
  },
  "demand_patterns": {
    "busiest_days": ["Monday", "Friday"],
    "busiest_hours": [7, 8, 17, 18],
    "average_bookings_per_trip": 14
  },
  "revenue_trend": [
    {
      "date": "2025-10-22",
      "trips": 2,
      "bookings": 28,
      "revenue": 7000.00
    }
  ],
  "popular_stops": [
    {
      "stop_name": "Ojota Bus Stop",
      "pickup_count": 320,
      "dropoff_count": 15
    },
    {
      "stop_name": "Challenge Bus Stop",
      "pickup_count": 20,
      "dropoff_count": 310
    }
  ]
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8097/analytics/route/456e4567-e89b-12d3-a456-426614174001?start_date=2025-10-22" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 4. Get Booking Analytics

Get booking-related metrics.

**Endpoint:** `GET /analytics/bookings`

**Authentication:** Required (ADMIN role)

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| start_date | date | No | -30 days | Start date (YYYY-MM-DD) |
| end_date | date | No | today | End date (YYYY-MM-DD) |
| status | string | No | null | Filter: CONFIRMED, CANCELLED, COMPLETED |
| granularity | string | No | day | day, week, month |

**Success Response (200 OK):**
```json
{
  "period": {
    "start_date": "2025-10-22",
    "end_date": "2025-11-21",
    "granularity": "day"
  },
  "summary": {
    "total_bookings": 25680,
    "confirmed_bookings": 24100,
    "cancelled_bookings": 1580,
    "completed_bookings": 22950,
    "cancellation_rate": 0.06,
    "completion_rate": 0.95
  },
  "cancellation_analysis": {
    "cancelled_by_rider": 1120,
    "cancelled_by_driver": 460,
    "cancellation_reasons": [
      {
        "reason": "Change of plans",
        "count": 520
      },
      {
        "reason": "Found alternative",
        "count": 380
      }
    ]
  },
  "booking_patterns": {
    "average_booking_lead_time_hours": 48,
    "peak_booking_hours": [10, 11, 19, 20],
    "peak_booking_days": ["Thursday", "Friday", "Sunday"]
  },
  "time_series": [
    {
      "date": "2025-10-22",
      "total": 850,
      "confirmed": 795,
      "cancelled": 55,
      "completed": 750
    }
  ]
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8097/analytics/bookings?start_date=2025-10-22&granularity=day" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 5. Get Revenue Analytics

Get detailed revenue and financial metrics.

**Endpoint:** `GET /analytics/revenue`

**Authentication:** Required (ADMIN role)

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| start_date | date | No | -30 days | Start date (YYYY-MM-DD) |
| end_date | date | No | today | End date (YYYY-MM-DD) |
| granularity | string | No | day | day, week, month |

**Success Response (200 OK):**
```json
{
  "period": {
    "start_date": "2025-10-22",
    "end_date": "2025-11-21",
    "granularity": "day"
  },
  "summary": {
    "gross_revenue": 89376000.00,
    "platform_fees": 8937600.00,
    "driver_payouts": 80438400.00,
    "net_revenue": 8937600.00,
    "refunds_issued": 125000.00
  },
  "revenue_sources": {
    "booking_fees": 8937600.00,
    "cancellation_fees": 158000.00,
    "other": 0.00
  },
  "payment_methods": {
    "korapay": {
      "transaction_count": 25680,
      "total_amount": 89376000.00,
      "success_rate": 0.98
    }
  },
  "time_series": [
    {
      "date": "2025-10-22",
      "gross_revenue": 2769000.00,
      "platform_fees": 276900.00,
      "net_revenue": 276900.00
    }
  ],
  "top_revenue_routes": [
    {
      "route_name": "Lagos - Ibadan Express",
      "revenue": 15750000.00,
      "booking_count": 4100
    }
  ]
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8097/analytics/revenue?start_date=2025-10-22&granularity=week" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 6. Get User Analytics

Get user behavior and engagement metrics.

**Endpoint:** `GET /analytics/users`

**Authentication:** Required (ADMIN role)

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| start_date | date | No | -30 days | Start date (YYYY-MM-DD) |
| end_date | date | No | today | End date (YYYY-MM-DD) |
| user_type | string | No | null | RIDER, DRIVER |

**Success Response (200 OK):**
```json
{
  "period": {
    "start_date": "2025-10-22",
    "end_date": "2025-11-21"
  },
  "overview": {
    "total_users": 15240,
    "new_users": 450,
    "active_users": 9870,
    "inactive_users": 5370,
    "retention_rate": 0.65
  },
  "user_breakdown": {
    "riders": 13450,
    "drivers": 1790,
    "verified_drivers": 1250
  },
  "engagement": {
    "daily_active_users": 3200,
    "weekly_active_users": 8500,
    "monthly_active_users": 9870,
    "average_sessions_per_user": 4.5,
    "average_session_duration_minutes": 12.3
  },
  "cohort_analysis": [
    {
      "cohort_month": "2025-10",
      "user_count": 450,
      "retention_month_1": 0.75,
      "retention_month_2": null
    }
  ],
  "time_series": [
    {
      "date": "2025-10-22",
      "new_users": 15,
      "active_users": 580,
      "dau": 3100
    }
  ]
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8097/analytics/users?start_date=2025-10-22&user_type=DRIVER" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 7. Generate Custom Report

Generate custom analytics report.

**Endpoint:** `POST /analytics/reports/custom`

**Authentication:** Required (ADMIN role)

**Request Body:**
```json
{
  "report_name": "Weekly Driver Performance",
  "metrics": ["total_trips", "earnings", "rating", "completion_rate"],
  "dimensions": ["driver_id", "route_id"],
  "filters": {
    "start_date": "2025-11-15",
    "end_date": "2025-11-21",
    "min_trips": 10
  },
  "format": "json"
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| report_name | string | Yes | Report name |
| metrics | array | Yes | Metrics to include |
| dimensions | array | Yes | Grouping dimensions |
| filters | object | No | Filter criteria |
| format | string | No | json, csv, excel |

**Available Metrics:**
- `total_trips`
- `completed_trips`
- `earnings`
- `rating`
- `completion_rate`
- `cancellation_rate`
- `on_time_rate`
- `occupancy_rate`
- `distance_km`

**Available Dimensions:**
- `driver_id`
- `route_id`
- `date`
- `day_of_week`
- `hour`

**Success Response (200 OK):**
```json
{
  "report_id": "890e4567-e89b-12d3-a456-426614174009",
  "report_name": "Weekly Driver Performance",
  "generated_at": "2025-11-21T10:00:00Z",
  "row_count": 125,
  "data": [
    {
      "driver_id": "234e4567-e89b-12d3-a456-426614174001",
      "route_id": "456e4567-e89b-12d3-a456-426614174001",
      "total_trips": 45,
      "earnings": 157500.00,
      "rating": 4.8,
      "completion_rate": 0.96
    }
  ],
  "download_url": "https://api.openride.com/downloads/reports/890e4567.json"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8097/analytics/reports/custom \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "report_name": "Weekly Driver Performance",
    "metrics": ["total_trips", "earnings", "rating"],
    "dimensions": ["driver_id"],
    "filters": {
      "start_date": "2025-11-15",
      "end_date": "2025-11-21"
    }
  }'
```

---

### 8. Export Analytics Data

Export analytics data in various formats.

**Endpoint:** `POST /analytics/export`

**Authentication:** Required (ADMIN or DRIVER for own data)

**Request Body:**
```json
{
  "analytics_type": "driver",
  "entity_id": "234e4567-e89b-12d3-a456-426614174001",
  "start_date": "2025-10-22",
  "end_date": "2025-11-21",
  "format": "csv",
  "include_raw_data": false
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| analytics_type | string | Yes | driver, route, platform, bookings |
| entity_id | UUID | Conditional | Required for driver/route types |
| start_date | date | Yes | Start date (YYYY-MM-DD) |
| end_date | date | Yes | End date (YYYY-MM-DD) |
| format | string | Yes | csv, json, excel |
| include_raw_data | boolean | No | Include granular data |

**Success Response (200 OK):**
```json
{
  "export_id": "901e4567-e89b-12d3-a456-426614174010",
  "status": "processing",
  "estimated_completion": "2025-11-21T10:05:00Z",
  "download_url": null
}
```

**Check export status:**
```bash
GET /analytics/export/{export_id}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8097/analytics/export \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "analytics_type": "driver",
    "entity_id": "234e4567-e89b-12d3-a456-426614174001",
    "start_date": "2025-10-22",
    "end_date": "2025-11-21",
    "format": "csv"
  }'
```

---

## Data Models

### Driver Analytics Schema

```typescript
interface DriverAnalytics {
  driver_id: string;                   // UUID
  period: DateRange;
  summary: DriverSummary;
  performance: PerformanceMetrics;
  earnings_breakdown: EarningsBreakdown;
  time_series: TimeSeriesData[];
  popular_routes: RouteStats[];
  ratings_distribution: Record<string, number>;
}

interface DriverSummary {
  total_trips: number;
  completed_trips: number;
  cancelled_trips: number;
  total_earnings: number;
  total_distance_km: number;
  total_hours: number;
  average_rating: number;
  total_reviews: number;
}

interface PerformanceMetrics {
  completion_rate: number;             // 0-1
  cancellation_rate: number;           // 0-1
  on_time_rate: number;                // 0-1
  average_trip_duration_minutes: number;
  average_occupancy_rate: number;      // 0-1
}

interface EarningsBreakdown {
  base_fares: number;
  tips: number;
  bonuses: number;
  platform_fees: number;               // Negative
  net_earnings: number;
}
```

---

## Rate Limiting

| Action | Limit | Window |
|--------|-------|--------|
| Get Analytics | 60 requests | Per minute |
| Generate Report | 10 requests | Per hour |
| Export Data | 5 requests | Per hour |

---

## Error Handling

### Common Error Responses

| Code | Error | Description |
|------|-------|-------------|
| 400 | Bad Request | Invalid date range or parameters |
| 401 | Unauthorized | Invalid or missing token |
| 403 | Forbidden | Not authorized to access this data |
| 404 | Not Found | Entity not found |
| 422 | Unprocessable Entity | Validation failed |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |

---

## Integration Notes for Frontend

### Analytics Dashboard

```javascript
// Driver dashboard
async function getDriverDashboard(driverId, days = 30) {
  const token = localStorage.getItem('accessToken');
  const endDate = new Date().toISOString().split('T')[0];
  const startDate = new Date(Date.now() - days * 24 * 60 * 60 * 1000)
    .toISOString().split('T')[0];
  
  const response = await fetch(
    `http://localhost:8097/analytics/driver/${driverId}?start_date=${startDate}&end_date=${endDate}&granularity=day`,
    {
      headers: { 'Authorization': `Bearer ${token}` }
    }
  );
  return response.json();
}

// Platform dashboard (Admin)
async function getPlatformDashboard() {
  const token = localStorage.getItem('accessToken');
  const response = await fetch(
    'http://localhost:8097/analytics/platform?granularity=day',
    {
      headers: { 'Authorization': `Bearer ${token}` }
    }
  );
  return response.json();
}

// Export data
async function exportAnalytics(type, entityId, startDate, endDate) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch(
    'http://localhost:8097/analytics/export',
    {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        analytics_type: type,
        entity_id: entityId,
        start_date: startDate,
        end_date: endDate,
        format: 'csv'
      })
    }
  );
  return response.json();
}
```

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-21 | Initial API documentation |
