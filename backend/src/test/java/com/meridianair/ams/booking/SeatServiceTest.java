package com.meridianair.ams.booking;

import com.meridianair.ams.domain.FlightOffer;
import com.meridianair.ams.domain.Seat;
import com.meridianair.ams.domain.SeatClass;
import com.meridianair.ams.domain.SeatStatus;
import com.meridianair.ams.repository.SeatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock private SeatRepository seatRepository;

    @Test
    void holdsAvailableSeatForRequestingUser() {
        UUID seatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Seat seat = newSeat(seatId);
        when(seatRepository.findByIdForUpdate(seatId)).thenReturn(Optional.of(seat));
        when(seatRepository.save(seat)).thenReturn(seat);

        var response = new SeatService(seatRepository).holdSeats(userId, java.util.List.of(seatId));

        assertEquals(SeatStatus.HELD, seat.getStatus());
        assertEquals(userId, seat.getHeldByUserId());
        assertTrue(seat.getHoldExpiresAt().isAfter(Instant.now()));
        assertEquals(1, response.heldSeats().size());
        verify(seatRepository).save(seat);
    }

    @Test
    void rejectsSeatHeldByAnotherUserWithoutSaving() {
        UUID seatId = UUID.randomUUID();
        Seat seat = newSeat(seatId);
        seat.hold(UUID.randomUUID(), Instant.now().plusSeconds(300));
        when(seatRepository.findByIdForUpdate(seatId)).thenReturn(Optional.of(seat));

        assertThrows(ResponseStatusException.class,
                () -> new SeatService(seatRepository).holdSeats(UUID.randomUUID(), java.util.List.of(seatId)));

        verify(seatRepository, never()).save(seat);
    }

    private Seat newSeat(UUID id) {
        FlightOffer offer = new FlightOffer("MA101", "MAA", "BLR", Instant.now(), Instant.now().plusSeconds(3600),
                new BigDecimal("100.00"), 20);
        Seat seat = new Seat(offer, "1A", SeatClass.ECONOMY);
        ReflectionTestUtils.setField(seat, "id", id);
        return seat;
    }
}