# OpenRide Ticketing Service

Blockchain-anchored ticketing service with cryptographic verification for tamper-evident digital tickets.

## Overview

The Ticketing Service generates secure, verifiable tickets for confirmed bookings using:
- **ECDSA signatures** for cryptographic verification
- **SHA-256 hashing** for ticket integrity
- **QR codes** for easy scanning and validation
- **Merkle trees** for efficient batch verification
- **Blockchain anchoring** (Polygon or Hyperledger Fabric) for tamper evidence

## Features

### Core Capabilities
- ✅ Generate cryptographically signed tickets
- ✅ QR code generation with embedded signatures
- ✅ Offline ticket verification (signature-based)
- ✅ Batched blockchain anchoring via Merkle trees
- ✅ Public key distribution for drivers
- ✅ Ticket verification API
- ✅ Audit logging for all verifications

### Security Features
- ECDSA (secp256k1) signature generation
- SHA-256 canonical hashing
- Tamper-evident ticket structure
- Key rotation support
- Secure key storage integration

### Blockchain Integration
- **Polygon (Mumbai Testnet/Mainnet)**
  - Gas-optimized batch anchoring
  - Merkle root storage on-chain
  - Transaction confirmation tracking
  
- **Hyperledger Fabric** (Alternative)
  - Private blockchain anchoring
  - Chaincode integration
  - Channel-based isolation

## Architecture

```
┌─────────────────┐
│ Booking Service │
└────────┬────────┘
         │ (1) Booking Confirmed
         ↓
┌─────────────────────────────────┐
│   Ticketing Service             │
│                                 │
│  ┌──────────────────────────┐  │
│  │ Ticket Generator         │  │
│  │ - Canonical JSON         │  │
│  │ - SHA-256 Hash           │  │
│  │ - ECDSA Signature        │  │
│  │ - QR Code Generation     │  │
│  └──────────────────────────┘  │
│              │                  │
│              ↓                  │
│  ┌──────────────────────────┐  │
│  │ Merkle Tree Builder      │  │
│  │ - Batch tickets          │  │
│  │ - Generate Merkle tree   │  │
│  │ - Create proofs          │  │
│  └──────────────────────────┘  │
│              │                  │
│              ↓                  │
│  ┌──────────────────────────┐  │
│  │ Blockchain Anchor        │  │
│  │ - Submit Merkle root     │  │
│  │ - Track confirmations    │  │
│  │ - Handle failures        │  │
│  └──────────────────────────┘  │
└─────────────────────────────────┘
         │
         ↓
┌─────────────────┐
│ Polygon Network │
│ (or Hyperledger)│
└─────────────────┘
```

## API Endpoints

### Internal APIs (Service-to-Service)

#### Generate Ticket
```http
POST /v1/tickets
Content-Type: application/json
Authorization: Bearer <service-token>

{
  "bookingId": "uuid",
  "riderId": "uuid",
  "driverId": "uuid",
  "routeId": "uuid",
  "tripDate": "2024-01-15T08:00:00Z",
  "seatNumber": 1,
  "pickupStop": "Lekki Phase 1",
  "dropoffStop": "Victoria Island",
  "fare": 1500.00
}

Response 201:
{
  "ticketId": "uuid",
  "qrCode": "base64-encoded-png",
  "signature": "hex-encoded-signature",
  "hash": "sha256-hash",
  "expiresAt": "2024-01-16T08:00:00Z"
}
```

### Public APIs

#### Get Ticket by Booking ID
```http
GET /v1/bookings/{bookingId}/ticket
Authorization: Bearer <jwt-token>

Response 200:
{
  "ticketId": "uuid",
  "bookingId": "uuid",
  "qrCode": "base64-encoded-png",
  "signature": "hex-signature",
  "hash": "sha256-hash",
  "status": "VALID",
  "createdAt": "2024-01-15T08:00:00Z",
  "expiresAt": "2024-01-16T08:00:00Z"
}
```

#### Verify Ticket
```http
POST /v1/tickets/verify
Content-Type: application/json

{
  "ticketId": "uuid",
  "signature": "hex-signature",
  "hash": "sha256-hash"
}

Response 200:
{
  "valid": true,
  "ticketId": "uuid",
  "bookingId": "uuid",
  "riderId": "uuid",
  "status": "VALID",
  "verificationMethod": "SIGNATURE",
  "message": "Ticket is valid and has not been tampered with"
}
```

#### Get Public Key
```http
GET /v1/tickets/public-key

Response 200:
{
  "publicKey": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----",
  "algorithm": "ECDSA",
  "curve": "secp256k1",
  "format": "PEM"
}
```

## Database Schema

### Tables

