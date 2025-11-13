#!/bin/bash

# Phase 4 - Booking Service Startup Script
# This script starts the Booking Service with all dependencies

set -e

echo "🚀 Starting OpenRide Booking Service (Phase 4)"
echo "================================================"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check prerequisites
echo -e "\n${YELLOW}Checking prerequisites...${NC}"

if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker not found. Please install Docker.${NC}"
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java not found. Please install Java 21+.${NC}"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven not found. Please install Maven 3.9+.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ All prerequisites found${NC}"

# Start infrastructure
echo -e "\n${YELLOW}Starting infrastructure (PostgreSQL, Redis, Kafka)...${NC}"
cd infrastructure
docker-compose up -d postgres redis kafka

# Wait for services to be ready
echo -e "\n${YELLOW}Waiting for services to be ready...${NC}"
sleep 10

# Check if PostgreSQL is ready
until docker exec $(docker ps -qf "name=postgres") pg_isready -U postgres &> /dev/null; do
    echo "Waiting for PostgreSQL..."
    sleep 2
done
echo -e "${GREEN}✅ PostgreSQL ready${NC}"

# Check if Redis is ready
until docker exec $(docker ps -qf "name=redis") redis-cli ping &> /dev/null; do
    echo "Waiting for Redis..."
    sleep 2
done
echo -e "${GREEN}✅ Redis ready${NC}"

# Check if Kafka is ready
until docker exec $(docker ps -qf "name=kafka") kafka-topics.sh --bootstrap-server localhost:9092 --list &> /dev/null; do
    echo "Waiting for Kafka..."
    sleep 2
done
echo -e "${GREEN}✅ Kafka ready${NC}"

# Create Kafka topics
echo -e "\n${YELLOW}Creating Kafka topics...${NC}"
docker exec $(docker ps -qf "name=kafka") kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic booking.created --partitions 3 --replication-factor 1
docker exec $(docker ps -qf "name=kafka") kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic booking.confirmed --partitions 3 --replication-factor 1
docker exec $(docker ps -qf "name=kafka") kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic booking.cancelled --partitions 3 --replication-factor 1
docker exec $(docker ps -qf "name=kafka") kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic booking.completed --partitions 3 --replication-factor 1
echo -e "${GREEN}✅ Kafka topics created${NC}"

# Build and start Booking Service
echo -e "\n${YELLOW}Building Booking Service...${NC}"
cd ../services/java/booking-service

mvn clean install -DskipTests

echo -e "\n${YELLOW}Starting Booking Service...${NC}"
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=openride_booking
export DB_USER=postgres
export DB_PASSWORD=postgres
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_DB=3
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export JWT_SECRET=your-secret-key-change-in-production

mvn spring-boot:run &

# Wait for service to start
echo -e "\n${YELLOW}Waiting for Booking Service to start...${NC}"
sleep 15

# Health check
until curl -s http://localhost:8083/api/actuator/health > /dev/null; do
    echo "Waiting for Booking Service..."
    sleep 2
done

echo -e "\n${GREEN}================================================${NC}"
echo -e "${GREEN}✅ Booking Service is running!${NC}"
echo -e "${GREEN}================================================${NC}"
echo -e "\n📊 Service Information:"
echo -e "  - Booking Service: http://localhost:8083/api"
echo -e "  - Swagger UI: http://localhost:8083/api/swagger-ui.html"
echo -e "  - Health Check: http://localhost:8083/api/actuator/health"
echo -e "  - Metrics: http://localhost:8083/api/actuator/prometheus"
echo -e "\n🗄️ Database:"
echo -e "  - PostgreSQL: localhost:5432/openride_booking"
echo -e "  - Redis: localhost:6379 (DB 3)"
echo -e "\n📡 Kafka Topics:"
echo -e "  - booking.created"
echo -e "  - booking.confirmed"
echo -e "  - booking.cancelled"
echo -e "  - booking.completed"
echo -e "\n💡 Next Steps:"
echo -e "  1. Open Swagger UI to explore the API"
echo -e "  2. Create a booking: POST /api/v1/bookings"
echo -e "  3. Monitor Kafka events with: docker exec -it kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic booking.created --from-beginning"
echo -e "\n🛑 To stop all services: docker-compose down"
echo -e "${GREEN}================================================${NC}\n"
