package com.meridianair.ams.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDate;
import java.util.UUID;

@Embeddable
public class PassengerDetail {

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private LocalDate dateOfBirth;

    /** The Seat this passenger was assigned. Kept as a plain UUID rather
     * than a @ManyToOne - this is an @Embeddable inside an
     * @ElementCollection, and a booking's passenger record should stay
     * readable even if the seat entity itself is ever deleted (e.g. a
     * flight offer being torn down long after it has flown). seatNumber
     * is a denormalized snapshot for exactly the same reason: display
     * shouldn't break if the live Seat row is gone. */
    private UUID seatId;

    private String seatNumber;

    protected PassengerDetail() {
    }

    public PassengerDetail(String firstName, String lastName, LocalDate dateOfBirth, UUID seatId, String seatNumber) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.seatId = seatId;
        this.seatNumber = seatNumber;
    }

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public UUID getSeatId() { return seatId; }
    public String getSeatNumber() { return seatNumber; }
}
