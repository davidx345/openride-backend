#!/bin/bash

# ===================================
# OpenRIDE - Python Services Deployment Script
# ===================================
# Deploy Python microservices to the Python Droplet
# Run this from the deployment directory

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEPLOYMENT_DIR="$PROJECT_ROOT/deployment"

echo "🚀 Deploying Python Services..."

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
docker-compose -f docker-compose.python.yml pull

echo "🛑 Stopping existing services..."
docker-compose -f docker-compose.python.yml down

echo "🚀 Starting services..."
docker-compose -f docker-compose.python.yml up -d

echo "⏳ Waiting for services to be healthy..."
sleep 30

echo "🏥 Checking service health..."
docker-compose -f docker-compose.python.yml ps

echo "📊 Service logs (last 20 lines):"
docker-compose -f docker-compose.python.yml logs --tail=20

echo ""
echo "✅ Python Services deployed successfully!"
echo ""
echo "Service URLs:"
echo "  Analytics Service:     http://localhost:8097"
echo "  Driver Service:        http://localhost:8090"
echo "  Fleet Service:         http://localhost:8096"
echo "  Matchmaking Service:   http://localhost:8091"
echo "  Notification Service:  http://localhost:8095"
echo "  Search Service:        http://localhost:8092"
echo ""
echo "View logs: docker-compose -f docker-compose.python.yml logs -f [service-name]"
echo "Stop services: docker-compose -f docker-compose.python.yml down"
echo ""
