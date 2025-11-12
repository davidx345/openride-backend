#!/bin/bash

# OpenRide Backend Setup Script
# This script sets up the development environment for OpenRide backend

set -e  # Exit on error

echo "🚀 OpenRide Backend Setup"
echo "=========================="
echo ""

# Check prerequisites
echo "📋 Checking prerequisites..."

# Check Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    exit 1
fi
echo "✅ Docker found"

# Check Docker Compose
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi
echo "✅ Docker Compose found"

# Check Java
if ! command -v java &> /dev/null; then
    echo "⚠️  Java is not installed. Java services will not work."
else
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        echo "⚠️  Java 17 or higher is required. Found: Java $JAVA_VERSION"
    else
        echo "✅ Java $JAVA_VERSION found"
    fi
fi

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo "⚠️  Maven is not installed. Java services will not build."
else
    echo "✅ Maven found"
fi

# Check Python
if ! command -v python3 &> /dev/null; then
    echo "⚠️  Python 3 is not installed. Python services will not work."
else
    PYTHON_VERSION=$(python3 --version | cut -d' ' -f2 | cut -d'.' -f1,2)
    echo "✅ Python $PYTHON_VERSION found"
fi

echo ""
echo "🔧 Setting up environment..."

# Create .env file if it doesn't exist
if [ ! -f .env ]; then
    echo "📝 Creating .env file from .env.example..."
    cp .env.example .env
    echo "✅ .env file created. Please update it with your actual values."
else
    echo "ℹ️  .env file already exists"
fi

echo ""
echo "🐳 Starting infrastructure services..."

# Start Docker Compose
docker-compose up -d

echo ""
echo "⏳ Waiting for services to be ready..."
sleep 10

# Check PostgreSQL
echo "🔍 Checking PostgreSQL..."
until docker exec openride-postgres pg_isready -U openride_user -d openride > /dev/null 2>&1; do
    echo "   Waiting for PostgreSQL..."
    sleep 2
done
echo "✅ PostgreSQL is ready"

# Check Redis
echo "🔍 Checking Redis..."
until docker exec openride-redis redis-cli -a openride_redis_password ping > /dev/null 2>&1; do
    echo "   Waiting for Redis..."
    sleep 2
done
echo "✅ Redis is ready"

echo ""
echo "📦 Installing shared libraries..."

# Install Java Commons
if command -v mvn &> /dev/null; then
    echo "🔨 Building Java Commons..."
    cd shared/java-commons
    mvn clean install -DskipTests
    cd ../..
    echo "✅ Java Commons installed"
else
    echo "⚠️  Skipping Java Commons (Maven not found)"
fi

# Install Python Commons
if command -v python3 &> /dev/null; then
    echo "🐍 Installing Python Commons..."
    cd shared/python-commons
    python3 -m pip install -e . --quiet
    cd ../..
    echo "✅ Python Commons installed"
else
    echo "⚠️  Skipping Python Commons (Python not found)"
fi

echo ""
echo "✨ Setup complete!"
echo ""
echo "📚 Next steps:"
echo "  1. Update .env with your actual configuration values"
echo "  2. Access pgAdmin: http://localhost:5050"
echo "     - Email: admin@openride.com"
echo "     - Password: admin"
echo "  3. Access Redis Commander: http://localhost:8081"
echo "  4. Read DEVELOPMENT.md for development guidelines"
echo "  5. Start building services (see BACKEND_IMPLEMENTATION_PLAN.md)"
echo ""
echo "🎉 Happy coding!"
