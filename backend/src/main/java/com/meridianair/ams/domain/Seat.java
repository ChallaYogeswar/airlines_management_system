package com.meridianair.ams.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per physical seat per flight. This replaces an earlier design
 * that tracked only a `seatsAvailable` count on FlightOffer - that works
 * fine for "how many seats are left" but can't answer "which seat did
 * this passenger get" or prevent two passengers from being assigned the
 * *same* seat, which is the actual problem a real airline has to solve.
 * Concurrency now happens at the seat level (see SeatService), not the
 * flight level.
 */
@Entity
@Table(name = "seats", indexes = {
        @Index(name = "idx_seats_flight_offer", columnList = "flight_offer_id"),
        @Index(name = "idx_seats_hold_expiry", columnList = "status, holdExpiresAt")
})
public class Seat {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_offer_id", nullable = false)
    private FlightOffer flightOffer;

    @Column(nullable = false)
    private String seatNumber; // e.g. "1A"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatClass seatClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status = SeatStatus.AVAILABLE;

    private UUID heldByUserId;
    private Instant holdExpiresAt;

    /** Defense-in-depth alongside the pessimistic locking SeatService
     * actually relies on - see FlightOfferRepository/SeatRepository's
     * findByIdForUpdate for why pessimistic (not optimistic) locking is
     * the primary mechanism here. */
    @Version
    private long version;

    protected Seat() {
    }

    public Seat(FlightOffer flightOffer, String seatNumber, SeatClass seatClass) {
        this.flightOffer = flightOffer;
        this.seatNumber = seatNumber;
        this.seatClass = seatClass;
    }

    public boolean isHoldExpired() {
        return status == SeatStatus.HELD && holdExpiresAt != null && holdExpiresAt.isBefore(Instant.now());
    }

    public void hold(UUID userId, Instant expiresAt) {
        this.status = SeatStatus.HELD;
        this.heldByUserId = userId;
        this.holdExpiresAt = expiresAt;
    }

    public void release() {
        this.status = SeatStatus.AVAILABLE;
        this.heldByUserId = null;
        this.holdExpiresAt = null;
    }

    public void book() {
        this.status = SeatStatus.BOOKED;
        this.holdExpiresAt = null;
    }

    public UUID getId() { return id; }
    public FlightOffer getFlightOffer() { return flightOffer; }
    public String getSeatNumber() { return seatNumber; }
    public SeatClass getSeatClass() { return seatClass; }
    public SeatStatus getStatus() { return status; }
    public UUID getHeldByUserId() { return heldByUserId; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
}
