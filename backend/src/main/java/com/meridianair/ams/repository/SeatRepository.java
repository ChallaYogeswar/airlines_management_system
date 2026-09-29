package com.meridianair.ams.repository;

import com.meridianair.ams.domain.FlightOffer;
import com.meridianair.ams.domain.Seat;
import com.meridianair.ams.domain.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByFlightOfferOrderBySeatNumberAsc(FlightOffer offer);

    long countByFlightOfferAndStatus(FlightOffer offer, SeatStatus status);

    List<Seat> findByFlightOfferAndStatus(FlightOffer offer, SeatStatus status);

    /** Same locking pattern as FlightOfferRepository.findByIdForUpdate,
     * applied per-seat instead of per-flight - this is what makes two
     * passengers racing for the same seat resolve correctly instead of
     * both succeeding. Callers must lock multiple seats in a consistent
     * (sorted) order to avoid deadlocking against another transaction
     * locking the same seats in reverse order - see SeatService. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") UUID id);

    List<Seat> findByStatusAndHoldExpiresAtBefore(SeatStatus status, Instant cutoff);
}
