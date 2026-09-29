package com.meridianair.ams.booking;

import com.meridianair.ams.audit.AuditService;
import com.meridianair.ams.domain.*;
import com.meridianair.ams.dto.BookedPassenger;
import com.meridianair.ams.dto.BookingSummary;
import com.meridianair.ams.dto.CreateBookingRequest;
import com.meridianair.ams.dto.PassengerDetailRequest;
import com.meridianair.ams.repository.BookingRepository;
import com.meridianair.ams.repository.FlightOfferRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private static final String REFERENCE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final int REFERENCE_LENGTH = 6;
    private static final int MAX_REFERENCE_ATTEMPTS = 10;

    /** BUSINESS seats cost 1.6x the flight's base economy price - the
     * seat map's class distinction needs to mean something, not just be
     * a color on a grid. */
    private static final BigDecimal BUSINESS_MULTIPLIER = new BigDecimal("1.6");

    private final FlightOfferRepository flightOfferRepository;
    private final BookingRepository bookingRepository;
    private final SeatService seatService;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public BookingService(FlightOfferRepository flightOfferRepository, BookingRepository bookingRepository,
                           SeatService seatService, AuditService auditService) {
        this.flightOfferRepository = flightOfferRepository;
        this.bookingRepository = bookingRepository;
        this.seatService = seatService;
        this.auditService = auditService;
    }

    /**
     * Creates a PENDING_PAYMENT booking against seats the user already
     * holds - does NOT transition seats to BOOKED. That only happens once
     * PaymentService confirms a successful charge, so an abandoned
     * checkout (created a booking, never paid) doesn't permanently lock
     * seats beyond their normal hold expiry.
     */
    @Transactional
    public BookingSummary createBooking(User user, CreateBookingRequest request) {
        FlightOffer offer = flightOfferRepository.findById(request.flightOfferId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));

        List<PassengerDetailRequest> passengerRequests = request.passengers();
        Set<UUID> seatIds = passengerRequests.stream().map(PassengerDetailRequest::seatId).collect(Collectors.toSet());
        if (seatIds.size() != passengerRequests.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each passenger must have a distinct seat");
        }

        List<SeatService.ConfirmedSeat> verifiedSeats =
                seatService.verifyHeldByUser(user.getId(), new ArrayList<>(seatIds));
        Map<UUID, SeatService.ConfirmedSeat> bySeatId = verifiedSeats.stream()
                .collect(Collectors.toMap(SeatService.ConfirmedSeat::id, s -> s));

        List<PassengerDetail> passengers = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;
        for (PassengerDetailRequest p : passengerRequests) {
            SeatService.ConfirmedSeat seat = bySeatId.get(p.seatId());
            BigDecimal seatPrice = seat.seatClass() == SeatClass.BUSINESS
                    ? offer.getPrice().multiply(BUSINESS_MULTIPLIER)
                    : offer.getPrice();
            totalPrice = totalPrice.add(seatPrice);
            passengers.add(new PassengerDetail(p.firstName(), p.lastName(), p.dateOfBirth(), seat.id(), seat.seatNumber()));
        }

        Booking booking = new Booking(user, offer, generateUniqueReference(), totalPrice, passengers);
        Booking saved = bookingRepository.save(booking);

        auditService.log(AuditLog.builder()
                .userId(user.getId()).eventType("booking.created").eventCategory("booking")
                .action("create").resourceType("booking").resourceId(saved.getId().toString())
                .status("success").message(passengers.size() + " seat(s) on " + offer.getFlightNumber() + ", pending payment"));

        return toSummary(saved);
    }

    @Transactional
    public void cancelBooking(User user, UUID bookingId) {
        Booking booking = requireOwnedBy(user, bookingId);

        if (!booking.isCancellable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is already cancelled");
        }

        List<UUID> seatIds = booking.getPassengers().stream()
                .map(PassengerDetail::getSeatId)
                .collect(Collectors.toList());
        seatService.releaseSeats(seatIds);

        booking.cancel();
        bookingRepository.save(booking);

        auditService.log(AuditLog.builder()
                .userId(user.getId()).eventType("booking.cancelled").eventCategory("booking")
                .action("cancel").resourceType("booking").resourceId(booking.getId().toString())
                .status("success"));
    }

    public List<BookingSummary> listMine(User user) {
        return bookingRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    public Page<BookingSummary> listAllForAdmin(Pageable pageable) {
        return bookingRepository.findAll(pageable).map(this::toSummary);
    }

    /** Package-private-ish helper shared with PaymentService - both need
     * "fetch this booking, but only if it belongs to this user, else 404
     * (never 403 - don't reveal that an ID belongs to someone else)". */
    Booking requireOwnedBy(User user, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (!booking.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        return booking;
    }

    BookingSummary toSummary(Booking booking) {
        FlightOffer offer = booking.getFlightOffer();
        List<BookedPassenger> passengers = booking.getPassengers().stream()
                .map(p -> new BookedPassenger(p.getFirstName(), p.getLastName(), p.getDateOfBirth(), p.getSeatNumber()))
                .collect(Collectors.toList());

        return new BookingSummary(
                booking.getId(), booking.getBookingReference(), offer.getFlightNumber(),
                offer.getOrigin(), offer.getDestination(), offer.getDepartureTime(), offer.getArrivalTime(),
                booking.getStatus(), booking.getTotalPrice(), passengers, booking.getCreatedAt()
        );
    }

    private String generateUniqueReference() {
        for (int attempt = 0; attempt < MAX_REFERENCE_ATTEMPTS; attempt++) {
            String candidate = randomReference();
            if (!bookingRepository.existsByBookingReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique booking reference after "
                + MAX_REFERENCE_ATTEMPTS + " attempts");
    }

    private String randomReference() {
        StringBuilder sb = new StringBuilder(REFERENCE_LENGTH);
        for (int i = 0; i < REFERENCE_LENGTH; i++) {
            sb.append(REFERENCE_ALPHABET.charAt(secureRandom.nextInt(REFERENCE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
