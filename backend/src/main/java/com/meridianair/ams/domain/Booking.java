package com.meridianair.ams.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_bookings_user", columnList = "user_id"),
        @Index(name = "idx_bookings_reference", columnList = "bookingReference")
})
public class Booking {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_offer_id", nullable = false)
    private FlightOffer flightOffer;

    @Column(nullable = false, unique = true, length = 8)
    private String bookingReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @ElementCollection
    @CollectionTable(name = "booking_passengers", joinColumns = @JoinColumn(name = "booking_id"))
    private List<PassengerDetail> passengers = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant cancelledAt;

    protected Booking() {
    }

    public Booking(User user, FlightOffer flightOffer, String bookingReference,
                   BigDecimal totalPrice, List<PassengerDetail> passengers) {
        this.user = user;
        this.flightOffer = flightOffer;
        this.bookingReference = bookingReference;
        this.totalPrice = totalPrice;
        this.passengers = passengers;
    }

    public void markPaid() {
        this.status = BookingStatus.CONFIRMED;
    }

    public void markPaymentFailed() {
        this.status = BookingStatus.PAYMENT_FAILED;
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    public boolean isCancellable() {
        return status != BookingStatus.CANCELLED;
    }

    public boolean isPayable() {
        return status == BookingStatus.PENDING_PAYMENT || status == BookingStatus.PAYMENT_FAILED;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public FlightOffer getFlightOffer() { return flightOffer; }
    public String getBookingReference() { return bookingReference; }
    public BookingStatus getStatus() { return status; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public List<PassengerDetail> getPassengers() { return passengers; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
