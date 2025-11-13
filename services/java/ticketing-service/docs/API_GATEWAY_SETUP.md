# API Gateway Configuration for OpenRide Ticketing Service

This document describes the API Gateway routing configuration for the Ticketing Service. Use Kong Gateway or NGINX as the API Gateway.

## Kong Gateway Configuration

### Route Configuration

Create Kong routes for ticketing endpoints:

```bash
# Create Ticketing Service
curl -i -X POST http://localhost:8001/services/ \
  --data name=ticketing-service \
  --data url='http://ticketing-service:8086'

# Route for ticket generation
curl -i -X POST http://localhost:8001/services/ticketing-service/routes \
  --data 'paths[]=/api/v1/tickets' \
  --data 'methods[]=POST' \
  --data name=ticket-generation

# Route for ticket retrieval
curl -i -X POST http://localhost:8001/services/ticketing-service/routes \
  --data 'paths[]=/api/v1/tickets' \
  --data 'methods[]=GET' \
  --data name=ticket-list

# Route for individual ticket operations
curl -i -X POST http://localhost:8001/services/ticketing-service/routes \
  --data 'paths[]=/api/v1/tickets/(.*)' \
  --data 'methods[]=GET' \
  --data 'methods[]=DELETE' \
  --data name=ticket-operations

# Route for ticket verification
curl -i -X POST http://localhost:8001/services/ticketing-service/routes \
  --data 'paths[]=/api/v1/tickets/verify' \
  --data 'methods[]=POST' \
  --data name=ticket-verification

# Route for Merkle proof
curl -i -X POST http://localhost:8001/services/ticketing-service/routes \
  --data 'paths[]=/api/v1/tickets/(.*)/proof' \
  --data 'methods[]=GET' \
  --data name=ticket-proof

# Route for QR code
curl -i -X POST http://localhost:8001/services/ticketing-service/routes \
  --data 'paths[]=/api/v1/tickets/(.*)/qr' \
  --data 'methods[]=GET' \
  --data name=ticket-qr
```

### JWT Authentication Plugin

Add JWT authentication to protect ticketing endpoints:

```bash
# Enable JWT plugin on ticket generation (requires authentication)
curl -i -X POST http://localhost:8001/routes/ticket-generation/plugins \
  --data name=jwt \
  --data config.secret_is_base64=false \
  --data config.key_claim_name=kid

# Enable JWT plugin on ticket operations
curl -i -X POST http://localhost:8001/routes/ticket-operations/plugins \
  --data name=jwt

# Enable JWT plugin on ticket verification
curl -i -X POST http://localhost:8001/routes/ticket-verification/plugins \
  --data name=jwt
```

### Rate Limiting Plugin

Add rate limiting to prevent abuse:

```bash
# Rate limit ticket generation (100 requests per minute per user)
curl -i -X POST http://localhost:8001/routes/ticket-generation/plugins \
  --data name=rate-limiting \
  --data config.minute=100 \
  --data config.policy=local \
  --data config.limit_by=consumer

# Rate limit ticket verification (200 requests per minute for drivers)
curl -i -X POST http://localhost:8001/routes/ticket-verification/plugins \
  --data name=rate-limiting \
  --data config.minute=200 \
  --data config.policy=local

# Rate limit QR code generation (50 requests per minute)
curl -i -X POST http://localhost:8001/routes/ticket-qr/plugins \
  --data name=rate-limiting \
  --data config.minute=50 \
  --data config.policy=local
```

### CORS Plugin

Enable CORS for web/mobile clients:

```bash
curl -i -X POST http://localhost:8001/services/ticketing-service/plugins \
  --data name=cors \
  --data config.origins=* \
  --data config.methods=GET,POST,DELETE \
  --data config.headers=Accept,Authorization,Content-Type \
  --data config.exposed_headers=X-Auth-Token \
  --data config.credentials=true \
  --data config.max_age=3600
```

### Request/Response Logging

Enable logging for debugging:

```bash
curl -i -X POST http://localhost:8001/services/ticketing-service/plugins \
  --data name=file-log \
  --data config.path=/var/log/kong/ticketing-service.log
```

## NGINX Configuration

Alternative configuration using NGINX:

