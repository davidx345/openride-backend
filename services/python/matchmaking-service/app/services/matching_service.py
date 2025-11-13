"""Main matching service - orchestrates route matching logic."""

import logging
import time
from decimal import Decimal
from typing import Sequence
from uuid import UUID

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.exceptions import MatchingError, PerformanceError
from app.models.route import Route
from app.repositories.route_repository import RouteRepository
from app.schemas.matching import (
    MatchRequest,
    MatchResponse,
    MatchResult,
    ScoreBreakdown,
)
from app.services.scoring_service import RouteScorer
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
        self.scorer = RouteScorer()
        self.user_service = UserService()

    async def match_routes(self, request: MatchRequest) -> MatchResponse:
        """
        Find and rank matching routes for rider request.

        Args:
            request: Match request with rider preferences

        Returns:
            MatchResponse: Ranked list of matching routes

        Raises:
            PerformanceError: If execution exceeds target time
        """
        start_time = time.time()

        try:
            # Step 1: Get candidate routes (geospatial filtering)
            radius_meters = request.radius_km * 1000
            candidate_routes = await self.route_repo.find_nearby_routes(
                origin_lat=request.origin_lat,
                origin_lon=request.origin_lon,
                dest_lat=request.dest_lat,
                dest_lon=request.dest_lon,
                radius_meters=radius_meters,
                max_results=settings.max_candidate_routes,
            )

            total_candidates = len(candidate_routes)
            logger.info(f"Found {total_candidates} candidate routes")

            # Step 2: Filter by time window
            time_filtered_routes = await self.route_repo.filter_by_time_window(
                routes=candidate_routes,
                desired_time=request.desired_time,
                window_minutes=settings.time_window_minutes,
            )

            logger.info(
                f"After time filtering: {len(time_filtered_routes)}/{total_candidates} routes"
            )

            # Step 3: Filter by minimum seats
            seat_filtered_routes = [
                r for r in time_filtered_routes if r.seats_available >= request.min_seats
            ]

            logger.info(
                f"After seat filtering: {len(seat_filtered_routes)}/{len(time_filtered_routes)} routes"
            )

            # Step 4: Filter by max price if specified
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

            # Step 5: Score and rank routes
            match_results = await self._score_and_rank_routes(
                routes=price_filtered_routes,
                request=request,
            )

            # Step 6: Fetch driver ratings (parallel for top 20)
            top_matches = match_results[:20]
            await self._enrich_with_driver_data(top_matches)

            # Check performance
            execution_time = int((time.time() - start_time) * 1000)
            logger.info(f"Matching completed in {execution_time}ms")

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
    ) -> list[MatchResult]:
        """
        Score and rank routes using composite algorithm.

        Args:
            routes: Candidate routes
            request: Match request

        Returns:
            list[MatchResult]: Sorted list of match results
        """
        all_prices = [r.base_price for r in routes]
        results: list[MatchResult] = []

        for route in routes:
            # Calculate component scores
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

            # Calculate composite score
            final_score = self.scorer.calculate_composite_score(
                route_match_score=route_match_score,
                time_match_score=time_match_score,
                rating_score=rating_score,
                price_score=price_score,
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
