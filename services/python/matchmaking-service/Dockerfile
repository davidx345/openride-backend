FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    postgresql-client \
    && rm -rf /var/lib/apt/lists/*

# Install Poetry
RUN pip install poetry==1.7.1

# Copy dependency files
COPY services/python/matchmaking-service/pyproject.toml ./

# Install dependencies (no dev dependencies in production)
RUN poetry config virtualenvs.create false \
    && poetry install --no-dev --no-interaction --no-ansi

# Copy application code
COPY services/python/matchmaking-service/app/ ./app/
COPY services/python/matchmaking-service/alembic/ ./alembic/
COPY services/python/matchmaking-service/alembic.ini ./

# Expose port
EXPOSE 8084

# Run migrations and start server
CMD ["sh", "-c", "alembic upgrade head && uvicorn app.main:app --host 0.0.0.0 --port 8084"]
