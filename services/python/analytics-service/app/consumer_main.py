"""Kafka consumer entry point.

Run this script to start the Kafka consumer:
    python -m app.consumer_main
"""

if __name__ == "__main__":
    from app.consumer import start_consumer
    from app.core.logging import configure_logging

    # Configure logging
    configure_logging()

    # Start consumer
    start_consumer()
