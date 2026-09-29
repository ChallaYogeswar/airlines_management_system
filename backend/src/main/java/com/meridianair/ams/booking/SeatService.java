package com.meridianair.ams.booking;

import com.meridianair.ams.domain.FlightOffer;
import com.meridianair.ams.domain.Seat;
import com.meridianair.ams.domain.SeatStatus;
import com.meridianair.ams.dto.SeatHoldResponse;
import com.meridianair.ams.dto.SeatSummary;
import com.meridianair.ams.repository.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The actual concurrency-critical logic behind seat selection. Two
 * passengers clicking the same seat within milliseconds of each other is
 * the normal case this has to get right, not an edge case.
 *
 * Locking discipline: every method that touches more than one seat sorts
 * seat IDs before locking them one at a time via
 * SeatRepository.findByIdForUpdate. Without that, two concurrent
 * multi-seat requests that happen to want an overlapping set of seats in
 * different orders could each hold one lock the other needs -> deadlock.
 * Sorting first means every transaction acquires locks in the same
 * global order, which makes that impossible.
 */
@Service
public class SeatService {

    private static final Logger log = LoggerFactory.getLogger(SeatService.class);
    private static final Duration HOLD_DURATION = Duration.ofMinutes(5);

    private final SeatRepository seatRepository;

    public SeatService(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    public List<SeatSummary> listForFlight(FlightOffer offer, UUID viewingUserId) {
        return seatRepository.findByFlightOfferOrderBySeatNumberAsc(offer).stream()
                .map(seat -> toSummary(seat, viewingUserId))
                .collect(Collectors.toList());
    }

    /**
     * All-or-nothing: if any requested seat can't be held, the whole call
     * fails and nothing is held. This runs as one transaction, so
     * throwing after holding some seats rolls all of them back - there's
     * no manual "undo the ones I already took" step needed.
     */
    @Transactional
    public SeatHoldResponse holdSeats(UUID userId, List<UUID> seatIds) {
        List<UUID> sorted = seatIds.stream().sorted().collect(Collectors.toList());
        Instant expiresAt = Instant.now().plus(HOLD_DURATION);
        List<Seat> held = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();

        for (UUID seatId : sorted) {
            Seat seat = seatRepository.findByIdForUpdate(seatId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat not found"));

            boolean freeForMe = seat.getStatus() == SeatStatus.AVAILABLE
                    || seat.isHoldExpired()
                    || (seat.getStatus() == SeatStatus.HELD && userId.equals(seat.getHeldByUserId()));

            if (!freeForMe) {
                conflicts.add(seat.getSeatNumber());
                continue;
            }

            seat.hold(userId, expiresAt);
            seatRepository.save(seat);
            held.add(seat);
        }

        if (!conflicts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seat(s) no longer available: " + String.join(", ", conflicts));
        }

        List<SeatSummary> summaries = held.stream()
                .sorted(Comparator.comparing(Seat::getSeatNumber))
                .map(seat -> toSummary(seat, userId))
                .collect(Collectors.toList());
        return new SeatHoldResponse(summaries, expiresAt);
    }

    @Transactional
    public void releaseHold(UUID userId, UUID seatId) {
        seatRepository.findByIdForUpdate(seatId).ifPresent(seat -> {
            if (seat.getStatus() == SeatStatus.HELD && userId.equals(seat.getHeldByUserId())) {
                seat.release();
                seatRepository.save(seat);
            }
        });
    }

    /**
     * Read-only verification used at booking-creation time: confirms
     * every seat is currently held by this user (and not expired)
     * without changing anything. Booking creation needs seat number and
     * class to build passenger records and price the booking, but
     * shouldn't transition seats to BOOKED until payment actually
     * succeeds - that happens later, in confirmSeats, called from
     * PaymentService once the charge clears.
     */
    @Transactional
    public List<ConfirmedSeat> verifyHeldByUser(UUID userId, List<UUID> seatIds) {
        List<ConfirmedSeat> verified = new ArrayList<>();
        for (UUID seatId : seatIds.stream().sorted().collect(Collectors.toList())) {
            Seat seat = seatRepository.findByIdForUpdate(seatId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat not found"));

            boolean stillMine = seat.getStatus() == SeatStatus.HELD
                    && userId.equals(seat.getHeldByUserId())
                    && !seat.isHoldExpired();

            if (!stillMine) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Hold on seat " + seat.getSeatNumber() + " has expired or is no longer yours - please reselect");
            }
            verified.add(new ConfirmedSeat(seat.getId(), seat.getSeatNumber(), seat.getSeatClass()));
        }
        return verified;
    }

    /**
     * Converts this user's held seats to BOOKED. Called from
     * PaymentService once a charge succeeds - Spring's default
     * propagation means this joins that same transaction rather than
     * opening a second one, so if this throws (hold expired between
     * booking creation and payment completing), the payment record and
     * booking status update roll back together with it. Returns the
     * confirmed seats' numbers/classes so the caller doesn't need a
     * second query.
     */
    @Transactional
    public List<ConfirmedSeat> confirmSeats(UUID userId, List<UUID> seatIds) {
        List<ConfirmedSeat> confirmed = new ArrayList<>();
        for (UUID seatId : seatIds.stream().sorted().collect(Collectors.toList())) {
            Seat seat = seatRepository.findByIdForUpdate(seatId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat not found"));

            boolean stillMine = seat.getStatus() == SeatStatus.HELD
                    && userId.equals(seat.getHeldByUserId())
                    && !seat.isHoldExpired();

            if (!stillMine) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Hold on seat " + seat.getSeatNumber() + " has expired or is no longer yours - please reselect");
            }
            seat.book();
            seatRepository.save(seat);
            confirmed.add(new ConfirmedSeat(seat.getId(), seat.getSeatNumber(), seat.getSeatClass()));
        }
        return confirmed;
    }

    public record ConfirmedSeat(UUID id, String seatNumber, com.meridianair.ams.domain.SeatClass seatClass) {}

    /** Releases seats back to AVAILABLE regardless of their current state
     * (HELD or BOOKED) - used both when cancelling a confirmed booking
     * and when cancelling one that never got past payment. */
    @Transactional
    public void releaseSeats(List<UUID> seatIds) {
        for (UUID seatId : seatIds.stream().sorted().collect(Collectors.toList())) {
            seatRepository.findByIdForUpdate(seatId).ifPresent(seat -> {
                seat.release();
                seatRepository.save(seat);
            });
        }
    }

    /** Backstop for holds nobody explicitly released - a passenger who
     * closes the tab mid-selection shouldn't keep a seat locked forever.
     * The 5-minute hold window means at most a 1-minute-late release on
     * top of that, which is an acceptable trade for not needing anything
     * fancier than a fixed-rate sweep. */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void sweepExpiredHolds() {
        List<Seat> expired = seatRepository.findByStatusAndHoldExpiresAtBefore(SeatStatus.HELD, Instant.now());
        if (expired.isEmpty()) return;

        for (Seat seat : expired) {
            seat.release();
            seatRepository.save(seat);
        }
        log.debug("Released {} expired seat hold(s)", expired.size());
    }

    private SeatSummary toSummary(Seat seat, UUID viewingUserId) {
        boolean heldByMe = seat.getStatus() == SeatStatus.HELD
                && viewingUserId != null
                && viewingUserId.equals(seat.getHeldByUserId());
        return new SeatSummary(seat.getId(), seat.getSeatNumber(), seat.getSeatClass(), seat.getStatus(), heldByMe);
    }
}
