"""Tests for route API endpoints."""
import pytest
from decimal import Decimal
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_create_route(client: AsyncClient, mock_jwt_token: str):
    """Test creating a route."""
    # First create a vehicle
    vehicle_data = {
        "plate_number": "RT-123XX",
        "make": "Toyota",
        "model": "Camry",
        "year": 2020,
        "color": "Silver",
        "seats_total": 4
    }
    
    vehicle_response = await client.post(
        "/v1/drivers/vehicles",
        json=vehicle_data,
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    vehicle_id = vehicle_response.json()["id"]
    
    # Create route
    route_data = {
        "vehicle_id": vehicle_id,
        "name": "Lekki to VI",
        "departure_time": "07:00:00",
        "active_days": [0, 1, 2, 3, 4],  # Mon-Fri
        "seats_total": 3,
        "base_price": "1500.00",
        "stops": [
            {
                "name": "Lekki Phase 1",
                "lat": "6.4302",
                "lon": "3.5066",
                "planned_arrival_offset_minutes": 0,
                "price_from_origin": "0.00"
            },
            {
                "name": "Victoria Island",
                "lat": "6.4281",
                "lon": "3.4219",
                "planned_arrival_offset_minutes": 30,
                "price_from_origin": "1500.00"
            }
        ]
    }
    
    response = await client.post(
        "/v1/drivers/routes",
        json=route_data,
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    
    assert response.status_code == 201
    data = response.json()
    assert data["name"] == "Lekki to VI"
    assert data["seats_total"] == 3
    assert data["seats_available"] == 3
    assert data["status"] == "ACTIVE"


@pytest.mark.asyncio
async def test_get_driver_routes(client: AsyncClient, mock_jwt_token: str):
    """Test getting driver's routes."""
    # Create vehicle and route (same as above)
    vehicle_data = {
        "plate_number": "GR-456YY",
        "make": "Honda",
        "model": "Accord",
        "year": 2021,
        "color": "Blue",
        "seats_total": 4
    }
    
    vehicle_response = await client.post(
        "/v1/drivers/vehicles",
        json=vehicle_data,
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    vehicle_id = vehicle_response.json()["id"]
    
    route_data = {
        "vehicle_id": vehicle_id,
        "name": "Test Route",
        "departure_time": "08:00:00",
        "active_days": [0, 1],
        "seats_total": 2,
        "base_price": "1000.00",
        "stops": [
            {
                "name": "Start",
                "lat": "6.5000",
                "lon": "3.5000",
                "planned_arrival_offset_minutes": 0,
                "price_from_origin": "0.00"
            },
            {
                "name": "End",
                "lat": "6.6000",
                "lon": "3.6000",
                "planned_arrival_offset_minutes": 20,
                "price_from_origin": "1000.00"
            }
        ]
    }
    
    await client.post(
        "/v1/drivers/routes",
        json=route_data,
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    
    # Get routes
    response = await client.get(
        "/v1/drivers/routes",
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    
    assert response.status_code == 200
    data = response.json()
    assert len(data) >= 1


@pytest.mark.asyncio
async def test_update_route_status(client: AsyncClient, mock_jwt_token: str):
    """Test updating route status."""
    # Create vehicle and route
    vehicle_data = {
        "plate_number": "US-789ZZ",
        "make": "Toyota",
        "model": "Camry",
        "year": 2020,
        "color": "Silver",
        "seats_total": 4
    }
    
    vehicle_response = await client.post(
        "/v1/drivers/vehicles",
        json=vehicle_data,
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    vehicle_id = vehicle_response.json()["id"]
    
    route_data = {
        "vehicle_id": vehicle_id,
        "name": "Status Test Route",
        "departure_time": "09:00:00",
        "active_days": [0],
        "seats_total": 3,
        "base_price": "1200.00",
        "stops": [
            {
                "name": "A",
                "lat": "6.4000",
                "lon": "3.4000",
                "planned_arrival_offset_minutes": 0,
                "price_from_origin": "0.00"
            },
            {
                "name": "B",
                "lat": "6.5000",
                "lon": "3.5000",
                "planned_arrival_offset_minutes": 15,
                "price_from_origin": "1200.00"
            }
        ]
    }
    
    create_response = await client.post(
        "/v1/drivers/routes",
        json=route_data,
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    route_id = create_response.json()["id"]
    
    # Update status to PAUSED
    response = await client.patch(
        f"/v1/drivers/routes/{route_id}/status?status=PAUSED",
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "PAUSED"
