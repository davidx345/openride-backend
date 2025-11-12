@echo off
REM OpenRide Backend Setup Script for Windows
REM This script sets up the development environment for OpenRide backend

echo.
echo OpenRide Backend Setup
echo ======================
echo.

echo Checking prerequisites...
echo.

REM Check Docker
where docker >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Docker is not installed. Please install Docker Desktop first.
    pause
    exit /b 1
)
echo [OK] Docker found

REM Check Docker Compose
where docker-compose >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Docker Compose is not installed. Please install Docker Compose first.
    pause
    exit /b 1
)
echo [OK] Docker Compose found

REM Check Java
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [WARNING] Java is not installed. Java services will not work.
) else (
    echo [OK] Java found
)

REM Check Maven
where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo [WARNING] Maven is not installed. Java services will not build.
) else (
    echo [OK] Maven found
)

REM Check Python
where python >nul 2>nul
if %errorlevel% neq 0 (
    echo [WARNING] Python is not installed. Python services will not work.
) else (
    echo [OK] Python found
)

echo.
echo Setting up environment...
echo.

REM Create .env file if it doesn't exist
if not exist .env (
    echo Creating .env file from .env.example...
    copy .env.example .env
    echo [OK] .env file created. Please update it with your actual values.
) else (
    echo [INFO] .env file already exists
)

echo.
echo Starting infrastructure services...
echo.

REM Start Docker Compose
docker-compose up -d

echo.
echo Waiting for services to be ready...
timeout /t 10 /nobreak >nul

echo.
echo Checking PostgreSQL...
:wait_postgres
docker exec openride-postgres pg_isready -U openride_user -d openride >nul 2>&1
if %errorlevel% neq 0 (
    echo    Waiting for PostgreSQL...
    timeout /t 2 /nobreak >nul
    goto wait_postgres
)
echo [OK] PostgreSQL is ready

echo.
echo Checking Redis...
:wait_redis
docker exec openride-redis redis-cli -a openride_redis_password ping >nul 2>&1
if %errorlevel% neq 0 (
    echo    Waiting for Redis...
    timeout /t 2 /nobreak >nul
    goto wait_redis
)
echo [OK] Redis is ready

echo.
echo Installing shared libraries...
echo.

REM Install Java Commons
where mvn >nul 2>nul
if %errorlevel% equ 0 (
    echo Building Java Commons...
    cd shared\java-commons
    call mvn clean install -DskipTests
    cd ..\..
    echo [OK] Java Commons installed
) else (
    echo [WARNING] Skipping Java Commons (Maven not found)
)

REM Install Python Commons
where python >nul 2>nul
if %errorlevel% equ 0 (
    echo Installing Python Commons...
    cd shared\python-commons
    python -m pip install -e . --quiet
    cd ..\..
    echo [OK] Python Commons installed
) else (
    echo [WARNING] Skipping Python Commons (Python not found)
)

echo.
echo ======================================
echo Setup complete!
echo ======================================
echo.
echo Next steps:
echo   1. Update .env with your actual configuration values
echo   2. Access pgAdmin: http://localhost:5050
echo      - Email: admin@openride.com
echo      - Password: admin
echo   3. Access Redis Commander: http://localhost:8081
echo   4. Read DEVELOPMENT.md for development guidelines
echo   5. Start building services (see BACKEND_IMPLEMENTATION_PLAN.md)
echo.
echo Happy coding!
echo.
pause
