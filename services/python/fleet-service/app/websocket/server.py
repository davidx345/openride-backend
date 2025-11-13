"""Socket.IO server with Redis adapter for horizontal scaling"""
import asyncio
from datetime import datetime
from typing import Any, Dict, Optional
from uuid import UUID

import socketio
from socketio import ASGIApp

from app.core.config import settings
from app.core.logging import get_logger
from app.services.auth import verify_jwt_token
from app.services.connection import ConnectionManager
from app.services.location import LocationService
from app.services.trip import TripService

logger = get_logger(__name__)

# Create Socket.IO server with Redis adapter
sio = socketio.AsyncServer(
    async_mode='asgi',
    cors_allowed_origins=settings.cors_origins,
    logger=settings.LOG_LEVEL == 'DEBUG',
    engineio_logger=settings.LOG_LEVEL == 'DEBUG',
    ping_timeout=60,
    ping_interval=25,
    max_http_buffer_size=1024 * 1024,  # 1MB
)

# Socket.IO app wrapper
socket_app = ASGIApp(
    sio,
    socketio_path=settings.SOCKETIO_PATH,
)

# Service instances (initialized in startup)
connection_manager: Optional[ConnectionManager] = None
location_service: Optional[LocationService] = None
trip_service: Optional[TripService] = None


async def initialize_services(db_session, redis_client):
    """Initialize WebSocket services"""
    global connection_manager, location_service, trip_service
    
    connection_manager = ConnectionManager(db_session, redis_client)
    location_service = LocationService(db_session, redis_client, sio)
    trip_service = TripService(db_session, redis_client, sio)
    
    # Set up Redis adapter for horizontal scaling
    mgr = socketio.AsyncRedisManager(settings.redis_url_with_password)
    sio.manager = mgr
    
    logger.info("WebSocket services initialized")


@sio.event
async def connect(sid: str, environ: Dict[str, Any], auth: Optional[Dict[str, Any]] = None):
    """Handle client connection with JWT authentication"""
    try:
        # Extract JWT token from auth or query params
        token = None
        if auth and 'token' in auth:
            token = auth['token']
        elif 'HTTP_AUTHORIZATION' in environ:
            auth_header = environ['HTTP_AUTHORIZATION']
            if auth_header.startswith('Bearer '):
                token = auth_header[7:]
        
        if not token:
            logger.warning(f"Connection rejected: No token provided (sid={sid})")
            return False
        
        # Verify JWT token
        payload = verify_jwt_token(token)
        user_id = UUID(payload.get('sub'))
        user_role = payload.get('role', 'RIDER')
        
        # Get client info
        ip_address = environ.get('REMOTE_ADDR')
        user_agent = environ.get('HTTP_USER_AGENT')
        
        # Check connection limits for drivers
        if user_role == 'DRIVER':
            active_connections = await connection_manager.count_active_connections(user_id)
            if active_connections >= settings.MAX_CONNECTIONS_PER_DRIVER:
                logger.warning(
                    f"Connection rejected: Max connections exceeded "
                    f"(user_id={user_id}, active={active_connections})"
                )
                await sio.emit('error', {
                    'error_code': 'MAX_CONNECTIONS_EXCEEDED',
                    'message': f'Maximum {settings.MAX_CONNECTIONS_PER_DRIVER} connections allowed',
                }, room=sid)
                return False
        
        # Create connection session
        await connection_manager.create_session(
            session_id=sid,
            user_id=user_id,
            user_role=user_role,
            connection_type='WEBSOCKET',
            ip_address=ip_address,
            user_agent=user_agent,
        )
        
        # Join user-specific room
        await sio.enter_room(sid, f"user:{user_id}")
        
        # Join role-specific room
        if user_role == 'DRIVER':
            await sio.enter_room(sid, "drivers")
        elif user_role == 'RIDER':
            await sio.enter_room(sid, "riders")
        
        logger.info(f"Client connected: user_id={user_id}, role={user_role}, sid={sid}")
        
        # Send connection confirmation
        await sio.emit('connected', {
            'user_id': str(user_id),
            'role': user_role,
            'timestamp': datetime.utcnow().isoformat(),
        }, room=sid)
        
        return True
        
    except Exception as e:
        logger.error(f"Connection error: {e}", exc_info=True)
        await sio.emit('error', {
            'error_code': 'AUTHENTICATION_FAILED',
            'message': 'Authentication failed',
        }, room=sid)
        return False


@sio.event
async def disconnect(sid: str):
    """Handle client disconnection"""
    try:
        # Mark session as disconnected
        session = await connection_manager.get_session(sid)
        if session:
            await connection_manager.disconnect_session(sid)
            
            # If driver, update status to OFFLINE
            if session.user_role == 'DRIVER':
                await location_service.update_driver_status(
                    driver_id=session.user_id,
                    status='OFFLINE',
                )
            
            logger.info(f"Client disconnected: user_id={session.user_id}, sid={sid}")
        
    except Exception as e:
        logger.error(f"Disconnect error: {e}", exc_info=True)


