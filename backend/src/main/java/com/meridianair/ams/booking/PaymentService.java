package com.meridianair.ams.booking;

import com.meridianair.ams.audit.AuditService;
import com.meridianair.ams.domain.AuditLog;
import com.meridianair.ams.domain.Booking;
import com.meridianair.ams.domain.BookingStatus;
import com.meridianair.ams.domain.Payment;
import com.meridianair.ams.domain.PassengerDetail;
import com.meridianair.ams.domain.User;
import com.meridianair.ams.dto.PaymentRequest;
import com.meridianair.ams.dto.PaymentResult;
import com.meridianair.ams.payment.PaymentGateway;
import com.meridianair.ams.repository.BookingRepository;
import com.meridianair.ams.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final String PROVIDER_NAME = "simulated";

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final SeatService seatService;
    private final AuditService auditService;

    public PaymentService(BookingService bookingService, BookingRepository bookingRepository,
                           PaymentRepository paymentRepository, PaymentGateway paymentGateway,
                           SeatService seatService, AuditService auditService) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.seatService = seatService;
        this.auditService = auditService;
    }

    /**
     * Seats are only ever transitioned HELD -> BOOKED here, after the
     * gateway reports success - never at booking creation. If the charge
     * fails, the booking moves to PAYMENT_FAILED but seats stay HELD
     * (until their normal expiry), so the passenger can fix their card
     * and retry via this same endpoint without losing their seats.
     *
     * seatService.confirmSeats runs inside this same transaction and can
     * itself throw (hold expired between booking creation and now) -
     * that rolls back the payment record and booking status update
     * together with it, rather than leaving a SUCCEEDED payment attached
     * to a booking whose seats were never actually secured.
     */
    @Transactional
    public PaymentResult pay(User user, UUID bookingId, PaymentRequest request) {
        Booking booking = bookingService.requireOwnedBy(user, bookingId);

        if (!booking.isPayable()) {
            String reason = booking.getStatus() == BookingStatus.CONFIRMED
                    ? "This booking has already been paid"
                    : "This booking has been cancelled";
            throw new ResponseStatusException(HttpStatus.CONFLICT, reason);
        }

        PaymentGateway.PaymentGatewayResult result = paymentGateway.charge(new PaymentGateway.PaymentGatewayRequest(
                booking.getTotalPrice(), request.cardNumber(), request.expiryMonth(), request.expiryYear(),
                request.cvv(), request.cardholderName()
        ));

        Payment payment = new Payment(booking, booking.getTotalPrice(), PROVIDER_NAME);

        if (result.success()) {
            List<UUID> seatIds = booking.getPassengers().stream()
                    .map(PassengerDetail::getSeatId)
                    .collect(Collectors.toList());
            seatService.confirmSeats(user.getId(), seatIds);

            payment.markSucceeded(result.providerReference(), result.cardLast4());
            booking.markPaid();

            auditService.log(AuditLog.builder()
                    .userId(user.getId()).eventType("payment.succeeded").eventCategory("payment")
                    .action("charge").resourceType("booking").resourceId(booking.getId().toString())
                    .status("success").message("card ending " + result.cardLast4()));
        } else {
            payment.markFailed(result.cardLast4(), result.declineReason());
            booking.markPaymentFailed();

            auditService.log(AuditLog.builder()
                    .userId(user.getId()).eventType("payment.failed").eventCategory("payment")
                    .action("charge").resourceType("booking").resourceId(booking.getId().toString())
                    .status("failure").message(result.declineReason()));
        }

        paymentRepository.save(payment);
        bookingRepository.save(booking);

        return new PaymentResult(result.success(), result.success() ? null : result.declineReason(),
                bookingService.toSummary(booking));
    }
}
