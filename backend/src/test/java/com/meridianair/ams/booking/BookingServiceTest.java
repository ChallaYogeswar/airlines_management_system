package com.meridianair.ams.booking;

import com.meridianair.ams.audit.AuditService;
import com.meridianair.ams.domain.*;
import com.meridianair.ams.dto.CreateBookingRequest;
import com.meridianair.ams.dto.PassengerDetailRequest;
import com.meridianair.ams.repository.BookingRepository;
import com.meridianair.ams.repository.FlightOfferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private FlightOfferRepository flightOfferRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private SeatService seatService;
    @Mock private AuditService auditService;

    @Test
    void createsPendingBookingFromUserHeldSeatsWithoutBookingThem() {
        UUID userId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        User user = new User("passenger@example.com", "Passenger", "encoded-password");
        ReflectionTestUtils.setField(user, "id", userId);
        FlightOffer offer = new FlightOffer("MA101", "MAA", "BLR", Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200), new BigDecimal("100.00"), 20);
        ReflectionTestUtils.setField(offer, "id", offerId);
        when(flightOfferRepository.findById(offerId)).thenReturn(Optional.of(offer));
        when(seatService.verifyHeldByUser(userId, List.of(seatId))).thenReturn(List.of(
                new SeatService.ConfirmedSeat(seatId, "1A", SeatClass.BUSINESS)));
        when(bookingRepository.existsByBookingReference(any())).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
            return booking;
        });

        Booking result = captureBooking(user, new CreateBookingRequest(offerId, List.of(
                new PassengerDetailRequest("Ari", "Patel", LocalDate.of(1990, 1, 1), seatId))));

        assertEquals(BookingStatus.PENDING_PAYMENT, result.getStatus());
        assertEquals(new BigDecimal("160.000"), result.getTotalPrice());
        verify(seatService).verifyHeldByUser(userId, List.of(seatId));
        verify(seatService, never()).confirmSeats(any(), any());
    }

    @Test
    void rejectsDuplicatePassengerSeatAssignments() {
        UUID offerId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        when(flightOfferRepository.findById(offerId)).thenReturn(Optional.of(
                new FlightOffer("MA101", "MAA", "BLR", Instant.now(), Instant.now().plusSeconds(3600),
                        new BigDecimal("100.00"), 20)));
        CreateBookingRequest request = new CreateBookingRequest(offerId, List.of(
                new PassengerDetailRequest("Ari", "Patel", LocalDate.of(1990, 1, 1), seatId),
                new PassengerDetailRequest("Sam", "Rao", LocalDate.of(1992, 2, 2), seatId)));
        BookingService service = new BookingService(flightOfferRepository, bookingRepository, seatService, auditService);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createBooking(new User("passenger@example.com", "Passenger", "hash"), request));

        assertEquals(400, exception.getStatusCode().value());
        verify(seatService, never()).verifyHeldByUser(any(), any());
    }

    private Booking captureBooking(User user, CreateBookingRequest request) {
        BookingService service = new BookingService(flightOfferRepository, bookingRepository, seatService, auditService);
        service.createBooking(user, request);
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        return captor.getValue();
    }
}