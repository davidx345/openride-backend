@echo off
REM Driver Service Startup Script for Windows

echo Starting OpenRide Driver Service...

cd /d "%~dp0"

REM Check if poetry is installed
where poetry >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Poetry is not installed. Please install it first:
    echo    curl -sSL https://install.python-poetry.org ^| python -
    exit /b 1
)

REM Install dependencies if needed
if not exist ".venv" (
    echo Installing dependencies...
    poetry install
)

REM Check if .env exists
if not exist ".env" (
    echo .env file not found. Copying from .env.example...
    copy .env.example .env
    echo Please edit .env file with your configuration
)

REM Run migrations
echo Running database migrations...
poetry run alembic upgrade head

REM Start the service
echo Starting Driver Service on port 8082...
poetry run uvicorn app.main:app --reload --port 8082 --host 0.0.0.0
