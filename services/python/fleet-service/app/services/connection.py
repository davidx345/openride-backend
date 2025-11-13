"""Connection session management"""
from datetime import datetime
from typing import Optional
from uuid import UUID

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.logging import get_logger
from app.db.models import ConnectionSession

logger = get_logger(__name__)


class ConnectionManager:
    """Manages WebSocket connection sessions"""
    
    def __init__(self, db: AsyncSession, redis):
        self.db = db
        self.redis = redis
    
    async def create_session(
        self,
        session_id: str,
        user_id: UUID,
        user_role: str,
        connection_type: str = 'WEBSOCKET',
        ip_address: Optional[str] = None,
        user_agent: Optional[str] = None,
    ) -> ConnectionSession:
        """Create a new connection session"""
        session = ConnectionSession(
            session_id=session_id,
            user_id=user_id,
            user_role=user_role,
            connection_type=connection_type,
            ip_address=ip_address,
            user_agent=user_agent,
            connected_at=datetime.utcnow(),
            last_activity=datetime.utcnow(),
            is_active=True,
        )
        
        self.db.add(session)
        await self.db.commit()
        await self.db.refresh(session)
        
        logger.info(f"Session created: {session_id} for user {user_id}")
        
        return session
    
    async def get_session(self, session_id: str) -> Optional[ConnectionSession]:
        """Get connection session by session ID"""
        result = await self.db.execute(
            select(ConnectionSession).where(
                ConnectionSession.session_id == session_id,
                ConnectionSession.is_active == True,
            )
        )
        return result.scalar_one_or_none()
    
    async def disconnect_session(self, session_id: str) -> None:
        """Mark session as disconnected"""
        await self.db.execute(
            update(ConnectionSession)
            .where(ConnectionSession.session_id == session_id)
            .values(
                is_active=False,
                disconnected_at=datetime.utcnow(),
            )
        )
        await self.db.commit()
        
        logger.info(f"Session disconnected: {session_id}")
    
    async def update_activity(self, session_id: str) -> None:
        """Update last activity timestamp"""
        await self.db.execute(
            update(ConnectionSession)
            .where(ConnectionSession.session_id == session_id)
            .values(last_activity=datetime.utcnow())
        )
        await self.db.commit()
    
    async def count_active_connections(self, user_id: UUID) -> int:
        """Count active connections for a user"""
        result = await self.db.execute(
            select(ConnectionSession)
            .where(
                ConnectionSession.user_id == user_id,
                ConnectionSession.is_active == True,
            )
        )
        return len(result.scalars().all())
    
    async def get_user_sessions(self, user_id: UUID) -> list[ConnectionSession]:
        """Get all active sessions for a user"""
        result = await self.db.execute(
            select(ConnectionSession)
            .where(
                ConnectionSession.user_id == user_id,
                ConnectionSession.is_active == True,
            )
        )
        return result.scalars().all()
