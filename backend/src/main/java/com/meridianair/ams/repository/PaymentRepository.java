package com.meridianair.ams.repository;

import com.meridianair.ams.domain.Booking;
import com.meridianair.ams.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByBooking(Booking booking);
}
