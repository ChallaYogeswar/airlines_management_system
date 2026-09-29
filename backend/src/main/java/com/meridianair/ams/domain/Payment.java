package com.meridianair.ams.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Deliberately never stores a card number, expiry, or CVV - only what's
 * safe to keep: the last 4 digits (for a passenger to recognize which
 * card they used), a provider reference, and the outcome. Real payment
 * processors work the same way for the same reason: full card data is a
 * liability nobody wants to hold longer than the single charge attempt
 * needs it.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false)
    private String provider;

    private String providerReference;
    private String cardLast4;
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant completedAt;

    protected Payment() {
    }

    public Payment(Booking booking, BigDecimal amount, String provider) {
        this.booking = booking;
        this.amount = amount;
        this.provider = provider;
    }

    public void markSucceeded(String providerReference, String cardLast4) {
        this.status = PaymentStatus.SUCCEEDED;
        this.providerReference = providerReference;
        this.cardLast4 = cardLast4;
        this.completedAt = Instant.now();
    }

    public void markFailed(String cardLast4, String failureReason) {
        this.status = PaymentStatus.FAILED;
        this.cardLast4 = cardLast4;
        this.failureReason = failureReason;
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Booking getBooking() { return booking; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getProviderReference() { return providerReference; }
    public String getCardLast4() { return cardLast4; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
