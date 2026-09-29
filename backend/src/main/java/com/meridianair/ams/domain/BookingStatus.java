package com.meridianair.ams.domain;

public enum BookingStatus {
    /** Seats are held (see Seat/SeatService) but not yet paid for. */
    PENDING_PAYMENT,
    /** Most recent payment attempt was declined - seats remain held
     * (subject to the normal hold expiry) so the passenger can retry
     * without losing their seats immediately. */
    PAYMENT_FAILED,
    CONFIRMED,
    CANCELLED
}
