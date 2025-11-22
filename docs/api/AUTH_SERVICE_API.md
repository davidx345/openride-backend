# Auth Service API Documentation

## Service Information
- **Service Name:** Auth Service
- **Port:** 8081
- **Base URL:** `/v1/auth`
- **Technology:** Java / Spring Boot
- **Description:** Handles user authentication using OTP-based passwordless authentication

---

## Authentication Flow

The auth service uses OTP (One-Time Password) authentication:
1. User requests OTP via phone number
2. System sends OTP via SMS
3. User verifies OTP
4. System returns JWT access token and refresh token

---

## Endpoints

### 1. Send OTP

Send a one-time password to user's phone number.

**Endpoint:** `POST /v1/auth/send-otp`

**Authentication:** Not required (public endpoint)

**Request Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "phone": "+234XXXXXXXXXX"
}
```

**Request Fields:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| phone | string | Yes | Must match pattern: `^\\+234[7-9][0-1][0-9]{8}$` | Nigerian phone number in international format |

**Success Response (200 OK):**
```json
{
  "message": "OTP sent successfully",
  "expiresIn": 300
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| message | string | Success message |
| expiresIn | integer | OTP expiration time in seconds (default: 300 = 5 minutes) |

**Error Responses:**

**400 Bad Request:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Phone number must be valid Nigerian format (+234XXXXXXXXXX)",
  "path": "/v1/auth/send-otp"
}
```

**429 Too Many Requests:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Too many OTP requests. Please try again later.",
  "path": "/v1/auth/send-otp"
}
```

**500 Internal Server Error:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Failed to send OTP",
  "path": "/v1/auth/send-otp"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8081/v1/auth/send-otp \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "+2348012345678"
  }'
```

---

### 2. Verify OTP

Verify OTP code and authenticate user.

**Endpoint:** `POST /v1/auth/verify-otp`

**Authentication:** Not required (public endpoint)

**Request Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "phone": "+234XXXXXXXXXX",
  "code": "123456"
}
```

**Request Fields:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| phone | string | Yes | Must match pattern: `^\\+234[7-9][0-1][0-9]{8}$` | Nigerian phone number |
| code | string | Yes | Must be exactly 6 digits | OTP code received via SMS |

**Success Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "phone": "+2348012345678",
    "fullName": "John Doe",
    "email": "john.doe@example.com",
    "role": "RIDER",
    "kycStatus": "NOT_STARTED"
  }
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| accessToken | string | JWT access token (valid for 1 hour) |
| refreshToken | string | JWT refresh token (valid for 30 days) |
| tokenType | string | Token type (always "Bearer") |
| expiresIn | integer | Access token expiration in seconds |
| user | object | User profile information |
| user.id | UUID | User unique identifier |
| user.phone | string | User phone number |
| user.fullName | string | User full name |
| user.email | string | User email address (nullable) |
| user.role | string | User role: RIDER, DRIVER, or ADMIN |
| user.kycStatus | string | KYC status: NOT_STARTED, PENDING, VERIFIED, REJECTED |

**Error Responses:**

**400 Bad Request:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "OTP code must be 6 digits",
  "path": "/v1/auth/verify-otp"
}
```

**401 Unauthorized:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired OTP",
  "path": "/v1/auth/verify-otp"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8081/v1/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "+2348012345678",
    "code": "123456"
  }'
```

---

### 3. Refresh Token

Refresh access token using refresh token.

**Endpoint:** `POST /v1/auth/refresh-token`

**Authentication:** Not required (uses refresh token)

**Request Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| refreshToken | string | Yes | Valid refresh token from previous login |

**Success Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| accessToken | string | New JWT access token |
| refreshToken | string | New refresh token (token rotation) |
| tokenType | string | Token type (always "Bearer") |
| expiresIn | integer | Access token expiration in seconds |

**Error Responses:**

**401 Unauthorized:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired refresh token",
  "path": "/v1/auth/refresh-token"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8081/v1/auth/refresh-token \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }'
