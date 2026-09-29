package com.meridianair.ams.web;

import com.meridianair.ams.booking.FlightSearchService;
import com.meridianair.ams.booking.SeatService;
import com.meridianair.ams.domain.FlightOffer;
import com.meridianair.ams.dto.SeatHoldRequest;
import com.meridianair.ams.dto.SeatHoldResponse;
import com.meridianair.ams.dto.SeatSummary;
import com.meridianair.ams.repository.FlightOfferRepository;
import com.meridianair.ams.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/flights/offers/{flightOfferId}/seats")
public class SeatController {

    private final SeatService seatService;
    private final FlightOfferRepository flightOfferRepository;
    private final CurrentUserProvider currentUserProvider;

    public SeatController(SeatService seatService, FlightOfferRepository flightOfferRepository,
                           CurrentUserProvider currentUserProvider) {
        this.seatService = seatService;
        this.flightOfferRepository = flightOfferRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /** Public - browsing the seat map (e.g. to see what's left before
     * signing in) doesn't need an account, only holding/booking does. */
    @GetMapping
    public List<SeatSummary> list(@PathVariable UUID flightOfferId) {
        FlightOffer offer = requireOffer(flightOfferId);
        UUID viewingUserId = currentUserIdOrNull();
        return seatService.listForFlight(offer, viewingUserId);
    }

    @PostMapping("/hold")
    public SeatHoldResponse hold(@PathVariable UUID flightOfferId, @Valid @RequestBody SeatHoldRequest request) {
        requireOffer(flightOfferId); // 404 early if the flight itself doesn't exist
        UUID userId = currentUserProvider.require().getId();
        return seatService.holdSeats(userId, request.seatIds());
    }

    @DeleteMapping("/{seatId}/hold")
    public void release(@PathVariable UUID flightOfferId, @PathVariable UUID seatId) {
        UUID userId = currentUserProvider.require().getId();
        seatService.releaseHold(userId, seatId);
    }

    private FlightOffer requireOffer(UUID id) {
        return flightOfferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));
    }

    /** Seat map viewing is public, but a logged-in viewer should still see
     * their own in-progress holds highlighted - so this reads the auth
     * context if present without requiring it, unlike
     * CurrentUserProvider.require(). */
    private UUID currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof UUID userId) ? userId : null;
    }
}
