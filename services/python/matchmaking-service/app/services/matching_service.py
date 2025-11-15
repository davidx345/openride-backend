"""Main matching service - orchestrates route matching logic."""

import logging
import time
from datetime import time as time_type
from decimal import Decimal
from typing import Optional, Sequence
from uuid import UUID

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.exceptions import MatchingError
from app.core.redis import redis_client
from app.models.route import Route
from app.repositories.hub_repository import HubRepository
from app.repositories.route_repository import RouteRepository
from app.schemas.matching import (
    MatchRequest,
    MatchResponse,
    MatchResult,
    ScoreBreakdown,
)
from app.services.feature_extraction_service import FeatureExtractionService
from app.services.hub_compatibility_service import HubCompatibilityService
from app.services.route_cache_service import RouteCacheService
from app.services.scoring_service import RouteScorer
from app.services.stop_sequence_validator import StopSequenceValidator
from app.services.user_service import UserService

settings = get_settings()
logger = logging.getLogger(__name__)


class MatchingService:
    """Orchestrates intelligent route matching and ranking."""

    def __init__(self, db: AsyncSession):
        """
        Initialize matching service.

        Args:
            db: Database session
        """
        self.db = db
        self.route_repo = RouteRepository(db)
        self.hub_repo = HubRepository(db)
        self.scorer = RouteScorer()
        self.user_service = UserService()
        self.route_cache = RouteCacheService(redis_client)
        self.hub_compatibility = HubCompatibilityService(db)
        self.stop_validator = StopSequenceValidator(db)
        self.feature_extractor = FeatureExtractionService()

    async def match_routes(
        self,
        request: MatchRequest,
        scoring_mode: str = "rule-based",
    ) -> MatchResponse:
        """
        Find and rank matching routes for rider request.

        Args:
            request: Match request with rider preferences
            scoring_mode: Scoring mode - "rule-based", "ml-based", or "hybrid" (default: "rule-based")

        Returns:
            MatchResponse: Ranked list of matching routes

        Raises:
            MatchingError: If matching fails
        """
        start_time = time.time()
        cache_hit = False

        try:
            # Step 0: Try to find nearest hubs for caching
            origin_hub_id: Optional[UUID] = None
            dest_hub_id: Optional[UUID] = None

            try:
                origin_hub = await self.hub_repo.find_nearest_hub(
                    lat=request.origin_lat,
                    lon=request.origin_lon,
                    max_distance_km=2.0,  # Within 2km
                )
                if origin_hub:
                    origin_hub_id = origin_hub.id

                if request.dest_lat and request.dest_lon:
                    dest_hub = await self.hub_repo.find_nearest_hub(
                        lat=request.dest_lat,
                        lon=request.dest_lon,
                        max_distance_km=2.0,
                    )
                    if dest_hub:
                        dest_hub_id = dest_hub.id
            except Exception as e:
                logger.debug(f"Hub lookup failed: {e}")

            # Step 1: Check cache for hub-based routes
            candidate_routes: Sequence[Route] = []
            if origin_hub_id and dest_hub_id:
                cached_routes = await self.route_cache.get_cached_routes(
                    origin_hub_id=origin_hub_id,
                    destination_hub_id=dest_hub_id,
                    departure_time=request.desired_time,
                    active_only=True,
                )

                if cached_routes:
                    # Convert cached dicts back to Route objects
                    candidate_routes = await self._routes_from_cache(cached_routes)
                    cache_hit = True
                    logger.info(f"Cache HIT: {len(candidate_routes)} routes from cache")

            # Step 2: On cache miss, query database
            if not cache_hit:
                radius_meters = request.radius_km * 1000
                candidate_routes = await self.route_repo.find_nearby_routes(
                    origin_lat=request.origin_lat,
                    origin_lon=request.origin_lon,
                    dest_lat=request.dest_lat,
                    dest_lon=request.dest_lon,
                    radius_meters=radius_meters,
                    max_results=settings.max_candidate_routes,
                )

                # Cache for future requests
                if origin_hub_id and dest_hub_id and candidate_routes:
                    await self.route_cache.cache_routes(
                        routes=list(candidate_routes),
                        origin_hub_id=origin_hub_id,
                        destination_hub_id=dest_hub_id,
                        departure_time=request.desired_time,
                        active_only=True,
                    )
                    logger.info(f"Cached {len(candidate_routes)} routes for hub pair")

            total_candidates = len(candidate_routes)
            logger.info(f"Found {total_candidates} candidate routes (cache_hit={cache_hit})")

            # Step 3: Phase 3 - Hub compatibility filtering
            if origin_hub_id and dest_hub_id:
                hub_filtered_routes = await self.hub_compatibility.filter_compatible_routes(
                    routes=list(candidate_routes),
                    origin_hub_id=origin_hub_id,
                    destination_hub_id=dest_hub_id,
                )
                logger.info(
                    f"After hub compatibility: {len(hub_filtered_routes)}/{total_candidates} routes"
                )
                candidate_routes = hub_filtered_routes

            # Step 4: Phase 3 - Stop sequence validation
            sequence_validated_routes = await self.stop_validator.validate_routes(
                routes=list(candidate_routes),
                origin_lat=request.origin_lat,
                origin_lon=request.origin_lon,
                dest_lat=request.dest_lat,
                dest_lon=request.dest_lon,
            )
            logger.info(
                f"After stop sequence validation: {len(sequence_validated_routes)}/{len(candidate_routes)} routes"
            )

            # Step 5: Filter by time window
            time_filtered_routes = await self.route_repo.filter_by_time_window(
                routes=sequence_validated_routes,
                desired_time=request.desired_time,
                window_minutes=settings.time_window_minutes,
            )
            logger.info(
                f"After time filtering: {len(time_filtered_routes)}/{len(sequence_validated_routes)} routes"
            )

            # Step 6: Filter by minimum seats
            seat_filtered_routes = [
                r for r in time_filtered_routes if r.seats_available >= request.min_seats
            ]
            logger.info(
                f"After seat filtering: {len(seat_filtered_routes)}/{len(time_filtered_routes)} routes"
            )

            # Step 7: Filter by max price if specified
            price_filtered_routes = seat_filtered_routes
            if request.max_price is not None:
                price_filtered_routes = [
                    r for r in seat_filtered_routes if r.base_price <= request.max_price
                ]
                logger.info(
                    f"After price filtering: {len(price_filtered_routes)}/{len(seat_filtered_routes)} routes"
                )

            matched_candidates = len(price_filtered_routes)

            if matched_candidates == 0:
                execution_time = int((time.time() - start_time) * 1000)
                return MatchResponse(
                    matches=[],
                    total_candidates=total_candidates,
                    matched_candidates=0,
                    execution_time_ms=execution_time,
                )

            # Step 8: Score and rank routes
            match_results = await self._score_and_rank_routes(
                routes=price_filtered_routes,
                request=request,
                scoring_mode=scoring_mode,
            )

            # Step 9: Fetch driver ratings (parallel for top 20)
            top_matches = match_results[:20]
            await self._enrich_with_driver_data(top_matches)

            # Check performance
            execution_time = int((time.time() - start_time) * 1000)
            logger.info(
                f"Matching completed in {execution_time}ms (cache_hit={cache_hit})"
            )

            if execution_time > settings.performance_target_ms:
                logger.warning(
                    f"Performance target exceeded: {execution_time}ms > {settings.performance_target_ms}ms"
                )

            return MatchResponse(
                matches=top_matches,
                total_candidates=total_candidates,
                matched_candidates=matched_candidates,
                execution_time_ms=execution_time,
            )

        except Exception as e:
            logger.error(f"Matching error: {e}", exc_info=True)
            raise MatchingError(f"Failed to match routes: {e}")

    async def _score_and_rank_routes(
        self,
        routes: Sequence[Route],
        request: MatchRequest,
        scoring_mode: str = "rule-based",
    ) -> list[MatchResult]:
        """
        Score and rank routes using rule-based or ML-based scoring.

        Args:
            routes: Candidate routes
            request: Match request
            scoring_mode: \"rule-based\", \"ml-based\", or \"hybrid\"

        Returns:
            list[MatchResult]: Sorted list of match results
        """
        all_prices = [r.base_price for r in routes]
        results: list[MatchResult] = []

        # Phase 4: Extract ML features if needed
        ml_features = None
        driver_stats_map = {}
        match_types = {}
        distances = {}

        if scoring_mode in ["ml-based", "hybrid"]:
            # Get driver stats from database (materialized view)
            driver_ids = [str(r.driver_id) for r in routes]
            driver_stats = await self.route_repo.get_driver_stats_batch(driver_ids)
            driver_stats_map = {str(d["driver_id"]): d for d in driver_stats}

            # Calculate match types and distances for each route
            for route in routes:
                # Determine match type (EXACT vs PARTIAL)
                if route.origin_hub_id and route.destination_hub_id:
                    match_types[str(route.id)] = "EXACT"
                else:
                    match_types[str(route.id)] = "PARTIAL"

                # Calculate distances
                from app.services.geospatial_utils import calculate_distance_haversine

                origin_dist = 0.0
                dest_dist = 0.0

                if route.route_stops:
                    # Find min distance to origin
                    origin_dists = [
                        calculate_distance_haversine(
                            stop.stop.lat,
                            stop.stop.lon,
                            request.origin_lat,
                            request.origin_lon,
                        )
                        for stop in route.route_stops
                    ]
                    origin_dist = min(origin_dists) if origin_dists else 0.0

                    # Find min distance to destination
                    if request.dest_lat and request.dest_lon:
                        dest_dists = [
                            calculate_distance_haversine(
                                stop.stop.lat,
                                stop.stop.lon,
                                request.dest_lat,
                                request.dest_lon,
                            )
                            for stop in route.route_stops
                        ]
                        dest_dist = min(dest_dists) if dest_dists else 0.0

                distances[route.id] = {
                    "origin_km": origin_dist / 1000.0,
                    "dest_km": dest_dist / 1000.0,
                }

            # Extract features for all routes
            ml_features = self.feature_extractor.extract_batch_features(
                routes=routes,
                request=request,
                driver_stats_map=driver_stats_map,
                match_types=match_types,
                distances=distances,
            )

        # Score each route
        for idx, route in enumerate(routes):
            # Calculate rule-based component scores
            route_match_score, has_origin, has_dest, correct_dir = (
                self.scorer.calculate_route_match_score(
                    route=route,
                    origin_lat=request.origin_lat,
                    origin_lon=request.origin_lon,
                    dest_lat=request.dest_lat,
                    dest_lon=request.dest_lon,
                )
            )

            time_match_score, time_diff = self.scorer.calculate_time_match_score(
                route_time=route.departure_time,
                desired_time=request.desired_time,
                max_window_minutes=settings.time_window_minutes,
            )

            # Rating score (will be updated after fetching driver data)
            rating_score = 0.5  # Neutral default

            price_score = self.scorer.calculate_price_score(
                route_price=route.base_price,
                all_prices=all_prices,
            )

            # Calculate rule-based composite score
            rule_based_score = self.scorer.calculate_composite_score(
                route_match_score=route_match_score,
                time_match_score=time_match_score,
                rating_score=rating_score,
                price_score=price_score,
            )

            # Phase 4: Calculate final score based on mode
            final_score = rule_based_score  # Default

            if scoring_mode == "ml-based" and ml_features is not None:
                # Pure ML scoring
                feature_weights = self.feature_extractor.get_feature_importance_weights()
                final_score = self.scorer.calculate_ml_score(
                    features=ml_features[idx],
                    feature_weights=feature_weights,
                )
            elif scoring_mode == "hybrid" and ml_features is not None:
                # Hybrid scoring (60% rule-based, 40% ML)
                feature_weights = self.feature_extractor.get_feature_importance_weights()
                ml_score = self.scorer.calculate_ml_score(
                    features=ml_features[idx],
                    feature_weights=feature_weights,
                )
                final_score = self.scorer.calculate_hybrid_score(
                    rule_based_score=rule_based_score,
                    ml_score=ml_score,
                    alpha=0.6,  # 60% rule-based, 40% ML
                )

            # Generate explanation
            explanation = self.scorer.generate_explanation(
                route_match_score=route_match_score,
                has_origin=has_origin,
                has_destination=has_dest,
                correct_direction=correct_dir,
                time_match_score=time_match_score,
                time_diff_minutes=time_diff,
                driver_rating=None,
                price_score=price_score,
                route_price=route.base_price,
            )

            # Create match result
            match_result = MatchResult(
                route_id=route.id,
                driver_id=route.driver_id,
                final_score=final_score,
                scores=ScoreBreakdown(
                    route_match=route_match_score,
                    time_match=time_match_score,
                    rating=rating_score,
                    price=price_score,
                ),
                explanation=explanation,
                recommended=final_score >= 0.7,
                route_name=route.name,
                departure_time=route.departure_time,
                seats_available=route.seats_available,
                base_price=route.base_price,
            )

            results.append(match_result)

        # Sort by final score (descending)
        results.sort(key=lambda x: x.final_score, reverse=True)

        return results

    async def _enrich_with_driver_data(self, match_results: list[MatchResult]) -> None:
        """
        Enrich match results with driver ratings.

        Fetches driver data from User Service and updates scores.

        Args:
            match_results: List of match results to enrich
        """
        try:
            driver_ids = [str(m.driver_id) for m in match_results]
            driver_data = await self.user_service.get_drivers_batch(driver_ids)

            # Update match results with driver ratings
            for match in match_results:
                driver_info = driver_data.get(str(match.driver_id))
                if driver_info:
                    rating = driver_info.get("rating")
                    match.driver_rating = rating

                    # Recalculate score with actual rating
                    if rating is not None:
                        new_rating_score = self.scorer.calculate_rating_score(rating)

                        # Recalculate composite score
                        match.final_score = self.scorer.calculate_composite_score(
                            route_match_score=match.scores.route_match,
                            time_match_score=match.scores.time_match,
                            rating_score=new_rating_score,
                            price_score=match.scores.price,
                        )
                        match.scores.rating = new_rating_score
                        match.recommended = match.final_score >= 0.7

                        # Regenerate explanation with rating
                        match.explanation = self.scorer.generate_explanation(
                            route_match_score=match.scores.route_match,
                            has_origin=True,  # Already matched
                            has_destination=True,
                            correct_direction=True,
                            time_match_score=match.scores.time_match,
                            time_diff_minutes=0,  # Not stored
                            driver_rating=rating,
                            price_score=match.scores.price,
                            route_price=match.base_price or Decimal(0),
                        )

            # Re-sort by updated scores
            match_results.sort(key=lambda x: x.final_score, reverse=True)

        except Exception as e:
            logger.warning(f"Failed to enrich with driver data: {e}")
            # Continue without driver data

    async def _routes_from_cache(self, cached_dicts: list[dict]) -> list[Route]:
        """
        Convert cached route dictionaries to Route objects.

        Args:
            cached_dicts: List of route dictionaries from cache

        Returns:
            list[Route]: List of Route objects
        """
        route_ids = [UUID(d["id"]) for d in cached_dicts]

        # Fetch full Route objects from database
        routes = await self.route_repo.get_routes_by_ids(route_ids)

        return routes
