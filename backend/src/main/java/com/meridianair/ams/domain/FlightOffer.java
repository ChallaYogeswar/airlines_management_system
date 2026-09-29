package com.meridianair.ams.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Deliberately separate from the live-ops `Flight` record used by
 * FlightStatusEngine/the departure board - that one represents a
 * flight's real-time gate status (boarding/delayed/on-runway) for
 * flights happening *today*. This one is sellable inventory: a
 * scheduled route + price + seat count that passengers search and book,
 * potentially weeks out. Conflating the two would mean every schedule
 * change fights with the live status simulator over the same row.
 *
 * totalSeats is capacity, fixed at creation (and only ever changed by an
 * admin action that also reconciles the actual Seat rows - see
 * AdminFlightOfferService). How many of those seats are actually free
 * right now is *not* stored here - it's computed from Seat.status, so
 * there's no separate counter that can drift out of sync with reality.
 */
@Entity
@Table(name = "flight_offers", indexes = {
        @Index(name = "idx_offers_route_time", columnList = "origin, destination, departureTime")
})
public class FlightOffer {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String flightNumber;

    @Column(nullable = false)
    private String origin;

    @Column(nullable = false)
    private String destination;

    @Column(nullable = false)
    private Instant departureTime;

    @Column(nullable = false)
    private Instant arrivalTime;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int totalSeats;

    /** Guards concurrent admin edits to this row (price/schedule/capacity)
     * - unrelated to seat-level concurrency, which is handled per-Seat. */
    @Version
    private long version;

    protected FlightOffer() {
    }

    public FlightOffer(String flightNumber, String origin, String destination, Instant departureTime,
                        Instant arrivalTime, BigDecimal price, int totalSeats) {
        this.flightNumber = flightNumber;
        this.origin = origin;
        this.destination = destination;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.price = price;
        this.totalSeats = totalSeats;
    }

    public UUID getId() { return id; }
    public String getFlightNumber() { return flightNumber; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public Instant getDepartureTime() { return departureTime; }
    public Instant getArrivalTime() { return arrivalTime; }
    public BigDecimal getPrice() { return price; }
    public int getTotalSeats() { return totalSeats; }

    public void applyAdminUpdate(BigDecimal price, int newTotalSeats, Instant departureTime, Instant arrivalTime) {
        this.price = price;
        this.totalSeats = newTotalSeats;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
    }
}
