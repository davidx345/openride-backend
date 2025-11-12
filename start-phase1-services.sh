#!/bin/bash

# Phase 1 Services Startup Script
# Starts Auth Service and User Service for OpenRide backend

set -e

echo "=========================================="
echo "OpenRide Phase 1 - Service Startup"
echo "=========================================="

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check prerequisites
echo -e "\n${YELLOW}Checking prerequisites...${NC}"

if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java not found. Please install Java 17+${NC}"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven not found. Please install Maven 3.9+${NC}"
    exit 1
fi

if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker not found. Please install Docker${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Prerequisites check passed${NC}"

# Check if Docker Compose is running
echo -e "\n${YELLOW}Checking Docker infrastructure...${NC}"

if ! docker ps | grep -q openride-postgres; then
    echo -e "${YELLOW}PostgreSQL not running. Starting Docker Compose...${NC}"
    docker-compose up -d
    echo "Waiting 10 seconds for services to start..."
    sleep 10
fi

if docker ps | grep -q openride-postgres && docker ps | grep -q openride-redis; then
    echo -e "${GREEN}✓ PostgreSQL and Redis are running${NC}"
else
    echo -e "${RED}Error: Infrastructure services not running${NC}"
    exit 1
fi

# Install shared commons
echo -e "\n${YELLOW}Installing shared Java commons library...${NC}"
cd shared/java-commons
mvn clean install -q
echo -e "${GREEN}✓ Java commons installed${NC}"

# Build and run Auth Service
echo -e "\n${YELLOW}Building Auth Service...${NC}"
cd ../../services/java/auth-service
mvn clean package -DskipTests -q
echo -e "${GREEN}✓ Auth Service built${NC}"

echo -e "${YELLOW}Running Flyway migrations for Auth Service...${NC}"
mvn flyway:migrate -q
echo -e "${GREEN}✓ Auth Service migrations complete${NC}"

echo -e "${YELLOW}Starting Auth Service on port 8081...${NC}"
nohup mvn spring-boot:run > auth-service.log 2>&1 &
AUTH_PID=$!
echo -e "${GREEN}✓ Auth Service started (PID: $AUTH_PID)${NC}"

# Build and run User Service
echo -e "\n${YELLOW}Building User Service...${NC}"
cd ../user-service
mvn clean package -DskipTests -q
echo -e "${GREEN}✓ User Service built${NC}"

echo -e "${YELLOW}Running Flyway migrations for User Service...${NC}"
mvn flyway:migrate -q
echo -e "${GREEN}✓ User Service migrations complete${NC}"

echo -e "${YELLOW}Starting User Service on port 8082...${NC}"
nohup mvn spring-boot:run > user-service.log 2>&1 &
USER_PID=$!
echo -e "${GREEN}✓ User Service started (PID: $USER_PID)${NC}"

# Wait for services to be ready
echo -e "\n${YELLOW}Waiting for services to be ready...${NC}"
sleep 15

# Health checks
echo -e "\n${YELLOW}Checking service health...${NC}"

AUTH_HEALTH=$(curl -s http://localhost:8081/api/actuator/health | grep -o '"status":"UP"' || echo "DOWN")
USER_HEALTH=$(curl -s http://localhost:8082/api/actuator/health | grep -o '"status":"UP"' || echo "DOWN")

if [ "$AUTH_HEALTH" == '"status":"UP"' ]; then
    echo -e "${GREEN}✓ Auth Service is healthy${NC}"
else
    echo -e "${RED}✗ Auth Service health check failed${NC}"
fi

if [ "$USER_HEALTH" == '"status":"UP"' ]; then
    echo -e "${GREEN}✓ User Service is healthy${NC}"
else
    echo -e "${RED}✗ User Service health check failed${NC}"
fi

# Summary
echo -e "\n=========================================="
echo -e "${GREEN}Phase 1 Services Started Successfully!${NC}"
echo "=========================================="
echo ""
echo "Service URLs:"
echo "  Auth Service:      http://localhost:8081/api"
echo "  User Service:      http://localhost:8082/api"
echo ""
echo "Swagger Documentation:"
echo "  Auth Service:      http://localhost:8081/api/swagger-ui.html"
echo "  User Service:      http://localhost:8082/api/swagger-ui.html"
echo ""
echo "Infrastructure:"
echo "  PostgreSQL:        localhost:5432"
echo "  Redis:             localhost:6379"
echo "  pgAdmin:           http://localhost:5050"
echo "  Redis Commander:   http://localhost:8081"
echo ""
echo "Logs:"
echo "  Auth Service:      services/java/auth-service/auth-service.log"
echo "  User Service:      services/java/user-service/user-service.log"
echo ""
echo "Process IDs:"
echo "  Auth Service:      $AUTH_PID"
echo "  User Service:      $USER_PID"
echo ""
echo "To stop services:"
echo "  kill $AUTH_PID $USER_PID"
echo ""
echo "=========================================="
