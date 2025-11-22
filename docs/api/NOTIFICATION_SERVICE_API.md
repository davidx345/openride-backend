# Notification Service API Documentation

## Service Information
- **Service Name:** Notification Service
- **Port:** 8095
- **Base URL:** `/v1`
- **Technology:** Python / FastAPI
- **Description:** Multi-channel notification delivery system supporting push notifications, SMS, email, and in-app messages

---

## Authentication

All endpoints require JWT authentication.

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

### 1. Send Notification

Send a notification via one or more channels.

**Endpoint:** `POST /notifications/send`

**Authentication:** Required (appropriate role based on notification type)

**Request Body:**
```json
{
  "recipient_id": "345e4567-e89b-12d3-a456-426614174002",
  "notification_type": "BOOKING_CONFIRMED",
  "channels": ["push", "sms"],
  "priority": "high",
  "title": "Booking Confirmed",
  "body": "Your booking for Lagos - Ibadan Express on Nov 25 at 8:00 AM has been confirmed.",
  "data": {
    "booking_id": "678e4567-e89b-12d3-a456-426614174011",
    "route_name": "Lagos - Ibadan Express",
    "departure_time": "2025-11-25T08:00:00Z"
  },
  "action_url": "/bookings/678e4567-e89b-12d3-a456-426614174011",
  "scheduled_for": null,
  "ttl_seconds": 86400
}
```

**Request Fields:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| recipient_id | UUID | Yes | Valid user | User to notify |
| notification_type | string | Yes | Enum | Type of notification |
| channels | array | Yes | 1-4 items | Delivery channels |
| priority | string | No | Enum | low, normal, high, urgent |
| title | string | Yes | 1-100 chars | Notification title |
| body | string | Yes | 1-500 chars | Notification body |
| data | object | No | - | Additional payload data |
| action_url | string | No | Max 500 chars | Deep link or URL |
| scheduled_for | datetime | No | ISO 8601 | Schedule for later |
| ttl_seconds | integer | No | 60-604800 | Time to live (default: 86400) |

**Notification Types:**

| Type | Description | Typical Channels |
|------|-------------|------------------|
| BOOKING_CONFIRMED | Booking confirmation | push, sms, email |
| BOOKING_CANCELLED | Booking cancellation | push, sms |
| TRIP_STARTING | Trip about to start | push, sms |
| TRIP_DELAYED | Trip delayed | push, sms |
| TRIP_CANCELLED | Trip cancelled by driver | push, sms, email |
| PAYMENT_SUCCESS | Payment successful | push, email |
| PAYMENT_FAILED | Payment failed | push, sms, email |
| PAYOUT_PROCESSED | Payout completed | push, email |
| KYC_APPROVED | KYC verification approved | push, email |
| KYC_REJECTED | KYC verification rejected | push, email |
| ROUTE_APPROVED | Route approved | push |
| REVIEW_RECEIVED | New review received | push |
| PROMOTIONAL | Marketing message | push, email |

**Channels:**

| Channel | Description | Requirements |
|---------|-------------|--------------|
| push | Push notification | Device token registered |
| sms | SMS message | Phone number verified |
| email | Email message | Email verified |
| in_app | In-app notification | Always available |

**Priority Levels:**

| Priority | Description | Delivery |
|----------|-------------|----------|
| low | Non-urgent updates | Best effort |
| normal | Standard notifications | Standard queue |
| high | Important updates | Priority queue |
| urgent | Critical alerts | Immediate delivery |

**Success Response (201 Created):**
```json
{
  "notification_id": "912e4567-e89b-12d3-a456-426614174012",
  "recipient_id": "345e4567-e89b-12d3-a456-426614174002",
  "notification_type": "BOOKING_CONFIRMED",
  "status": "queued",
  "channels": {
    "push": {
      "status": "queued",
      "message_id": null
    },
    "sms": {
      "status": "queued",
      "message_id": null
    }
  },
  "created_at": "2025-11-21T10:00:00Z",
  "scheduled_for": null,
  "estimated_delivery": "2025-11-21T10:00:05Z"
}
```

**Error Responses:**

**400 Bad Request:**
```json
{
  "detail": "Invalid channel: user has no verified phone number for SMS"
}
```

