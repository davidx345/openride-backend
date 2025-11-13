"""Tests for vehicle API endpoints."""
import pytest
from uuid import uuid4
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_create_vehicle(client: AsyncClient, mock_jwt_token: str):
    """Test creating a vehicle."""
    vehicle_data = {
        "plate_number": "ABC-123DE",
        "make": "Toyota",
        "model": "Camry",
        "year": 2020,
        "color": "Silver",
        "seats_total": 4
    }
    
    response = await client.post(
        "/v1/drivers/vehicles",
        json=vehicle_data,
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    
    assert response.status_code == 201
    data = response.json()
    assert data["plate_number"] == "ABC-123DE"
    assert data["make"] == "Toyota"
    assert data["is_active"] is True


@pytest.mark.asyncio
async def test_get_vehicles(client: AsyncClient, mock_jwt_token: str):
    """Test getting driver's vehicles."""
    # Create a vehicle first
    vehicle_data = {
        "plate_number": "XYZ-789FG",
        "make": "Honda",
        "model": "Accord",
        "year": 2021,
        "color": "Blue",
        "seats_total": 4
    }
    
    await client.post(
        "/v1/drivers/vehicles",
        json=vehicle_data,
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    
    # Get vehicles
    response = await client.get(
        "/v1/drivers/vehicles",
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    
    assert response.status_code == 200
    data = response.json()
    assert len(data) >= 1
    assert any(v["plate_number"] == "XYZ-789FG" for v in data)


@pytest.mark.asyncio
async def test_create_vehicle_duplicate_plate(client: AsyncClient, mock_jwt_token: str):
    """Test creating a vehicle with duplicate plate number."""
    vehicle_data = {
        "plate_number": "DUP-123XX",
        "make": "Toyota",
        "model": "Camry",
        "year": 2020,
        "color": "Silver",
        "seats_total": 4
    }
    
    # Create first vehicle
    response1 = await client.post(
        "/v1/drivers/vehicles",
        json=vehicle_data,
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    assert response1.status_code == 201
    
    # Try to create duplicate
    response2 = await client.post(
        "/v1/drivers/vehicles",
        json=vehicle_data,
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    assert response2.status_code == 400


@pytest.mark.asyncio
async def test_update_vehicle(client: AsyncClient, mock_jwt_token: str):
    """Test updating a vehicle."""
    # Create vehicle
    vehicle_data = {
        "plate_number": "UPD-456YY",
        "make": "Toyota",
        "model": "Camry",
        "year": 2020,
        "color": "Silver",
        "seats_total": 4
    }
    
    create_response = await client.post(
        "/v1/drivers/vehicles",
        json=vehicle_data,
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    vehicle_id = create_response.json()["id"]
    
    # Update vehicle
    update_data = {"color": "Red"}
    response = await client.put(
        f"/v1/drivers/vehicles/{vehicle_id}",
        json=update_data,
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    
    assert response.status_code == 200
    data = response.json()
    assert data["color"] == "Red"


@pytest.mark.asyncio
async def test_delete_vehicle(client: AsyncClient, mock_jwt_token: str):
    """Test deleting a vehicle."""
    # Create vehicle
    vehicle_data = {
        "plate_number": "DEL-789ZZ",
        "make": "Toyota",
        "model": "Camry",
        "year": 2020,
        "color": "Silver",
        "seats_total": 4
    }
    
    create_response = await client.post(
        "/v1/drivers/vehicles",
        json=vehicle_data,
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    vehicle_id = create_response.json()["id"]
    
    # Delete vehicle
    response = await client.delete(
        f"/v1/drivers/vehicles/{vehicle_id}",
        headers={"Authorization": f"Bearer {mock_jwt_token}"}
    )
    
    assert response.status_code == 204
