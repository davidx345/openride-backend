#!/bin/bash

# Driver Service Startup Script

set -e

echo "🚀 Starting OpenRide Driver Service..."

# Change to driver service directory
cd "$(dirname "$0")"

# Check if poetry is installed
if ! command -v poetry &> /dev/null; then
    echo "❌ Poetry is not installed. Please install it first:"
    echo "   curl -sSL https://install.python-poetry.org | python3 -"
    exit 1
fi

# Install dependencies if needed
if [ ! -d ".venv" ]; then
    echo "📦 Installing dependencies..."
    poetry install
fi

# Check if .env exists
if [ ! -f ".env" ]; then
    echo "⚠️  .env file not found. Copying from .env.example..."
    cp .env.example .env
    echo "✏️  Please edit .env file with your configuration"
fi

# Check database connection
echo "🔍 Checking database connection..."
if ! poetry run python -c "import asyncio; from app.core.database import engine; asyncio.run(engine.dispose())" 2>/dev/null; then
    echo "⚠️  Warning: Could not connect to database. Make sure PostgreSQL is running."
    echo "   You may need to run: docker-compose up -d postgres"
fi

# Run migrations
echo "🗄️  Running database migrations..."
poetry run alembic upgrade head

# Start the service
echo "✅ Starting Driver Service on port 8082..."
poetry run uvicorn app.main:app --reload --port 8082 --host 0.0.0.0
