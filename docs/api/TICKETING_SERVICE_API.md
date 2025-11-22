# Ticketing Service API Documentation

## Service Information
- **Service Name:** Ticketing Service
- **Port:** 8086
- **Base URL:** `/v1`
- **Technology:** Java / Spring Boot
- **Description:** Generates cryptographically secure tickets with blockchain anchoring for verification

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

The Ticketing Service provides tamper-proof digital tickets using:
- **Cryptographic Signatures:** Each ticket is digitally signed
- **QR Codes:** For easy scanning and verification
- **Blockchain Anchoring:** Merkle root anchored on Polygon blockchain
- **Batch Processing:** Tickets batched for efficient blockchain writes

---

## Endpoints

### 1. Generate Ticket

Generate a cryptographically signed ticket for a confirmed booking.

**Endpoint:** `POST /v1/bookings/{bookingId}/ticket`

**Authentication:** Required (RIDER role)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| bookingId | UUID | Yes | Confirmed booking ID |

**Request Body:** None

**Success Response (201 Created):**
```json
{
  "id": "991e4567-e89b-12d3-a456-426614174006",
  "bookingId": "789e4567-e89b-12d3-a456-426614174004",
  "riderId": "123e4567-e89b-12d3-a456-426614174000",
  "driverId": "234e4567-e89b-12d3-a456-426614174001",
  "routeId": "123e4567-e89b-12d3-a456-426614174000",
  "tripDate": "2025-11-25T08:00:00.000Z",
  "seatNumber": 5,
  "pickupStop": "Ojota Bus Stop",
  "dropoffStop": "Challenge Bus Stop",
  "fare": 3500.00,
  "hash": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6",
  "signature": "MEUCIQD5K8...[base64 signature]...==",
  "qrCode": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...",
  "status": "ACTIVE",
  "generatedAt": "2025-11-21T10:30:00.000Z",
  "expiresAt": "2025-11-26T08:00:00.000Z",
  "usedAt": null,
  "revokedAt": null,
  "merkleBatch": {
    "batchId": "001e4567-e89b-12d3-a456-426614174007",
    "merkleRoot": "b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a1",
    "status": "PENDING_ANCHOR",
    "transactionHash": null,
    "blockchainType": null
  }
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Ticket unique identifier |
| bookingId | UUID | Associated booking ID |
| riderId | UUID | Rider ID |
| driverId | UUID | Driver ID |
| routeId | UUID | Route ID |
| tripDate | ISO 8601 | Trip date and time |
| seatNumber | integer | Assigned seat number |
| pickupStop | string | Pickup stop name |
| dropoffStop | string | Dropoff stop name |
| fare | decimal | Ticket fare in NGN |
| hash | string | SHA-256 hash of ticket data |
| signature | string | ECDSA digital signature (Base64) |
| qrCode | string | QR code image (Base64 data URL) |
| status | string | Ticket status (ACTIVE, USED, REVOKED, EXPIRED) |
| generatedAt | ISO 8601 | Ticket generation timestamp |
| expiresAt | ISO 8601 | Ticket expiration (24h after trip) |
| usedAt | ISO 8601 | Ticket scan timestamp (nullable) |
| revokedAt | ISO 8601 | Revocation timestamp (nullable) |
| merkleBatch | object | Blockchain batch information |

**Merkle Batch Fields:**

| Field | Type | Description |
|-------|------|-------------|
| batchId | UUID | Batch unique identifier |
| merkleRoot | string | Merkle tree root hash |
| status | string | PENDING_ANCHOR, ANCHORED, FAILED |
| transactionHash | string | Blockchain transaction hash (nullable) |
| blockchainType | string | Blockchain network (POLYGON, nullable) |

**Error Responses:**

**400 Bad Request:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Booking must be confirmed before generating ticket",
  "path": "/v1/bookings/789e4567-e89b-12d3-a456-426614174004/ticket"
}
```

