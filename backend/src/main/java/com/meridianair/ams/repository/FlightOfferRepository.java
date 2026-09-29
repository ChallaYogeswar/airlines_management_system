package com.meridianair.ams.repository;

import com.meridianair.ams.domain.FlightOffer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlightOfferRepository extends JpaRepository<FlightOffer, UUID> {

    List<FlightOffer> findByOriginIgnoreCaseAndDestinationIgnoreCaseAndDepartureTimeBetweenOrderByDepartureTimeAsc(
            String origin, String destination, Instant from, Instant to);

    /**
     * Takes a row-level write lock (SELECT ... FOR UPDATE) for the
     * duration of the caller's transaction. Booking creation no longer
     * needs this - seat-level concurrency is handled entirely by
     * SeatRepository.findByIdForUpdate now. This lock guards concurrent
     * *admin* edits to a flight's own fields (price, schedule, seat
     * capacity) - see AdminFlightOfferService.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FlightOffer f where f.id = :id")
    Optional<FlightOffer> findByIdForUpdate(@Param("id") UUID id);
}
