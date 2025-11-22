# User Service API Documentation

## Service Information
- **Service Name:** User Service
- **Port:** 8082
- **Base URL:** `/v1/users`
- **Technology:** Java / Spring Boot
- **Description:** Manages user profiles, driver onboarding, and KYC verification

---

## Authentication

All endpoints require JWT authentication unless specified otherwise.

**Request Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

---

## Endpoints

### 1. Create User

Create a new user account (Internal - called by Auth Service).

**Endpoint:** `POST /v1/users`

**Authentication:** Internal service-to-service (not exposed to public)

**Request Body:**
```json
{
  "phone": "+2348012345678",
  "role": "RIDER"
}
```

**Request Fields:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| phone | string | Yes | Must match pattern: `^\\+234[7-9][0-1][0-9]{8}$` | Nigerian phone number |
| role | string | No | RIDER or DRIVER | User role (default: RIDER) |

**Success Response (201 Created):**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "phone": "+2348012345678",
  "fullName": null,
  "email": null,
  "role": "RIDER",
  "kycStatus": "NOT_STARTED",
  "rating": null,
  "isActive": true,
  "createdAt": "2025-11-21T10:30:00.000Z",
  "updatedAt": "2025-11-21T10:30:00.000Z",
  "driverProfile": null
}
```

---

### 2. Get Current User Profile

Get authenticated user's profile.

**Endpoint:** `GET /v1/users/me`

**Authentication:** Required

**Request Headers:**
```
Authorization: Bearer <access_token>
```

**Request Body:** None

**Success Response (200 OK):**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "phone": "+2348012345678",
  "fullName": "John Doe",
  "email": "john.doe@example.com",
  "role": "RIDER",
  "kycStatus": "VERIFIED",
  "rating": 4.8,
  "isActive": true,
  "createdAt": "2025-11-21T10:30:00.000Z",
  "updatedAt": "2025-11-21T11:45:00.000Z",
  "driverProfile": null
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | User unique identifier |
| phone | string | User phone number |
| fullName | string | User full name (nullable) |
| email | string | User email address (nullable) |
| role | string | User role: RIDER, DRIVER, or ADMIN |
| kycStatus | string | KYC verification status |
| rating | decimal | User rating (1.0 - 5.0, nullable) |
| isActive | boolean | Whether user account is active |
| createdAt | ISO 8601 | Account creation timestamp |
| updatedAt | ISO 8601 | Last update timestamp |
| driverProfile | object | Driver profile (null for riders) |

**Error Responses:**

**401 Unauthorized:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or missing token",
  "path": "/v1/users/me"
}
```

**Example cURL:**
```bash
curl -X GET http://localhost:8082/v1/users/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 3. Update User Profile

Update current user's profile information.

**Endpoint:** `PUT /v1/users/me`

**Authentication:** Required

**Request Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "fullName": "John Doe",
  "email": "john.doe@example.com"
}
```

**Request Fields:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| fullName | string | No | Min: 2, Max: 100 chars | User's full name |
| email | string | No | Valid email format | User's email address |

**Success Response (200 OK):**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "phone": "+2348012345678",
  "fullName": "John Doe",
  "email": "john.doe@example.com",
  "role": "RIDER",
  "kycStatus": "VERIFIED",
  "rating": 4.8,
  "isActive": true,
  "createdAt": "2025-11-21T10:30:00.000Z",
  "updatedAt": "2025-11-21T12:00:00.000Z",
  "driverProfile": null
}
```

**Error Responses:**

**400 Bad Request:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid email format",
  "path": "/v1/users/me"
}
```

**Example cURL:**
```bash
curl -X PUT http://localhost:8082/v1/users/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "John Doe",
    "email": "john.doe@example.com"
  }'
```

---

### 4. Upgrade to Driver

Request to upgrade rider account to driver.

**Endpoint:** `POST /v1/users/upgrade-to-driver`

**Authentication:** Required (must be RIDER role)

**Request Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "licenseNumber": "ABC123456789",
  "vehicleType": "SEDAN",
  "vehicleModel": "Toyota Camry",
  "vehiclePlateNumber": "LAG-123-AB"
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| licenseNumber | string | Yes | Driver's license number |
| vehicleType | string | Yes | Vehicle type: SEDAN, SUV, MINIBUS |
| vehicleModel | string | Yes | Vehicle make and model |
| vehiclePlateNumber | string | Yes | Vehicle registration plate number |

**Success Response (200 OK):**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "phone": "+2348012345678",
  "fullName": "John Doe",
  "email": "john.doe@example.com",
  "role": "DRIVER",
  "kycStatus": "PENDING",
  "rating": null,
  "isActive": true,
  "createdAt": "2025-11-21T10:30:00.000Z",
  "updatedAt": "2025-11-21T12:15:00.000Z",
  "driverProfile": {
    "id": "456e4567-e89b-12d3-a456-426614174001",
    "licensePhotoUrl": null,
    "vehiclePhotoUrl": null,
    "kycNotes": "Pending KYC verification",
    "totalTrips": 0,
    "totalEarnings": 0.00
  }
}
```

**Response Fields:**

**Driver Profile Object:**

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Driver profile ID |
| licensePhotoUrl | string | URL to driver's license photo (nullable) |
| vehiclePhotoUrl | string | URL to vehicle photo (nullable) |
| kycNotes | string | KYC review notes |
| totalTrips | integer | Total completed trips |
| totalEarnings | decimal | Total earnings in NGN |

**Error Responses:**

**400 Bad Request:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "User is already a driver",
  "path": "/v1/users/upgrade-to-driver"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8082/v1/users/upgrade-to-driver \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "licenseNumber": "ABC123456789",
    "vehicleType": "SEDAN",
    "vehicleModel": "Toyota Camry",
    "vehiclePlateNumber": "LAG-123-AB"
  }'
```

