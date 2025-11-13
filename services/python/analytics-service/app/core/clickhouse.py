"""ClickHouse client manager."""

from typing import Any, Dict, List, Optional

import clickhouse_connect

from app.core.config import settings
from app.core.logging import get_logger

logger = get_logger(__name__)


class ClickHouseManager:
    """ClickHouse connection manager."""

    def __init__(self) -> None:
        """Initialize ClickHouse manager."""
        self._client: Optional[clickhouse_connect.driver.Client] = None

    def connect(self) -> None:
        """Initialize ClickHouse connection."""
        try:
            self._client = clickhouse_connect.get_client(
                host=settings.CLICKHOUSE_HOST,
                port=settings.CLICKHOUSE_PORT,
                username=settings.CLICKHOUSE_USER,
                password=settings.CLICKHOUSE_PASSWORD,
                database=settings.CLICKHOUSE_DATABASE,
                secure=settings.CLICKHOUSE_SECURE,
            )
            # Test connection
            self._client.ping()
            logger.info("clickhouse_connected", host=settings.CLICKHOUSE_HOST, database=settings.CLICKHOUSE_DATABASE)
        except Exception as e:
            logger.error("clickhouse_connection_failed", error=str(e))
            raise

    def disconnect(self) -> None:
        """Close ClickHouse connection."""
        if self._client:
            self._client.close()
            logger.info("clickhouse_disconnected")

    @property
    def client(self) -> clickhouse_connect.driver.Client:
        """Get ClickHouse client instance.
        
        Returns:
            ClickHouse client
            
        Raises:
            RuntimeError: If ClickHouse not connected
        """
        if not self._client:
            raise RuntimeError("ClickHouse not connected. Call connect() first.")
        return self._client

    def query(self, query: str, parameters: Optional[Dict[str, Any]] = None) -> clickhouse_connect.driver.query.QueryResult:
        """Execute a query.
        
        Args:
            query: SQL query string
            parameters: Query parameters
            
        Returns:
            Query result
        """
        return self.client.query(query, parameters=parameters)

    def insert(self, table: str, data: List[List[Any]], column_names: Optional[List[str]] = None) -> None:
        """Insert data into table.
        
        Args:
            table: Table name
            data: Data rows
            column_names: Column names (optional)
        """
        self.client.insert(table, data, column_names=column_names)

    def command(self, cmd: str, parameters: Optional[Dict[str, Any]] = None) -> Any:
        """Execute a command (DDL, etc).
        
        Args:
            cmd: SQL command
            parameters: Command parameters
            
        Returns:
            Command result
        """
        return self.client.command(cmd, parameters=parameters)


# Global ClickHouse manager instance
clickhouse_manager = ClickHouseManager()


def get_clickhouse() -> clickhouse_connect.driver.Client:
    """Dependency to get ClickHouse client.
    
    Returns:
        ClickHouse client
        
    Usage:
        @app.get("/metrics")
        def get_metrics(ch: Client = Depends(get_clickhouse)):
            ...
    """
    return clickhouse_manager.client
