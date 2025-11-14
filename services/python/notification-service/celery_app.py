"""Celery application configuration."""
from celery import Celery
from celery.schedules import crontab
from app.config import settings

# Create Celery app
celery_app = Celery(
    "notification_service",
    broker=settings.celery_broker_url,
    backend=settings.celery_result_backend,
)

# Celery configuration
celery_app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="UTC",
    enable_utc=True,
    task_track_started=True,
    task_time_limit=300,  # 5 minutes
    task_soft_time_limit=240,  # 4 minutes
    worker_prefetch_multiplier=4,
    worker_max_tasks_per_child=1000,
    broker_connection_retry_on_startup=True,
)

# Task routing
celery_app.conf.task_routes = {
    "app.tasks.notification_tasks.send_notification_async": {"queue": "notifications"},
    "app.tasks.notification_tasks.send_broadcast_async": {"queue": "broadcasts"},
    "app.tasks.notification_tasks.retry_failed_notifications": {"queue": "retries"},
    "app.tasks.notification_tasks.cleanup_old_notifications": {"queue": "maintenance"},
}

# Scheduled tasks (Celery Beat)
celery_app.conf.beat_schedule = {
    "retry-failed-notifications": {
        "task": "app.tasks.notification_tasks.retry_failed_notifications",
        "schedule": crontab(minute="*/10"),  # Every 10 minutes
    },
    "cleanup-old-notifications": {
        "task": "app.tasks.notification_tasks.cleanup_old_notifications",
        "schedule": crontab(hour=2, minute=0),  # Daily at 2 AM
        "kwargs": {"days": 90},
    },
}

# Auto-discover tasks
celery_app.autodiscover_tasks(["app.tasks"])

if __name__ == "__main__":
    celery_app.start()
