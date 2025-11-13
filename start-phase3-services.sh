#!/bin/bash

# Phase 3 Services Startup Script
# Starts Matchmaking Service and Search & Discovery Service

set -e

echo "=========================================="
echo "Starting OpenRide Phase 3 Services"
echo "=========================================="
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if PostgreSQL is running
echo -e "${YELLOW}Checking PostgreSQL...${NC}"
if ! pg_isready -h localhost -p 5432 > /dev/null 2>&1; then
    echo -e "${RED}✗ PostgreSQL is not running${NC}"
    echo "Please start PostgreSQL first"
    exit 1
fi
echo -e "${GREEN}✓ PostgreSQL is running${NC}"

# Check if Redis is running
echo -e "${YELLOW}Checking Redis...${NC}"
if ! redis-cli ping > /dev/null 2>&1; then
    echo -e "${RED}✗ Redis is not running${NC}"
    echo "Please start Redis first"
    exit 1
fi
echo -e "${GREEN}✓ Redis is running${NC}"

# Check if PostGIS extension exists
echo -e "${YELLOW}Checking PostGIS...${NC}"
if ! psql -d openride -c "SELECT PostGIS_Version();" > /dev/null 2>&1; then
    echo -e "${RED}✗ PostGIS extension not found${NC}"
    echo "Installing PostGIS extension..."
    psql -d openride -c "CREATE EXTENSION IF NOT EXISTS postgis;"
fi
echo -e "${GREEN}✓ PostGIS is ready${NC}"

echo ""
echo "=========================================="
echo "Running Database Migration"
echo "=========================================="
echo ""

cd services/python/matchmaking-service

# Check if migration is needed
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}Creating .env file from example...${NC}"
    cp .env.example .env
    echo -e "${RED}⚠ Please edit .env file with your configuration${NC}"
    echo "Then run this script again"
    exit 1
fi

# Install dependencies if needed
if [ ! -d ".venv" ]; then
    echo -e "${YELLOW}Installing matchmaking-service dependencies...${NC}"
    poetry install
fi

# Run migration
echo -e "${YELLOW}Running database migration...${NC}"
poetry run alembic upgrade head
echo -e "${GREEN}✓ Migration complete${NC}"

cd ../..

echo ""
echo "=========================================="
echo "Starting Matchmaking Service (Port 8084)"
echo "=========================================="
echo ""

cd services/python/matchmaking-service

# Start in background
poetry run uvicorn app.main:app --host 0.0.0.0 --port 8084 > /tmp/matchmaking-service.log 2>&1 &
MATCHMAKING_PID=$!

echo -e "${GREEN}✓ Matchmaking Service started (PID: $MATCHMAKING_PID)${NC}"
echo "Logs: /tmp/matchmaking-service.log"

cd ../..

echo ""
echo "=========================================="
echo "Starting Search Service (Port 8085)"
echo "=========================================="
echo ""

cd services/python/search-service

# Setup if needed
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}Creating .env file from example...${NC}"
    cp .env.example .env
fi

if [ ! -d ".venv" ]; then
    echo -e "${YELLOW}Installing search-service dependencies...${NC}"
    poetry install
fi

# Start in background
poetry run uvicorn app.main:app --host 0.0.0.0 --port 8085 > /tmp/search-service.log 2>&1 &
SEARCH_PID=$!

echo -e "${GREEN}✓ Search Service started (PID: $SEARCH_PID)${NC}"
echo "Logs: /tmp/search-service.log"

cd ../..

# Wait for services to start
echo ""
echo -e "${YELLOW}Waiting for services to start...${NC}"
sleep 3

# Health checks
echo ""
echo "=========================================="
echo "Health Checks"
echo "=========================================="
echo ""

echo -e "${YELLOW}Checking Matchmaking Service...${NC}"
if curl -s http://localhost:8084/health | grep -q "healthy"; then
    echo -e "${GREEN}✓ Matchmaking Service is healthy${NC}"
else
    echo -e "${RED}✗ Matchmaking Service health check failed${NC}"
fi

echo -e "${YELLOW}Checking Search Service...${NC}"
if curl -s http://localhost:8085/health | grep -q "healthy"; then
    echo -e "${GREEN}✓ Search Service is healthy${NC}"
else
    echo -e "${RED}✗ Search Service health check failed${NC}"
fi

echo ""
echo "=========================================="
echo "Phase 3 Services Started Successfully! 🚀"
echo "=========================================="
echo ""
echo "Service URLs:"
echo "  Matchmaking Service: http://localhost:8084"
echo "  Search Service:      http://localhost:8085"
echo ""
echo "API Documentation:"
echo "  Matchmaking: http://localhost:8084/docs"
echo "  Search:      http://localhost:8085/docs"
echo ""
echo "Process IDs:"
echo "  Matchmaking PID: $MATCHMAKING_PID"
echo "  Search PID:      $SEARCH_PID"
echo ""
echo "To stop services:"
echo "  kill $MATCHMAKING_PID $SEARCH_PID"
echo ""
echo "View logs:"
echo "  tail -f /tmp/matchmaking-service.log"
echo "  tail -f /tmp/search-service.log"
echo ""