```

---

### 4. Logout

Logout user and invalidate tokens.

**Endpoint:** `POST /v1/auth/logout`

**Authentication:** Required (Bearer token)

**Request Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

**Request Body:** None

**Success Response (204 No Content):**

No response body.

**Error Responses:**

**401 Unauthorized:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or missing token",
  "path": "/v1/auth/logout"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8081/v1/auth/logout \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

## Error Handling

### Common Error Response Format

All errors follow this structure:

```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 400,
  "error": "Error Type",
  "message": "Detailed error message",
  "path": "/v1/auth/endpoint"
}
```

### HTTP Status Codes

| Code | Description | When Used |
|------|-------------|-----------|
| 200 | OK | Successful request |
| 204 | No Content | Successful logout |
| 400 | Bad Request | Invalid request data or validation failure |
| 401 | Unauthorized | Invalid credentials or token |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server-side error |

---

## Rate Limiting

The auth service implements rate limiting to prevent abuse:

- **OTP Send:** Maximum 3 requests per phone number per 15 minutes
- **OTP Verify:** Maximum 5 attempts per phone number per 15 minutes
- **Token Refresh:** Maximum 10 requests per refresh token per hour

**Rate Limit Headers:**
```
X-RateLimit-Limit: 3
X-RateLimit-Remaining: 2
X-RateLimit-Reset: 1637490600
```

---

## Security Considerations

1. **OTP Security:**
   - OTPs expire after 5 minutes
   - OTPs are single-use (deleted after verification)
   - Rate limiting prevents brute force attacks

2. **Token Security:**
   - Access tokens expire after 1 hour
   - Refresh tokens expire after 30 days
   - Token rotation on refresh (old token invalidated)
   - Tokens are invalidated on logout

3. **Phone Number Validation:**
   - Only Nigerian phone numbers accepted (+234)
   - Format strictly validated

---

## Integration Notes for Frontend

### Authentication Flow Implementation

```javascript
// 1. Send OTP
async function sendOTP(phoneNumber) {
  const response = await fetch('http://localhost:8081/v1/auth/send-otp', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phone: phoneNumber })
  });
  return response.json();
}

// 2. Verify OTP
async function verifyOTP(phoneNumber, code) {
  const response = await fetch('http://localhost:8081/v1/auth/verify-otp', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phone: phoneNumber, code })
  });
  const data = await response.json();
  
  // Store tokens
  localStorage.setItem('accessToken', data.accessToken);
  localStorage.setItem('refreshToken', data.refreshToken);
  
  return data;
}

// 3. Refresh Token
async function refreshAccessToken() {
  const refreshToken = localStorage.getItem('refreshToken');
  const response = await fetch('http://localhost:8081/v1/auth/refresh-token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken })
  });
  const data = await response.json();
  
  // Update tokens
  localStorage.setItem('accessToken', data.accessToken);
  localStorage.setItem('refreshToken', data.refreshToken);
  
  return data;
}

// 4. Logout
async function logout() {
  const token = localStorage.getItem('accessToken');
  await fetch('http://localhost:8081/v1/auth/logout', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });
  
  // Clear tokens
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
}
```

### Automatic Token Refresh

Implement interceptor to automatically refresh expired tokens:

```javascript
// Axios interceptor example
axios.interceptors.response.use(
  response => response,
  async error => {
    const originalRequest = error.config;
    
    if (error.response.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      
      try {
        await refreshAccessToken();
        const token = localStorage.getItem('accessToken');
        originalRequest.headers['Authorization'] = 'Bearer ' + token;
        return axios(originalRequest);
      } catch (refreshError) {
        // Redirect to login
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }
    
    return Promise.reject(error);
  }
);
```

---

## Testing

### Postman Collection

Import this collection to test all auth endpoints:

```json
{
  "info": {
    "name": "OpenRIDE Auth Service",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Send OTP",
      "request": {
        "method": "POST",
        "header": [{"key": "Content-Type", "value": "application/json"}],
        "body": {
          "mode": "raw",
          "raw": "{\n  \"phone\": \"+2348012345678\"\n}"
        },
        "url": "{{baseUrl}}/v1/auth/send-otp"
      }
    }
  ]
}
```

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-21 | Initial API documentation |
