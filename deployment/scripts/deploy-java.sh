#!/bin/bash

# ===================================
# OpenRIDE - Java Services Deployment Script
# ===================================
# Deploy Java microservices to the Java Droplet
# Run this from the deployment directory

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEPLOYMENT_DIR="$PROJECT_ROOT/deployment"

echo "🚀 Deploying Java Services..."

# Check if .env exists in deployment directory
if [ ! -f "$DEPLOYMENT_DIR/.env" ]; then
    echo "⚠️  Warning: .env file not found in deployment/"
    echo "Checking for .env in project root..."
    if [ -f "$PROJECT_ROOT/.env" ]; then
        echo "✅ Found .env in project root, copying to deployment/"
        cp "$PROJECT_ROOT/.env" "$DEPLOYMENT_DIR/.env"
    else
        echo "❌ Error: .env file not found!"
        echo "Copy .env.example to $DEPLOYMENT_DIR/.env and fill in your configuration"
        echo "Or create it in $PROJECT_ROOT/.env"
        exit 1
    fi
fi

# Load environment variables
set -a
source "$DEPLOYMENT_DIR/.env"
set +a

cd "$DEPLOYMENT_DIR"

echo "📦 Pulling latest images..."
docker-compose -f docker-compose.java.yml pull

echo "🛑 Stopping existing services..."
docker-compose -f docker-compose.java.yml down

echo "🚀 Starting services..."
docker-compose -f docker-compose.java.yml up -d

echo "⏳ Waiting for services to be healthy..."
sleep 30

echo "🏥 Checking service health..."
docker-compose -f docker-compose.java.yml ps

echo "📊 Service logs (last 20 lines):"
docker-compose -f docker-compose.java.yml logs --tail=20

echo ""
echo "✅ Java Services deployed successfully!"
echo ""
echo "Service URLs:"
echo "  Auth Service:      http://localhost:8081"
echo "  User Service:      http://localhost:8082"
echo "  Booking Service:   http://localhost:8083"
echo "  Payments Service:  http://localhost:8084"
echo "  Payouts Service:   http://localhost:8085"
echo "  Ticketing Service: http://localhost:8086"
echo "  Admin Service:     http://localhost:8087"
echo ""
echo "View logs: docker-compose -f docker-compose.java.yml logs -f [service-name]"
echo "Stop services: docker-compose -f docker-compose.java.yml down"
echo ""