#### tickets
```sql
CREATE TABLE tickets (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE,
    rider_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    route_id UUID NOT NULL,
    trip_date TIMESTAMP NOT NULL,
    seat_number INTEGER NOT NULL,
    pickup_stop VARCHAR(255) NOT NULL,
    dropoff_stop VARCHAR(255) NOT NULL,
    fare DECIMAL(10, 2) NOT NULL,
    canonical_json TEXT NOT NULL,
    hash VARCHAR(64) NOT NULL UNIQUE,
    signature TEXT NOT NULL,
    qr_code TEXT,
    status VARCHAR(20) NOT NULL,
    merkle_batch_id UUID,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

#### ticket_verification_logs
```sql
CREATE TABLE ticket_verification_logs (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL,
    verifier_id UUID,
    verification_method VARCHAR(50) NOT NULL,
    result VARCHAR(20) NOT NULL,
    verification_time TIMESTAMP DEFAULT NOW()
);
```

#### merkle_batches
```sql
CREATE TABLE merkle_batches (
    id UUID PRIMARY KEY,
    merkle_root VARCHAR(64) NOT NULL,
    ticket_count INTEGER NOT NULL,
    blockchain_anchor_id UUID,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);
```

#### blockchain_anchors
```sql
CREATE TABLE blockchain_anchors (
    id UUID PRIMARY KEY,
    merkle_batch_id UUID NOT NULL,
    blockchain_type VARCHAR(50) NOT NULL,
    transaction_hash VARCHAR(66),
    block_number BIGINT,
    confirmation_count INTEGER DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    gas_used BIGINT,
    submitted_at TIMESTAMP,
    confirmed_at TIMESTAMP
);
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host | localhost |
| `DB_PORT` | PostgreSQL port | 5432 |
| `DB_NAME` | Database name | openride |
| `JWT_SECRET` | JWT secret key | (required) |
| `BLOCKCHAIN_ENABLED` | Enable blockchain anchoring | true |
| `BLOCKCHAIN_PROVIDER` | polygon or hyperledger | polygon |
| `POLYGON_NETWORK` | mumbai or mainnet | mumbai |
| `POLYGON_RPC_URL` | Polygon RPC endpoint | (required) |
| `POLYGON_WALLET_PRIVATE_KEY` | Wallet private key | (required) |
| `AUTO_GENERATE_KEYS` | Auto-generate ECDSA keys | true |
| `BATCH_SIZE` | Tickets per Merkle batch | 100 |

## Running the Service

### Local Development
```bash
# Start PostgreSQL
docker-compose up -d postgres

# Run the service
cd services/java/ticketing-service
mvn spring-boot:run
```

### Docker
```bash
# Build
docker build -t openride/ticketing-service .

# Run
docker run -p 8086:8086 \
  -e DB_HOST=postgres \
  -e POLYGON_RPC_URL=https://rpc-mumbai.maticvigil.com \
  -e POLYGON_WALLET_PRIVATE_KEY=your-key \
  openride/ticketing-service
```

## Testing

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=TicketServiceTest

# Run with coverage
mvn test jacoco:report
```

## Cryptographic Details

### Ticket Structure (Canonical JSON)
```json
{
  "bookingId": "uuid",
  "riderId": "uuid",
  "driverId": "uuid",
  "routeId": "uuid",
  "tripDate": "2024-01-15T08:00:00Z",
  "seatNumber": 1,
  "pickupStop": "Lekki Phase 1",
  "dropoffStop": "Victoria Island",
  "fare": 1500.00
}
```

### Signature Generation
1. Serialize ticket to canonical JSON (sorted keys)
2. Compute SHA-256 hash
3. Sign hash with ECDSA private key (secp256k1)
4. Encode signature as hex string

### Verification Process
1. Extract signature from QR code
2. Recompute ticket hash
3. Verify signature using public key
4. Check ticket status in database
5. Validate expiration time

### Merkle Tree Batching
1. Collect N tickets (default: 100)
2. Build Merkle tree from ticket hashes
3. Generate Merkle proofs for each ticket
4. Submit Merkle root to blockchain
5. Store proofs in database

## Security Considerations

- Private keys stored securely (Vault recommended)
- Public keys cached and distributed to drivers
- Signature verification can be performed offline
- Blockchain provides additional tamper evidence
- All verification attempts logged
- Rate limiting on verification endpoint

## Monitoring

### Metrics
- Tickets generated per minute
- Verification success/failure rate
- Average batch size
- Blockchain confirmation time
- Failed blockchain submissions

### Health Checks
- Database connectivity
- Blockchain RPC availability
- Key file access
- Merkle batch queue size

## License

Proprietary - OpenRide Platform
