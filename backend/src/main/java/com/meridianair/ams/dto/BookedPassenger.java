package com.meridianair.ams.dto;

import java.time.LocalDate;

/** Output-only view of a passenger on a confirmed booking - deliberately
 * separate from PassengerDetailRequest (the input shape), since the
 * response also needs the assigned seat number and has no reason to
 * accept one back. */
public record BookedPassenger(
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String seatNumber
) {}