**429 Too Many Requests:**
```json
{
  "detail": "Notification rate limit exceeded for user"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8095/notifications/send \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "recipient_id": "345e4567-e89b-12d3-a456-426614174002",
    "notification_type": "BOOKING_CONFIRMED",
    "channels": ["push", "sms"],
    "priority": "high",
    "title": "Booking Confirmed",
    "body": "Your booking has been confirmed."
  }'
```

---

### 2. Send Bulk Notifications

Send notifications to multiple recipients.

**Endpoint:** `POST /notifications/send-bulk`

**Authentication:** Required (ADMIN role)

**Request Body:**
```json
{
  "recipient_ids": [
    "345e4567-e89b-12d3-a456-426614174002",
    "345e4567-e89b-12d3-a456-426614174003"
  ],
  "notification_type": "PROMOTIONAL",
  "channels": ["push", "email"],
  "priority": "normal",
  "title": "Special Offer: 20% Off Your Next Ride",
  "body": "Book your next trip this week and get 20% off!",
  "data": {
    "promo_code": "RIDE20",
    "expires_at": "2025-11-28T23:59:59Z"
  },
  "action_url": "/search",
  "batch_size": 100,
  "rate_limit_per_second": 10
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| recipient_ids | array | Yes | User IDs (max 10,000) |
| notification_type | string | Yes | Notification type |
| channels | array | Yes | Delivery channels |
| priority | string | No | Priority level |
| title | string | Yes | Notification title |
| body | string | Yes | Notification body |
| data | object | No | Additional data |
| action_url | string | No | Action URL |
| batch_size | integer | No | Batch size (default: 100) |
| rate_limit_per_second | integer | No | Rate limit (default: 10) |

**Success Response (202 Accepted):**
```json
{
  "batch_id": "023e4567-e89b-12d3-a456-426614174013",
  "total_recipients": 2,
  "status": "processing",
  "estimated_completion": "2025-11-21T10:05:00Z",
  "created_at": "2025-11-21T10:00:00Z"
}
```

**Check batch status:**
```bash
GET /notifications/batches/{batch_id}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8095/notifications/send-bulk \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "recipient_ids": ["345e4567-e89b-12d3-a456-426614174002"],
    "notification_type": "PROMOTIONAL",
    "channels": ["push"],
    "title": "Special Offer",
    "body": "Get 20% off your next ride!"
  }'
```

---

### 3. Get Notification Status

Get the delivery status of a notification.

**Endpoint:** `GET /notifications/{notification_id}`

**Authentication:** Required (sender or recipient)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| notification_id | UUID | Yes | Notification ID |

**Success Response (200 OK):**
```json
{
  "id": "912e4567-e89b-12d3-a456-426614174012",
  "recipient_id": "345e4567-e89b-12d3-a456-426614174002",
  "notification_type": "BOOKING_CONFIRMED",
  "priority": "high",
  "status": "delivered",
  "channels": {
    "push": {
      "status": "delivered",
      "message_id": "fcm-msg-abc123",
      "sent_at": "2025-11-21T10:00:05Z",
      "delivered_at": "2025-11-21T10:00:06Z",
      "error": null
    },
    "sms": {
      "status": "delivered",
      "message_id": "sms-msg-xyz789",
      "sent_at": "2025-11-21T10:00:05Z",
      "delivered_at": "2025-11-21T10:00:10Z",
      "error": null
    }
  },
  "title": "Booking Confirmed",
  "body": "Your booking has been confirmed.",
  "created_at": "2025-11-21T10:00:00Z",
  "read_at": "2025-11-21T10:05:00Z"
}
```

**Channel Status Values:**

| Status | Description |
|--------|-------------|
| queued | Waiting to be sent |
| sent | Sent to provider |
| delivered | Delivered to device |
| failed | Delivery failed |
| bounced | Bounced (email) |
| read | Read by recipient |

**Example cURL:**
```bash
curl -X GET http://localhost:8095/notifications/912e4567-e89b-12d3-a456-426614174012 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 4. List User Notifications

Get notifications for current user.

**Endpoint:** `GET /notifications`

**Authentication:** Required

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| unread_only | boolean | No | false | Only unread notifications |
| notification_type | string | No | null | Filter by type |
| limit | integer | No | 20 | Max results (1-100) |
| offset | integer | No | 0 | Pagination offset |