@sio.event
async def driver_location(sid: str, data: Dict[str, Any]):
    """Handle driver location update"""
    try:
        session = await connection_manager.get_session(sid)
        if not session or session.user_role != 'DRIVER':
            await sio.emit('error', {
                'error_code': 'UNAUTHORIZED',
                'message': 'Driver role required',
            }, room=sid)
            return
        
        # Update location
        location = await location_service.update_driver_location(
            driver_id=session.user_id,
            latitude=data['latitude'],
            longitude=data['longitude'],
            bearing=data.get('bearing'),
            speed=data.get('speed'),
            accuracy=data.get('accuracy'),
            altitude=data.get('altitude'),
        )
        
        # Broadcast to subscribers
        await location_service.broadcast_location_update(
            driver_id=session.user_id,
            location=location,
        )
        
    except Exception as e:
        logger.error(f"Location update error: {e}", exc_info=True)
        await sio.emit('error', {
            'error_code': 'LOCATION_UPDATE_FAILED',
            'message': str(e),
        }, room=sid)


@sio.event
async def driver_online(sid: str, data: Dict[str, Any]):
    """Handle driver going online"""
    try:
        session = await connection_manager.get_session(sid)
        if not session or session.user_role != 'DRIVER':
            await sio.emit('error', {
                'error_code': 'UNAUTHORIZED',
                'message': 'Driver role required',
            }, room=sid)
            return
        
        # Update status to ONLINE
        await location_service.update_driver_status(
            driver_id=session.user_id,
            status='ONLINE',
            location_data=data.get('location'),
        )
        
        logger.info(f"Driver online: driver_id={session.user_id}")
        
        await sio.emit('status_updated', {
            'status': 'ONLINE',
            'timestamp': datetime.utcnow().isoformat(),
        }, room=sid)
        
    except Exception as e:
        logger.error(f"Driver online error: {e}", exc_info=True)
        await sio.emit('error', {
            'error_code': 'STATUS_UPDATE_FAILED',
            'message': str(e),
        }, room=sid)


@sio.event
async def driver_offline(sid: str, data: Dict[str, Any]):
    """Handle driver going offline"""
    try:
        session = await connection_manager.get_session(sid)
        if not session or session.user_role != 'DRIVER':
            return
        
        # Update status to OFFLINE
        await location_service.update_driver_status(
            driver_id=session.user_id,
            status='OFFLINE',
        )
        
        logger.info(f"Driver offline: driver_id={session.user_id}")
        
        await sio.emit('status_updated', {
            'status': 'OFFLINE',
            'timestamp': datetime.utcnow().isoformat(),
        }, room=sid)
        
    except Exception as e:
        logger.error(f"Driver offline error: {e}", exc_info=True)


@sio.event
async def rider_subscribe(sid: str, data: Dict[str, Any]):
    """Handle rider subscribing to trip updates"""
    try:
        session = await connection_manager.get_session(sid)
        if not session or session.user_role != 'RIDER':
            await sio.emit('error', {
                'error_code': 'UNAUTHORIZED',
                'message': 'Rider role required',
            }, room=sid)
            return
        
        trip_id = UUID(data['trip_id'])
        
        # Verify rider owns this trip
        trip = await trip_service.get_trip(trip_id)
        if not trip or trip.rider_id != session.user_id:
            await sio.emit('error', {
                'error_code': 'TRIP_NOT_FOUND',
                'message': 'Trip not found or access denied',
            }, room=sid)
            return
        
        # Join trip-specific room
        await sio.enter_room(sid, f"trip:{trip_id}")
        
        logger.info(f"Rider subscribed to trip: rider_id={session.user_id}, trip_id={trip_id}")
        
        # Send current trip status
        await sio.emit('trip_status', {
            'trip_id': str(trip_id),
            'status': trip.status,
            'driver_id': str(trip.driver_id),
            'estimated_arrival': trip.estimated_arrival.isoformat() if trip.estimated_arrival else None,
        }, room=sid)
        
    except Exception as e:
        logger.error(f"Rider subscribe error: {e}", exc_info=True)
        await sio.emit('error', {
            'error_code': 'SUBSCRIBE_FAILED',
            'message': str(e),
        }, room=sid)


@sio.event
async def rider_unsubscribe(sid: str, data: Dict[str, Any]):
    """Handle rider unsubscribing from trip updates"""
    try:
        trip_id = UUID(data['trip_id'])
        
        # Leave trip-specific room
        await sio.leave_room(sid, f"trip:{trip_id}")
        
        logger.info(f"Rider unsubscribed from trip: trip_id={trip_id}, sid={sid}")
        
    except Exception as e:
        logger.error(f"Rider unsubscribe error: {e}", exc_info=True)


async def broadcast_trip_update(trip_id: UUID, data: Dict[str, Any]):
    """Broadcast trip update to all subscribers"""
    await sio.emit('trip:update', data, room=f"trip:{trip_id}")


async def broadcast_driver_location(driver_id: UUID, data: Dict[str, Any]):
    """Broadcast driver location to subscribers"""
    await sio.emit('driver:location', data, room=f"user:{driver_id}")