**404 Not Found:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 404,
  "error": "Not Found",
  "message": "Booking not found",
  "path": "/v1/bookings/789e4567-e89b-12d3-a456-426614174004/ticket"
}
```

**409 Conflict:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 409,
  "error": "Conflict",
  "message": "Ticket already generated for this booking",
  "path": "/v1/bookings/789e4567-e89b-12d3-a456-426614174004/ticket"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8086/v1/bookings/789e4567-e89b-12d3-a456-426614174004/ticket \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 2. Verify Ticket

Verify ticket authenticity by scanning QR code or entering ticket code.

**Endpoint:** `POST /v1/tickets/verify`

**Authentication:** Required (DRIVER role)

**Request Body:**
```json
{
  "ticketCode": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6"
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| ticketCode | string | Yes | Ticket hash from QR code |

**Success Response (200 OK):**
```json
{
  "valid": true,
  "ticket": {
    "id": "991e4567-e89b-12d3-a456-426614174006",
    "bookingId": "789e4567-e89b-12d3-a456-426614174004",
    "riderId": "123e4567-e89b-12d3-a456-426614174000",
    "riderName": "John Doe",
    "seatNumber": 5,
    "pickupStop": "Ojota Bus Stop",
    "dropoffStop": "Challenge Bus Stop",
    "fare": 3500.00,
    "status": "ACTIVE",
    "tripDate": "2025-11-25T08:00:00.000Z"
  },
  "verificationDetails": {
    "signatureValid": true,
    "notExpired": true,
    "notUsed": true,
    "notRevoked": true,
    "blockchainVerified": true
  },
  "message": "Ticket is valid and can be used"
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| valid | boolean | Overall ticket validity |
| ticket | object | Ticket information |
| verificationDetails | object | Detailed verification results |
| message | string | Human-readable validation message |

**Verification Details:**

| Field | Type | Description |
|-------|------|-------------|
| signatureValid | boolean | Digital signature is valid |
| notExpired | boolean | Ticket has not expired |
| notUsed | boolean | Ticket has not been used |
| notRevoked | boolean | Ticket has not been revoked |
| blockchainVerified | boolean | Merkle root verified on blockchain |

**Invalid Ticket Response (200 OK with valid=false):**
```json
{
  "valid": false,
  "ticket": null,
  "verificationDetails": {
    "signatureValid": false,
    "notExpired": true,
    "notUsed": true,
    "notRevoked": true,
    "blockchainVerified": false
  },
  "message": "Invalid ticket signature - possible forgery"
}
```

**Error Responses:**

**404 Not Found:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 404,
  "error": "Not Found",
  "message": "Ticket not found",
  "path": "/v1/tickets/verify"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8086/v1/tickets/verify \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "ticketCode": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6"
  }'
```

---

### 3. Mark Ticket as Used

Mark a verified ticket as used (driver action during boarding).

**Endpoint:** `POST /v1/tickets/verify` (same endpoint, but marks as used after verification)

**Note:** When a driver verifies a valid ticket, the system automatically marks it as USED.

---

### 4. Revoke Ticket

Revoke a ticket (e.g., after booking cancellation).

**Endpoint:** `POST /v1/tickets/revoke`

**Authentication:** Internal service (called by Booking Service)

**Request Body:**
```json
{
  "ticketId": "991e4567-e89b-12d3-a456-426614174006",
  "reason": "Booking cancelled by user"
}
```

**Success Response (200 OK):**
```json
{
  "id": "991e4567-e89b-12d3-a456-426614174006",
  "status": "REVOKED",
  "revokedAt": "2025-11-21T11:00:00.000Z",
  "message": "Ticket successfully revoked"
}
```

---

### 5. Get Public Key

Get the public key for offline ticket verification.

**Endpoint:** `GET /v1/tickets/public-key`

**Authentication:** Not required (public endpoint)

**Success Response (200 OK):**
```json
{
  "publicKey": "-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...\n-----END PUBLIC KEY-----",
  "algorithm": "ECDSA",
  "curve": "secp256k1",
  "format": "PEM"
}
```

**Use Case:** Drivers can download the public key for offline ticket verification when internet is unavailable.

**Example cURL:**
```bash
curl -X GET http://localhost:8086/v1/tickets/public-key
```

---

## Ticket Status Flow

```
ACTIVE → USED → COMPLETED
   ↓       
REVOKED
   ↓
EXPIRED
```

**Status Descriptions:**

| Status | Description | Can Use |
|--------|-------------|---------|
| ACTIVE | Ticket valid and unused | Yes |
| USED | Ticket scanned and used for boarding | No |
| REVOKED | Ticket cancelled (booking cancelled) | No |
| EXPIRED | Ticket expired (24h after trip time) | No |

---

## Blockchain Integration

### Merkle Tree Batching

Tickets are batched into Merkle trees for efficient blockchain anchoring:

1. **Batch Creation:** Every 100 tickets or every 10 minutes (whichever comes first)
2. **Merkle Tree:** Tickets hashed into a Merkle tree
3. **Root Anchoring:** Merkle root published to Polygon blockchain
4. **Verification:** Individual tickets can be verified using Merkle proof

### Batch Status

| Status | Description | Timeline |
|--------|-------------|----------|
| PENDING_ANCHOR | Batch created, awaiting blockchain write | 0-10 min |
| ANCHORING | Transaction sent to blockchain | 1-2 min |
| ANCHORED | Successfully written to blockchain | Permanent |
| FAILED | Blockchain write failed (will retry) | Retry in 5 min |

### Blockchain Network

- **Network:** Polygon Mumbai Testnet (Production: Polygon Mainnet)
- **Contract:** Smart contract for storing Merkle roots
- **Gas:** Paid by platform

---

## QR Code Format

QR codes contain the ticket hash in this format:

```
openride://ticket/verify?code=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6
```

**Scanning Flow:**
1. Driver app scans QR code
2. Extracts ticket hash from URL
3. Calls `/v1/tickets/verify` API
4. Displays verification result

---

## Data Models

### Ticket Response Schema

```typescript
interface TicketResponse {
  id: string;                           // UUID
  bookingId: string;                    // UUID
  riderId: string;                      // UUID
  driverId: string;                     // UUID
  routeId: string;                      // UUID
  tripDate: string;                     // ISO 8601
  seatNumber: number;
  pickupStop: string;
  dropoffStop: string;
  fare: number;                         // Decimal (NGN)
  hash: string;                         // SHA-256 hash
  signature: string;                    // Base64 ECDSA signature
  qrCode: string;                       // Base64 image data URL
  status: TicketStatus;
  generatedAt: string;                  // ISO 8601
  expiresAt: string;                    // ISO 8601
  usedAt: string | null;                // ISO 8601
  revokedAt: string | null;             // ISO 8601
  merkleBatch: MerkleBatchInfo | null;
}

interface MerkleBatchInfo {
  batchId: string;                      // UUID
  merkleRoot: string;                   // Hash
  status: string;                       // PENDING_ANCHOR, ANCHORED, FAILED
  transactionHash: string | null;       // Blockchain TX hash
  blockchainType: string | null;        // POLYGON
}

type TicketStatus = 
  | 'ACTIVE' 
  | 'USED' 
  | 'REVOKED' 
  | 'EXPIRED';
```

---

## Error Handling

### Common Error Responses

| Code | Error | Description |
|------|-------|-------------|
| 400 | Bad Request | Invalid ticket code, booking not confirmed |
| 404 | Not Found | Ticket not found |
| 409 | Conflict | Ticket already exists for booking |
| 500 | Internal Server Error | Cryptographic operation failed |

---

## Integration Notes for Frontend

### Ticket Generation Flow

```javascript
// 1. Generate ticket after payment confirmation
async function generateTicket(bookingId) {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch(
    `http://localhost:8086/v1/bookings/${bookingId}/ticket`,
    {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` }
    }
  );
  
  const ticket = await response.json();
  
  // Store ticket for offline access
  await storeTicketOffline(ticket);
  
  return ticket;
}

// 2. Display ticket with QR code
function displayTicket(ticket) {
  const ticketHtml = `
    <div class="ticket">
      <h2>Your Ticket</h2>
      <img src="${ticket.qrCode}" alt="Ticket QR Code" />
      <p>Booking: ${ticket.bookingReference}</p>
      <p>Seat: ${ticket.seatNumber}</p>
      <p>From: ${ticket.pickupStop}</p>
      <p>To: ${ticket.dropoffStop}</p>
      <p>Date: ${new Date(ticket.tripDate).toLocaleString()}</p>
      <p>Status: <span class="status-${ticket.status}">${ticket.status}</span></p>
    </div>
  `;
  
  document.getElementById('ticket-container').innerHTML = ticketHtml;
}

// 3. Store ticket for offline access
async function storeTicketOffline(ticket) {
  if ('indexedDB' in window) {
    const db = await openTicketDB();
    const tx = db.transaction('tickets', 'readwrite');
    await tx.objectStore('tickets').put(ticket);
  }
}

// 4. Verify ticket (Driver app)
async function verifyTicket(ticketCode) {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch('http://localhost:8086/v1/tickets/verify', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ ticketCode })
  });
  
  const result = await response.json();
  
  if (result.valid) {
    showVerificationSuccess(result.ticket);
  } else {
    showVerificationError(result.message, result.verificationDetails);
  }
  
  return result;
}

// 5. QR Code Scanner (Driver app)
async function startQRScanner() {
  const scanner = new Html5QrcodeScanner("qr-reader", {
    fps: 10,
    qrbox: 250
  });
  
  scanner.render(async (decodedText) => {
    // Extract ticket code from URL
    const url = new URL(decodedText);
    const ticketCode = url.searchParams.get('code');
    
    if (ticketCode) {
      await verifyTicket(ticketCode);
      scanner.clear();
    }
  });
}
```

### Offline Ticket Verification

```javascript
// Download public key for offline verification
async function downloadPublicKey() {
  const response = await fetch('http://localhost:8086/v1/tickets/public-key');
  const { publicKey } = await response.json();
  
  // Store in local storage
  localStorage.setItem('ticketPublicKey', publicKey);
  
  return publicKey;
}

// Verify ticket signature offline
async function verifyTicketOffline(ticket) {
  const publicKey = localStorage.getItem('ticketPublicKey');
  
  if (!publicKey) {
    throw new Error('Public key not available for offline verification');
  }
  
  // Use Web Crypto API to verify signature
  const encoder = new TextEncoder();
  const data = encoder.encode(ticket.hash);
  const signature = base64ToArrayBuffer(ticket.signature);
  
  const key = await crypto.subtle.importKey(
    'spki',
    pemToArrayBuffer(publicKey),
    { name: 'ECDSA', namedCurve: 'P-256' },
    false,
    ['verify']
  );
  
  const valid = await crypto.subtle.verify(
    { name: 'ECDSA', hash: 'SHA-256' },
    key,
    signature,
    data
  );
  
  return {
    valid,
    message: valid ? 'Signature valid (offline)' : 'Invalid signature',
    note: 'Blockchain verification not available offline'
  };
}
```

---

## Security Considerations

1. **Ticket Forgery Prevention:**
   - Each ticket digitally signed with ECDSA
   - Signature verified against public key
   - Blockchain anchoring provides immutable audit trail

2. **Replay Attack Prevention:**
   - Each ticket can only be used once
   - Status changes recorded with timestamp
   - Database enforces uniqueness constraints

3. **QR Code Security:**
   - QR codes contain only the ticket hash
   - Full ticket data retrieved from backend
   - Cannot be tampered without invalidating signature

4. **Offline Verification:**
   - Drivers can verify signatures offline using public key
   - Blockchain verification requires internet
   - Reduced risk of fraud in low-connectivity areas

---

## Blockchain Explorer

View ticket anchoring transactions:

- **Testnet:** https://mumbai.polygonscan.com/tx/{transactionHash}
- **Mainnet:** https://polygonscan.com/tx/{transactionHash}

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-21 | Initial API documentation |