```nginx
# /etc/nginx/conf.d/ticketing-service.conf

upstream ticketing_backend {
    server ticketing-service:8086;
}

# Rate limiting zones
limit_req_zone $binary_remote_addr zone=ticket_gen:10m rate=100r/m;
limit_req_zone $binary_remote_addr zone=ticket_verify:10m rate=200r/m;
limit_req_zone $binary_remote_addr zone=ticket_qr:10m rate=50r/m;

server {
    listen 80;
    server_name api.openride.local;

    # Ticket generation endpoint
    location /api/v1/tickets {
        limit_req zone=ticket_gen burst=10 nodelay;
        
        # JWT validation (using auth_request)
        auth_request /auth;
        
        proxy_pass http://ticketing_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Timeouts
        proxy_connect_timeout 5s;
        proxy_send_timeout 10s;
        proxy_read_timeout 10s;
    }

    # Ticket verification endpoint
    location /api/v1/tickets/verify {
        limit_req zone=ticket_verify burst=20 nodelay;
        
        auth_request /auth;
        
        proxy_pass http://ticketing_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # QR code endpoint
    location ~ ^/api/v1/tickets/([^/]+)/qr$ {
        limit_req zone=ticket_qr burst=5 nodelay;
        
        auth_request /auth;
        
        proxy_pass http://ticketing_backend;
        proxy_set_header Host $host;
    }

    # Merkle proof endpoint
    location ~ ^/api/v1/tickets/([^/]+)/proof$ {
        auth_request /auth;
        
        proxy_pass http://ticketing_backend;
        proxy_set_header Host $host;
    }

    # Individual ticket operations
    location ~ ^/api/v1/tickets/([^/]+)$ {
        auth_request /auth;
        
        proxy_pass http://ticketing_backend;
        proxy_set_header Host $host;
    }

    # JWT validation endpoint (internal)
    location /auth {
        internal;
        proxy_pass http://auth-service:8081/api/v1/auth/validate;
        proxy_pass_request_body off;
        proxy_set_header Content-Length "";
        proxy_set_header X-Original-URI $request_uri;
        proxy_set_header Authorization $http_authorization;
    }

    # CORS headers
    add_header 'Access-Control-Allow-Origin' '*' always;
    add_header 'Access-Control-Allow-Methods' 'GET, POST, DELETE, OPTIONS' always;
    add_header 'Access-Control-Allow-Headers' 'Authorization, Content-Type' always;
    add_header 'Access-Control-Max-Age' '3600' always;

    # Handle preflight
    if ($request_method = 'OPTIONS') {
        return 204;
    }
}
```

## Environment Variables

Add these environment variables to your API Gateway configuration:

```bash
# Kong
KONG_DATABASE=postgres
KONG_PG_HOST=postgres
KONG_PG_PORT=5432
KONG_PG_USER=kong
KONG_PG_PASSWORD=kong
KONG_PROXY_ACCESS_LOG=/dev/stdout
KONG_ADMIN_ACCESS_LOG=/dev/stdout
KONG_PROXY_ERROR_LOG=/dev/stderr
KONG_ADMIN_ERROR_LOG=/dev/stderr

# Service Discovery
TICKETING_SERVICE_URL=http://ticketing-service:8086
AUTH_SERVICE_URL=http://auth-service:8081
```

## Health Checks

Configure health checks for ticketing service:

```bash
# Kong health check
curl -i -X POST http://localhost:8001/services/ticketing-service/plugins \
  --data name=request-termination \
  --data config.status_code=503 \
  --data config.message="Ticketing service unavailable"
```

## Monitoring & Metrics

Enable Prometheus metrics:

```bash
# Kong Prometheus plugin
curl -i -X POST http://localhost:8001/services/ticketing-service/plugins \
  --data name=prometheus
```

Access metrics at: `http://kong:8001/metrics`

## Testing Gateway Configuration

Test the routing:

```bash
# Generate ticket (requires JWT)
curl -X POST http://localhost:8000/api/v1/tickets \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "bookingId": "booking-123",
    "userId": "user-456",
    "driverId": "driver-789"
  }'

# Verify ticket
curl -X POST http://localhost:8000/api/v1/tickets/verify \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "ticketId": "ticket-123",
    "driverId": "driver-789"
  }'

# Get QR code
curl -X GET http://localhost:8000/api/v1/tickets/ticket-123/qr \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## Security Considerations

1. **JWT Validation**: All endpoints require valid JWT tokens
2. **Rate Limiting**: Prevents DDoS and abuse
3. **HTTPS**: Use TLS in production (terminate at gateway)
4. **IP Whitelisting**: Restrict admin endpoints to internal IPs
5. **Request Size Limits**: Prevent large payload attacks
6. **Timeout Configuration**: Prevent slow loris attacks

## Deployment Checklist

- [ ] Kong/NGINX deployed and running
- [ ] Database configured for Kong (if using Kong)
- [ ] Service routes configured
- [ ] JWT authentication enabled
- [ ] Rate limiting configured
- [ ] CORS enabled for web clients
- [ ] Health checks configured
- [ ] Logging enabled
- [ ] Metrics collection enabled
- [ ] SSL/TLS certificates installed
- [ ] Firewall rules configured

## Troubleshooting

### Route Not Working

```bash
# Check Kong routes
curl http://localhost:8001/routes

# Check Kong services
curl http://localhost:8001/services

# Check Kong plugins
curl http://localhost:8001/plugins
```

### JWT Authentication Failing

```bash
# Verify JWT plugin configuration
curl http://localhost:8001/routes/ticket-generation/plugins

# Check auth service connectivity
curl http://auth-service:8081/health
```

### Rate Limiting Issues

```bash
# Check rate limit plugin config
curl http://localhost:8001/plugins | grep rate-limiting

# View rate limit counters (Kong)
curl http://localhost:8001/cache
```