**Success Response (200 OK):**
```json
{
  "notifications": [
    {
      "id": "912e4567-e89b-12d3-a456-426614174012",
      "notification_type": "BOOKING_CONFIRMED",
      "priority": "high",
      "title": "Booking Confirmed",
      "body": "Your booking has been confirmed.",
      "data": {
        "booking_id": "678e4567-e89b-12d3-a456-426614174011"
      },
      "action_url": "/bookings/678e4567-e89b-12d3-a456-426614174011",
      "created_at": "2025-11-21T10:00:00Z",
      "read_at": null,
      "is_read": false
    }
  ],
  "total_count": 1,
  "unread_count": 1,
  "pagination": {
    "limit": 20,
    "offset": 0,
    "has_more": false
  }
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8095/notifications?unread_only=true&limit=20" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 5. Mark Notification as Read

Mark notification as read.

**Endpoint:** `PATCH /notifications/{notification_id}/read`

**Authentication:** Required (recipient only)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| notification_id | UUID | Yes | Notification ID |

**Success Response (200 OK):**
```json
{
  "id": "912e4567-e89b-12d3-a456-426614174012",
  "is_read": true,
  "read_at": "2025-11-21T10:30:00Z"
}
```

**Example cURL:**
```bash
curl -X PATCH http://localhost:8095/notifications/912e4567-e89b-12d3-a456-426614174012/read \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 6. Mark All Notifications as Read

Mark all user's notifications as read.

**Endpoint:** `PATCH /notifications/read-all`

**Authentication:** Required

**Success Response (200 OK):**
```json
{
  "marked_as_read": 15,
  "timestamp": "2025-11-21T10:30:00Z"
}
```

**Example cURL:**
```bash
curl -X PATCH http://localhost:8095/notifications/read-all \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 7. Delete Notification

Delete notification.

**Endpoint:** `DELETE /notifications/{notification_id}`

**Authentication:** Required (recipient only)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| notification_id | UUID | Yes | Notification ID to delete |

**Success Response (204 No Content):**

No response body.

**Example cURL:**
```bash
curl -X DELETE http://localhost:8095/notifications/912e4567-e89b-12d3-a456-426614174012 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 8. Register Device Token

Register device token for push notifications.

**Endpoint:** `POST /notifications/devices`

**Authentication:** Required

**Request Body:**
```json
{
  "device_token": "fcm-device-token-abc123xyz789",
  "device_type": "android",
  "device_name": "Samsung Galaxy S21"
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| device_token | string | Yes | FCM/APNS device token |
| device_type | string | Yes | android, ios, web |
| device_name | string | No | Human-readable device name |

**Success Response (201 Created):**
```json
{
  "device_id": "134e4567-e89b-12d3-a456-426614174014",
  "user_id": "345e4567-e89b-12d3-a456-426614174002",
  "device_token": "fcm-device-token-abc123xyz789",
  "device_type": "android",
  "device_name": "Samsung Galaxy S21",
  "registered_at": "2025-11-21T10:00:00Z",
  "last_active": "2025-11-21T10:00:00Z"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8095/notifications/devices \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "device_token": "fcm-device-token-abc123xyz789",
    "device_type": "android",
    "device_name": "Samsung Galaxy S21"
  }'
```

---

### 9. Unregister Device Token

Remove device token.

**Endpoint:** `DELETE /notifications/devices/{device_id}`

**Authentication:** Required

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| device_id | UUID | Yes | Device ID to remove |

**Success Response (204 No Content):**

No response body.

**Example cURL:**
```bash
curl -X DELETE http://localhost:8095/notifications/devices/134e4567-e89b-12d3-a456-426614174014 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 10. Update Notification Preferences

Update user's notification preferences.

**Endpoint:** `PUT /notifications/preferences`

**Authentication:** Required

