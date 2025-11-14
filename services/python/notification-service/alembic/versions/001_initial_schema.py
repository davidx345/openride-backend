"""Initial schema for notification service

Revision ID: 001
Revises: 
Create Date: 2025-11-14 10:00:00.000000

"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision = '001'
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Create enums
    notification_channel_enum = postgresql.ENUM(
        'push', 'sms', 'email',
        name='notificationchannel',
        create_type=False
    )
    notification_channel_enum.create(op.get_bind(), checkfirst=True)

    notification_status_enum = postgresql.ENUM(
        'pending', 'sent', 'delivered', 'failed', 'retrying',
        name='notificationstatus',
        create_type=False
    )
    notification_status_enum.create(op.get_bind(), checkfirst=True)

    notification_priority_enum = postgresql.ENUM(
        'low', 'medium', 'high', 'urgent',
        name='notificationpriority',
        create_type=False
    )
    notification_priority_enum.create(op.get_bind(), checkfirst=True)

    notification_type_enum = postgresql.ENUM(
        'booking_confirmed', 'payment_success', 'payment_failed',
        'driver_arriving', 'trip_started', 'trip_completed',
        'booking_cancelled', 'route_cancelled', 'kyc_approved',
        'kyc_rejected', 'payout_processed', 'custom',
        name='notificationtype',
        create_type=False
    )
    notification_type_enum.create(op.get_bind(), checkfirst=True)

    # Create notification_templates table
    op.create_table(
        'notification_templates',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column('template_key', sa.String(100), unique=True, nullable=False),
        sa.Column('notification_type', notification_type_enum, nullable=False),
        sa.Column('channel', notification_channel_enum, nullable=False),
        sa.Column('subject_template', sa.String(255), nullable=True),
        sa.Column('body_template', sa.Text, nullable=False),
        sa.Column('is_active', sa.Boolean, default=True, nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now()),
        sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.func.now(), onupdate=sa.func.now()),
    )
    op.create_index('idx_template_key', 'notification_templates', ['template_key'])
    op.create_index('idx_template_type_channel', 'notification_templates', ['notification_type', 'channel'])

    # Create user_notification_preferences table
    op.create_table(
        'user_notification_preferences',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column('user_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('push_enabled', sa.Boolean, default=True, nullable=False),
        sa.Column('sms_enabled', sa.Boolean, default=True, nullable=False),
        sa.Column('email_enabled', sa.Boolean, default=True, nullable=False),
        sa.Column('booking_notifications', sa.Boolean, default=True, nullable=False),
        sa.Column('trip_notifications', sa.Boolean, default=True, nullable=False),
        sa.Column('payment_notifications', sa.Boolean, default=True, nullable=False),
        sa.Column('marketing_notifications', sa.Boolean, default=False, nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now()),
        sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.func.now(), onupdate=sa.func.now()),
    )
    op.create_index('idx_user_preferences', 'user_notification_preferences', ['user_id'], unique=True)

    # Create fcm_tokens table
    op.create_table(
        'fcm_tokens',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column('user_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('token', sa.String(255), nullable=False),
        sa.Column('device_type', sa.String(20), nullable=False),
        sa.Column('device_id', sa.String(255), nullable=True),
        sa.Column('is_active', sa.Boolean, default=True, nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now()),
        sa.Column('last_used_at', sa.DateTime(timezone=True), nullable=True),
    )
    op.create_index('idx_fcm_user', 'fcm_tokens', ['user_id'])
    op.create_index('idx_fcm_token', 'fcm_tokens', ['token'], unique=True)

    # Create notification_logs table
    op.create_table(
        'notification_logs',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column('user_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('notification_type', notification_type_enum, nullable=False),
        sa.Column('channel', notification_channel_enum, nullable=False),
        sa.Column('status', notification_status_enum, default='pending', nullable=False),
        sa.Column('priority', notification_priority_enum, default='medium', nullable=False),
        sa.Column('recipient_address', sa.String(255), nullable=False),
        sa.Column('subject', sa.String(255), nullable=True),
        sa.Column('body', sa.Text, nullable=False),
        sa.Column('data', postgresql.JSONB, nullable=True),
        sa.Column('sent_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('delivered_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('failed_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('error_message', sa.Text, nullable=True),
        sa.Column('retry_count', sa.Integer, default=0, nullable=False),
        sa.Column('max_retries', sa.Integer, default=3, nullable=False),
        sa.Column('next_retry_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('provider_message_id', sa.String(255), nullable=True),
        sa.Column('provider_response', postgresql.JSONB, nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now()),
        sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.func.now(), onupdate=sa.func.now()),
    )
    op.create_index('idx_notif_user_id', 'notification_logs', ['user_id'])
    op.create_index('idx_notif_status', 'notification_logs', ['status'])
    op.create_index('idx_notif_created_at', 'notification_logs', ['created_at'])
    op.create_index('idx_notif_retry', 'notification_logs', ['status', 'next_retry_at'])


def downgrade() -> None:
    op.drop_index('idx_notif_retry', 'notification_logs')
    op.drop_index('idx_notif_created_at', 'notification_logs')
    op.drop_index('idx_notif_status', 'notification_logs')
    op.drop_index('idx_notif_user_id', 'notification_logs')
    op.drop_table('notification_logs')

    op.drop_index('idx_fcm_token', 'fcm_tokens')
    op.drop_index('idx_fcm_user', 'fcm_tokens')
    op.drop_table('fcm_tokens')

    op.drop_index('idx_user_preferences', 'user_notification_preferences')
    op.drop_table('user_notification_preferences')

    op.drop_index('idx_template_type_channel', 'notification_templates')
    op.drop_index('idx_template_key', 'notification_templates')
    op.drop_table('notification_templates')

    # Drop enums
    sa.Enum(name='notificationtype').drop(op.get_bind(), checkfirst=True)
    sa.Enum(name='notificationpriority').drop(op.get_bind(), checkfirst=True)
    sa.Enum(name='notificationstatus').drop(op.get_bind(), checkfirst=True)
    sa.Enum(name='notificationchannel').drop(op.get_bind(), checkfirst=True)
