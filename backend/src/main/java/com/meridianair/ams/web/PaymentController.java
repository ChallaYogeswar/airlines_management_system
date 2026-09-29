package com.meridianair.ams.web;

import com.meridianair.ams.booking.PaymentService;
import com.meridianair.ams.dto.PaymentRequest;
import com.meridianair.ams.dto.PaymentResult;
import com.meridianair.ams.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUserProvider currentUserProvider;

    public PaymentController(PaymentService paymentService, CurrentUserProvider currentUserProvider) {
        this.paymentService = paymentService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/api/bookings/{bookingId}/pay")
    public PaymentResult pay(@PathVariable UUID bookingId, @Valid @RequestBody PaymentRequest request) {
        return paymentService.pay(currentUserProvider.require(), bookingId, request);
    }
}