**Request Body:**
```json
{
  "channels": {
    "push": {
      "enabled": true,
      "types": ["BOOKING_CONFIRMED", "TRIP_STARTING", "PAYMENT_SUCCESS"]
    },
    "sms": {
      "enabled": true,
      "types": ["BOOKING_CONFIRMED", "TRIP_CANCELLED"]
    },
    "email": {
      "enabled": true,
      "types": ["BOOKING_CONFIRMED", "PAYOUT_PROCESSED", "PROMOTIONAL"]
    },
    "in_app": {
      "enabled": true,
      "types": ["*"]
    }
  },
  "quiet_hours": {
    "enabled": true,
    "start_time": "22:00:00",
    "end_time": "07:00:00",
    "timezone": "Africa/Lagos"
  }
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| channels | object | Yes | Channel preferences |
| quiet_hours | object | No | Quiet hours configuration |

**Success Response (200 OK):**
```json
{
  "user_id": "345e4567-e89b-12d3-a456-426614174002",
  "channels": {
    "push": {
      "enabled": true,
      "types": ["BOOKING_CONFIRMED", "TRIP_STARTING", "PAYMENT_SUCCESS"]
    },
    "sms": {
      "enabled": true,
      "types": ["BOOKING_CONFIRMED", "TRIP_CANCELLED"]
    },
    "email": {
      "enabled": true,
      "types": ["BOOKING_CONFIRMED", "PAYOUT_PROCESSED", "PROMOTIONAL"]
    },
    "in_app": {
      "enabled": true,
      "types": ["*"]
    }
  },
  "quiet_hours": {
    "enabled": true,
    "start_time": "22:00:00",
    "end_time": "07:00:00",
    "timezone": "Africa/Lagos"
  },
  "updated_at": "2025-11-21T10:00:00Z"
}
```

**Example cURL:**
```bash
curl -X PUT http://localhost:8095/notifications/preferences \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "channels": {
      "push": {"enabled": true, "types": ["BOOKING_CONFIRMED"]},
      "sms": {"enabled": false, "types": []}
    }
  }'
```

---

### 11. Get Notification Preferences

Get current notification preferences.

**Endpoint:** `GET /notifications/preferences`

**Authentication:** Required

**Success Response (200 OK):**

Returns preferences object (same structure as Update Preferences response).

**Example cURL:**
```bash
curl -X GET http://localhost:8095/notifications/preferences \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

## WebSocket: Real-Time Notifications

### Connect to Notification Stream

Real-time notification delivery via WebSocket.

**WebSocket Endpoint:** `ws://localhost:8095/ws/notifications`

**Authentication:** Query parameter `?token=<access_token>`

**Connection:**
```javascript
const ws = new WebSocket(
  `ws://localhost:8095/ws/notifications?token=${accessToken}`
);

ws.onopen = () => {
  console.log('Connected to notifications');
};

ws.onmessage = (event) => {
  const notification = JSON.parse(event.data);
  console.log('New notification:', notification);
  displayNotification(notification);
};

ws.onerror = (error) => {
  console.error('WebSocket error:', error);
};

ws.onclose = () => {
  console.log('Disconnected from notifications');
};
```

**Message Format:**
```json
{
  "id": "912e4567-e89b-12d3-a456-426614174012",
  "notification_type": "BOOKING_CONFIRMED",
  "priority": "high",
  "title": "Booking Confirmed",
  "body": "Your booking has been confirmed.",
  "data": {
    "booking_id": "678e4567-e89b-12d3-a456-426614174011"
  },
  "action_url": "/bookings/678e4567-e89b-12d3-a456-426614174011",
  "created_at": "2025-11-21T10:00:00Z"
}
```

---

## Data Models

### Notification Schema

```typescript
interface Notification {
  id: string;                          // UUID
  recipient_id: string;                // UUID
  notification_type: NotificationType;
  priority: Priority;
  title: string;
  body: string;
  data: Record<string, any>;
  action_url: string | null;
  channels: Record<Channel, ChannelStatus>;
  status: NotificationStatus;
  created_at: string;                  // ISO 8601
  scheduled_for: string | null;        // ISO 8601
  sent_at: string | null;              // ISO 8601
  delivered_at: string | null;         // ISO 8601
  read_at: string | null;              // ISO 8601
  is_read: boolean;
}

interface ChannelStatus {
  status: 'queued' | 'sent' | 'delivered' | 'failed' | 'bounced' | 'read';
  message_id: string | null;
  sent_at: string | null;              // ISO 8601
  delivered_at: string | null;         // ISO 8601
  error: string | null;
}

type NotificationType = 
  | 'BOOKING_CONFIRMED'
  | 'BOOKING_CANCELLED'
  | 'TRIP_STARTING'
  | 'TRIP_DELAYED'
  | 'TRIP_CANCELLED'
  | 'PAYMENT_SUCCESS'
  | 'PAYMENT_FAILED'
  | 'PAYOUT_PROCESSED'
  | 'KYC_APPROVED'
  | 'KYC_REJECTED'
  | 'ROUTE_APPROVED'
  | 'REVIEW_RECEIVED'
  | 'PROMOTIONAL';

