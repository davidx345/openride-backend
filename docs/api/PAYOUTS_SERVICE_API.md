# Payouts Service API Documentation

## Service Information
- **Service Name:** Payouts Service
- **Port:** 8087
- **Base URL:** `/v1/payouts`
- **Technology:** Java / Spring Boot
- **Description:** Manages driver earnings, payout requests, and settlements with automated commission calculation

---

## Authentication

All endpoints require JWT authentication.

**Request Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

---

## Overview

The Payouts Service handles:
- **Earnings Tracking:** Automatic tracking of driver earnings from completed trips
- **Commission Calculation:** Platform commission (15% default) deducted automatically
- **Payout Requests:** Drivers request withdrawals to their bank accounts
- **Settlement Processing:** Automated weekly settlements via Paystack
- **Wallet Management:** Driver balance and transaction ledger

---

## Endpoints

### 1. Request Payout

Request a payout of available earnings.

**Endpoint:** `POST /v1/payouts/request`

**Authentication:** Required (DRIVER role)

**Request Body:**
```json
{
  "amount": 50000.00,
  "bankAccountId": "111e4567-e89b-12d3-a456-426614174008"
}
```

**Request Fields:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| amount | decimal | Yes | > 0 | Amount to withdraw in NGN |
| bankAccountId | UUID | Yes | Valid bank account | Driver's verified bank account |

**Success Response (201 Created):**
```json
{
  "id": "222e4567-e89b-12d3-a456-426614174009",
  "driverId": "234e4567-e89b-12d3-a456-426614174001",
  "bankAccount": {
    "id": "111e4567-e89b-12d3-a456-426614174008",
    "bankName": "GTBank",
    "accountNumber": "0123456789",
    "accountName": "John Doe"
  },
  "amount": 50000.00,
  "status": "PENDING_REVIEW",
  "requestedAt": "2025-11-21T10:30:00.000Z",
  "reviewedAt": null,
  "reviewedBy": null,
  "reviewerNotes": null,
  "settlementId": null,
  "completedAt": null,
  "failureReason": null,
  "createdAt": "2025-11-21T10:30:00.000Z"
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Payout request ID |
| driverId | UUID | Driver ID |
| bankAccount | object | Bank account details |
| amount | decimal | Payout amount in NGN |
| status | string | Payout status (see status flow) |
| requestedAt | ISO 8601 | Request timestamp |
| reviewedAt | ISO 8601 | Review timestamp (nullable) |
| reviewedBy | UUID | Admin who reviewed (nullable) |
| reviewerNotes | string | Review notes (nullable) |
| settlementId | UUID | Settlement batch ID (nullable) |
| completedAt | ISO 8601 | Completion timestamp (nullable) |
| failureReason | string | Failure reason (nullable) |
| createdAt | ISO 8601 | Record creation timestamp |

**Error Responses:**

**400 Bad Request:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient balance. Available: 45000.00 NGN",
  "path": "/v1/payouts/request"
}
```

**422 Unprocessable Entity:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Minimum payout amount is 5000.00 NGN",
  "path": "/v1/payouts/request"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8087/v1/payouts/request \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 50000.00,
    "bankAccountId": "111e4567-e89b-12d3-a456-426614174008"
  }'