---

### 5. Upload KYC Documents

Upload KYC documents for driver verification (Multipart file upload).

**Endpoint:** `POST /v1/drivers/kyc-documents`

**Authentication:** Required (must be DRIVER role)

**Request Headers:**
```
Authorization: Bearer <access_token>
Content-Type: multipart/form-data
```

**Request Body (multipart/form-data):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| licensePhoto | file | Yes | Driver's license photo (JPG/PNG, max 5MB) |
| vehiclePhoto | file | Yes | Vehicle photo (JPG/PNG, max 5MB) |
| bvn | string | Yes | Bank Verification Number (11 digits) |

**Success Response (200 OK):**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "phone": "+2348012345678",
  "fullName": "John Doe",
  "email": "john.doe@example.com",
  "role": "DRIVER",
  "kycStatus": "PENDING",
  "rating": null,
  "isActive": true,
  "createdAt": "2025-11-21T10:30:00.000Z",
  "updatedAt": "2025-11-21T12:30:00.000Z",
  "driverProfile": {
    "id": "456e4567-e89b-12d3-a456-426614174001",
    "licensePhotoUrl": "https://storage.openride.com/kyc/license_123.jpg",
    "vehiclePhotoUrl": "https://storage.openride.com/kyc/vehicle_123.jpg",
    "kycNotes": "Documents uploaded, awaiting review",
    "totalTrips": 0,
    "totalEarnings": 0.00
  }
}
```

**Error Responses:**

**400 Bad Request:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "File size exceeds maximum allowed (5MB)",
  "path": "/v1/drivers/kyc-documents"
}
```

**403 Forbidden:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Only drivers can upload KYC documents",
  "path": "/v1/drivers/kyc-documents"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8082/v1/drivers/kyc-documents \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -F "licensePhoto=@/path/to/license.jpg" \
  -F "vehiclePhoto=@/path/to/vehicle.jpg" \
  -F "bvn=12345678901"
```

---

## Data Models

### User Response Schema

```typescript
interface UserResponse {
  id: string;                    // UUID
  phone: string;                 // +234XXXXXXXXXX
  fullName: string | null;
  email: string | null;
  role: 'RIDER' | 'DRIVER' | 'ADMIN';
  kycStatus: 'NOT_STARTED' | 'PENDING' | 'VERIFIED' | 'REJECTED';
  rating: number | null;         // 1.0 - 5.0
  isActive: boolean;
  createdAt: string;             // ISO 8601
  updatedAt: string;             // ISO 8601
  driverProfile: DriverProfile | null;
}

interface DriverProfile {
  id: string;                    // UUID
  licensePhotoUrl: string | null;
  vehiclePhotoUrl: string | null;
  kycNotes: string;
  totalTrips: number;
  totalEarnings: number;         // Decimal (NGN)
}
```

---

## Error Handling

### Common Error Response Format

```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 400,
  "error": "Error Type",
  "message": "Detailed error message",
  "path": "/v1/users/endpoint"
}
```

### HTTP Status Codes

| Code | Description | When Used |
|------|-------------|-----------|
| 200 | OK | Successful GET/PUT request |
| 201 | Created | User successfully created |
| 400 | Bad Request | Invalid request data or validation failure |
| 401 | Unauthorized | Invalid or missing token |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | User not found |
| 409 | Conflict | User already exists or duplicate data |
| 500 | Internal Server Error | Server-side error |

---

## KYC Status Flow

```
NOT_STARTED → PENDING → VERIFIED
                  ↓
              REJECTED
```

**Status Descriptions:**

| Status | Description | Actions Available |
|--------|-------------|-------------------|
| NOT_STARTED | No KYC documents uploaded | Upload documents |
| PENDING | Documents uploaded, awaiting admin review | Wait for review |
| VERIFIED | KYC approved by admin | Can create routes, receive bookings |
| REJECTED | KYC rejected by admin | Re-upload documents with corrections |

---

## Integration Notes for Frontend

### User Profile Management

```javascript
// Get current user profile
async function getUserProfile() {
  const token = localStorage.getItem('accessToken');
  const response = await fetch('http://localhost:8082/v1/users/me', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return response.json();
}

// Update user profile
async function updateProfile(fullName, email) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch('http://localhost:8082/v1/users/me', {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ fullName, email })
  });
  return response.json();
}

// Upgrade to driver
async function upgradeToDriver(driverData) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch('http://localhost:8082/v1/users/upgrade-to-driver', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(driverData)
  });
  return response.json();
}

// Upload KYC documents
async function uploadKYCDocuments(licensePhoto, vehiclePhoto, bvn) {
  const token = localStorage.getItem('accessToken');
  const formData = new FormData();
  formData.append('licensePhoto', licensePhoto);
  formData.append('vehiclePhoto', vehiclePhoto);
  formData.append('bvn', bvn);
  
  const response = await fetch('http://localhost:8082/v1/drivers/kyc-documents', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` },
    body: formData
  });
  return response.json();
}
```

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-21 | Initial API documentation |