type Priority = 'low' | 'normal' | 'high' | 'urgent';

type Channel = 'push' | 'sms' | 'email' | 'in_app';

type NotificationStatus = 'queued' | 'sent' | 'delivered' | 'failed';
```

---

## Rate Limiting

| Action | Limit | Window |
|--------|-------|--------|
| Send Notification | 60 requests | Per minute |
| Send Bulk | 5 requests | Per hour |
| List Notifications | 120 requests | Per minute |
| Update Preferences | 10 requests | Per hour |

**Per-User Rate Limits:**
- Max 100 notifications per user per day
- Max 20 SMS per user per day
- Max 50 emails per user per day

---

## Error Handling

### Common Error Responses

| Code | Error | Description |
|------|-------|-------------|
| 400 | Bad Request | Invalid channel, unverified contact info |
| 401 | Unauthorized | Invalid or missing token |
| 403 | Forbidden | Not authorized to send notification |
| 404 | Not Found | Notification not found |
| 422 | Unprocessable Entity | Validation failed |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |

---

## Integration Notes for Frontend

### Notification System

```javascript
// 1. Register device for push notifications
async function registerDevice(deviceToken, deviceType) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch(
    'http://localhost:8095/notifications/devices',
    {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        device_token: deviceToken,
        device_type: deviceType
      })
    }
  );
  return response.json();
}

// 2. Get notifications
async function getNotifications(unreadOnly = false) {
  const token = localStorage.getItem('accessToken');
  const params = new URLSearchParams({
    unread_only: unreadOnly.toString(),
    limit: '20'
  });
  
  const response = await fetch(
    `http://localhost:8095/notifications?${params}`,
    {
      headers: { 'Authorization': `Bearer ${token}` }
    }
  );
  return response.json();
}

// 3. Mark as read
async function markAsRead(notificationId) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch(
    `http://localhost:8095/notifications/${notificationId}/read`,
    {
      method: 'PATCH',
      headers: { 'Authorization': `Bearer ${token}` }
    }
  );
  return response.json();
}

// 4. Update preferences
async function updateNotificationPreferences(preferences) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch(
    'http://localhost:8095/notifications/preferences',
    {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(preferences)
    }
  );
  return response.json();
}
```

### Real-Time Notification Handler

```javascript
class NotificationManager {
  constructor() {
    this.ws = null;
    this.unreadCount = 0;
  }
  
  connect(accessToken) {
    this.ws = new WebSocket(
      `ws://localhost:8095/ws/notifications?token=${accessToken}`
    );
    
    this.ws.onmessage = (event) => {
      const notification = JSON.parse(event.data);
      this.handleNotification(notification);
    };
    
    this.ws.onerror = (error) => {
      console.error('Notification WebSocket error:', error);
      setTimeout(() => this.connect(accessToken), 5000);
    };
  }
  
  handleNotification(notification) {
    // Update unread count
    this.unreadCount++;
    this.updateBadge();
    
    // Show notification UI
    this.showNotificationPopup(notification);
    
    // Play sound for high priority
    if (notification.priority === 'high' || notification.priority === 'urgent') {
      this.playNotificationSound();
    }
  }
  
  showNotificationPopup(notification) {
    // Create notification toast/popup
    const popup = document.createElement('div');
    popup.className = 'notification-popup';
    popup.innerHTML = `
      <h4>${notification.title}</h4>
      <p>${notification.body}</p>
    `;
    
    popup.onclick = () => {
      if (notification.action_url) {
        window.location.href = notification.action_url;
      }
      markAsRead(notification.id);
      this.unreadCount--;
      this.updateBadge();
    };
    
    document.body.appendChild(popup);
    
    setTimeout(() => popup.remove(), 5000);
  }
  
  updateBadge() {
    const badge = document.getElementById('notification-badge');
    if (badge) {
      badge.textContent = this.unreadCount;
      badge.style.display = this.unreadCount > 0 ? 'block' : 'none';
    }
  }
  
  playNotificationSound() {
    const audio = new Audio('/sounds/notification.mp3');
    audio.play().catch(e => console.log('Cannot play sound:', e));
  }
  
  disconnect() {
    if (this.ws) {
      this.ws.close();
    }
  }
}
```

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-21 | Initial API documentation |