```

---

### 2. List Payout Requests

Get all payout requests for authenticated driver.

**Endpoint:** `GET /v1/payouts/requests`

**Authentication:** Required (DRIVER role)

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| status | string | No | null | Filter by status |
| page | integer | No | 0 | Page number (0-indexed) |
| size | integer | No | 20 | Page size (max 100) |
| sort | string | No | requestedAt,desc | Sort field and direction |

**Success Response (200 OK):**
```json
{
  "content": [
    {
      "id": "222e4567-e89b-12d3-a456-426614174009",
      "amount": 50000.00,
      "status": "COMPLETED",
      "requestedAt": "2025-11-21T10:30:00.000Z",
      "completedAt": "2025-11-23T09:00:00.000Z",
      "bankAccount": {
        "bankName": "GTBank",
        "accountNumber": "0123456789"
      }
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "first": true
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8087/v1/payouts/requests?status=COMPLETED&page=0&size=20" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 3. Get Driver Balance

Get current wallet balance and earnings summary.

**Endpoint:** `GET /v1/payouts/balance`

**Authentication:** Required (DRIVER role)

**Success Response (200 OK):**
```json
{
  "driverId": "234e4567-e89b-12d3-a456-426614174001",
  "availableBalance": 75000.00,
  "pendingBalance": 25000.00,
  "totalEarnings": 500000.00,
  "totalPayouts": 400000.00,
  "platformCommission": 75000.00,
  "lastPayoutAt": "2025-11-20T09:00:00.000Z",
  "updatedAt": "2025-11-21T10:30:00.000Z"
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| driverId | UUID | Driver ID |
| availableBalance | decimal | Balance available for withdrawal |
| pendingBalance | decimal | Earnings pending settlement (from recent trips) |
| totalEarnings | decimal | Lifetime gross earnings |
| totalPayouts | decimal | Lifetime payouts to driver |
| platformCommission | decimal | Total commission paid to platform |
| lastPayoutAt | ISO 8601 | Last payout timestamp (nullable) |
| updatedAt | ISO 8601 | Last balance update |

**Example cURL:**
```bash
curl -X GET http://localhost:8087/v1/payouts/balance \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 4. Get Earnings Summary

Get detailed earnings breakdown by period.

**Endpoint:** `GET /v1/payouts/earnings`

**Authentication:** Required (DRIVER role)

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| startDate | date | No | 30 days ago | Start date (YYYY-MM-DD) |
| endDate | date | No | Today | End date (YYYY-MM-DD) |

**Success Response (200 OK):**
```json
{
  "driverId": "234e4567-e89b-12d3-a456-426614174001",
  "period": {
    "startDate": "2025-10-21",
    "endDate": "2025-11-21"
  },
  "summary": {
    "totalTrips": 45,
    "grossEarnings": 157500.00,
    "platformCommission": 23625.00,
    "netEarnings": 133875.00,
    "averageEarningPerTrip": 3500.00
  },
  "breakdown": [
    {
      "date": "2025-11-21",
      "trips": 3,
      "grossEarnings": 10500.00,
      "commission": 1575.00,
      "netEarnings": 8925.00
    },
    {
      "date": "2025-11-20",
      "trips": 5,
      "grossEarnings": 17500.00,
      "commission": 2625.00,
      "netEarnings": 14875.00
    }
  ]
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8087/v1/payouts/earnings?startDate=2025-10-21&endDate=2025-11-21" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 5. Get Ledger History

Get detailed transaction ledger (all credits and debits).

**Endpoint:** `GET /v1/payouts/ledger`

**Authentication:** Required (DRIVER role)

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| type | string | No | null | Filter by type: CREDIT, DEBIT |
| page | integer | No | 0 | Page number |
| size | integer | No | 50 | Page size |

**Success Response (200 OK):**
```json
{
  "content": [
    {
      "id": "333e4567-e89b-12d3-a456-426614174010",
      "type": "CREDIT",
      "amount": 3500.00,
      "description": "Trip earnings - Booking BK-20251121-ABC123",
      "referenceId": "789e4567-e89b-12d3-a456-426614174004",
      "referenceType": "BOOKING",
      "balanceAfter": 75000.00,
      "createdAt": "2025-11-21T10:00:00.000Z"
    },
    {
      "id": "444e4567-e89b-12d3-a456-426614174011",
      "type": "DEBIT",
      "amount": 50000.00,
      "description": "Payout to GTBank ****6789",
      "referenceId": "222e4567-e89b-12d3-a456-426614174009",
      "referenceType": "PAYOUT",
      "balanceAfter": 25000.00,
      "createdAt": "2025-11-20T09:00:00.000Z"
    }
  ],
  "totalElements": 2,
  "totalPages": 1
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8087/v1/payouts/ledger?type=CREDIT&page=0&size=50" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 6. Add/Update Bank Account

Add or update bank account for payouts.

**Endpoint:** `POST /v1/payouts/bank-accounts`

**Authentication:** Required (DRIVER role)

**Request Body:**
```json
{
  "bankCode": "058",
  "accountNumber": "0123456789",
  "accountName": "John Doe"
}
```

**Request Fields:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| bankCode | string | Yes | Valid bank code | Nigerian bank code (3 digits) |
| accountNumber | string | Yes | 10 digits | Bank account number |
| accountName | string | Yes | Match account | Account holder name |

**Success Response (201 Created):**
```json
{
  "id": "111e4567-e89b-12d3-a456-426614174008",
  "driverId": "234e4567-e89b-12d3-a456-426614174001",
  "bankCode": "058",
  "bankName": "GTBank",
  "accountNumber": "0123456789",
  "accountName": "John Doe",
  "isVerified": true,
  "isPrimary": true,
  "createdAt": "2025-11-21T10:30:00.000Z"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8087/v1/payouts/bank-accounts \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "bankCode": "058",
    "accountNumber": "0123456789",
    "accountName": "John Doe"
  }'
```

---

## Payout Status Flow

```
PENDING_REVIEW → APPROVED → PROCESSING → COMPLETED
      ↓              ↓
  REJECTED      CANCELLED
                    ↓
                 FAILED
```

**Status Descriptions:**

| Status | Description | Timeline |
|--------|-------------|----------|
| PENDING_REVIEW | Awaiting admin review | 0-24 hours |
| APPROVED | Approved, queued for next settlement batch | Next settlement (Monday 2 AM) |
| PROCESSING | Settlement batch being processed | 1-2 hours |
| COMPLETED | Payout successful | 3-5 business days in bank |
| REJECTED | Rejected by admin (fraud/policy violation) | Immediate |
| CANCELLED | Cancelled by driver before processing | Immediate |
| FAILED | Payment processing failed | Manual intervention |

---

## Commission Structure

| Component | Rate | Description |
|-----------|------|-------------|
| Platform Commission | 15% | Deducted from gross earnings |
| Payment Gateway Fee | ~1.5% | Absorbed by platform |
| Transfer Fee | Free | Platform covers transfer costs |

**Example Calculation:**
```
Booking Fare: 3,500 NGN
Platform Commission (15%): 525 NGN
Driver Earnings: 2,975 NGN
```

---

## Settlement Schedule

Automated settlements run on a schedule:

| Day | Time | Action |
|-----|------|--------|
| Monday | 2:00 AM WAT | Process all APPROVED payouts |
| Monday | 3:00 AM WAT | Settlement batch sent to Paystack |
| Monday | 9:00 AM WAT | Funds arrive in driver accounts (typically) |

**Manual Settlements:**
- Admins can trigger manual settlements for urgent payouts
- Available 24/7 for customer support escalations

---

## Minimum Payout Rules

| Rule | Value |
|------|-------|
| Minimum Amount | 5,000 NGN |
| Maximum Amount | 500,000 NGN per request |
| Daily Limit | 1,000,000 NGN |
| Frequency | Once per day |

---

## Data Models

### Payout Response Schema

```typescript
interface PayoutResponse {
  id: string;                           // UUID
  driverId: string;                     // UUID
  bankAccount: BankAccountInfo;
  amount: number;                       // Decimal (NGN)
  status: PayoutStatus;
  requestedAt: string;                  // ISO 8601
  reviewedAt: string | null;            // ISO 8601
  reviewedBy: string | null;            // UUID
  reviewerNotes: string | null;
  settlementId: string | null;          // UUID
  completedAt: string | null;           // ISO 8601
  failureReason: string | null;
  createdAt: string;                    // ISO 8601
}

interface BankAccountInfo {
  id: string;                           // UUID
  bankName: string;
  bankCode: string;
  accountNumber: string;
  accountName: string;
}

interface WalletResponse {
  driverId: string;                     // UUID
  availableBalance: number;             // Decimal (NGN)
  pendingBalance: number;               // Decimal (NGN)
  totalEarnings: number;                // Decimal (NGN)
  totalPayouts: number;                 // Decimal (NGN)
  platformCommission: number;           // Decimal (NGN)
  lastPayoutAt: string | null;          // ISO 8601
  updatedAt: string;                    // ISO 8601
}

type PayoutStatus = 
  | 'PENDING_REVIEW' 
  | 'APPROVED' 
  | 'REJECTED' 
  | 'PROCESSING' 
  | 'COMPLETED' 
  | 'FAILED' 
  | 'CANCELLED';
```

---

## Error Handling

### Common Error Responses

| Code | Error | Description |
|------|-------|-------------|
| 400 | Bad Request | Invalid request, insufficient balance |
| 401 | Unauthorized | Invalid or missing token |
| 403 | Forbidden | Not a driver, KYC not verified |
| 404 | Not Found | Payout or bank account not found |
| 422 | Unprocessable Entity | Below minimum payout amount |
| 429 | Too Many Requests | Daily limit exceeded |
| 500 | Internal Server Error | Payment processing error |

---

## Integration Notes for Frontend

### Complete Payout Flow

```javascript
// 1. Check wallet balance
async function getBalance() {
  const token = localStorage.getItem('accessToken');
  const response = await fetch('http://localhost:8087/v1/payouts/balance', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return response.json();
}

// 2. Add bank account
async function addBankAccount(bankDetails) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch('http://localhost:8087/v1/payouts/bank-accounts', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(bankDetails)
  });
  return response.json();
}

// 3. Request payout
async function requestPayout(amount, bankAccountId) {
  const token = localStorage.getItem('accessToken');
  
  // Validate minimum amount
  if (amount < 5000) {
    throw new Error('Minimum payout amount is 5,000 NGN');
  }
  
  const response = await fetch('http://localhost:8087/v1/payouts/request', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ amount, bankAccountId })
  });
  
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.message);
  }
  
  return response.json();
}

// 4. Track payout status
async function trackPayout(payoutId) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch(
    `http://localhost:8087/v1/payouts/requests?page=0&size=20`,
    {
      headers: { 'Authorization': `Bearer ${token}` }
    }
  );
  const data = await response.json();
  return data.content.find(p => p.id === payoutId);
}

// 5. Get earnings summary
async function getEarnings(startDate, endDate) {
  const token = localStorage.getItem('accessToken');
  const params = new URLSearchParams({ startDate, endDate });
  
  const response = await fetch(
    `http://localhost:8087/v1/payouts/earnings?${params}`,
    {
      headers: { 'Authorization': `Bearer ${token}` }
    }
  );
  return response.json();
}
```

### Wallet Dashboard Component

```javascript
class WalletDashboard {
  async load() {
    const [balance, earnings] = await Promise.all([
      getBalance(),
      getEarnings(
        new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
        new Date().toISOString().split('T')[0]
      )
    ]);
    
    this.renderBalance(balance);
    this.renderEarnings(earnings);
  }
  
  renderBalance(balance) {
    return `
      <div class="wallet">
        <h2>Wallet Balance</h2>
        <p class="balance">₦${balance.availableBalance.toLocaleString()}</p>
        <p class="pending">Pending: ₦${balance.pendingBalance.toLocaleString()}</p>
        <button onclick="requestPayout()">Request Payout</button>
      </div>
    `;
  }
  
  renderEarnings(earnings) {
    return `
      <div class="earnings">
        <h3>Last 30 Days</h3>
        <p>Total Trips: ${earnings.summary.totalTrips}</p>
        <p>Gross Earnings: ₦${earnings.summary.grossEarnings.toLocaleString()}</p>
        <p>Commission: ₦${earnings.summary.platformCommission.toLocaleString()}</p>
        <p>Net Earnings: ₦${earnings.summary.netEarnings.toLocaleString()}</p>
      </div>
    `;
  }
}
```

---

## Kafka Events

The service publishes events to Kafka for real-time updates:

### Topics

| Topic | Event | Description |
|-------|-------|-------------|
| trip.completed | TripCompletedEvent | Trip completed, earnings credited |
| payout.requested | PayoutRequestedEvent | Payout request created |
| payout.completed | PayoutCompletedEvent | Payout successfully processed |
| settlement.started | SettlementStartedEvent | Settlement batch started |

### Event Schema Example

```json
{
  "eventType": "PAYOUT_COMPLETED",
  "timestamp": "2025-11-21T10:30:00.000Z",
  "data": {
    "payoutId": "222e4567-e89b-12d3-a456-426614174009",
    "driverId": "234e4567-e89b-12d3-a456-426614174001",
    "amount": 50000.00,
    "status": "COMPLETED"
  }
}
```

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-21 | Initial API documentation |
