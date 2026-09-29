package com.meridianair.ams.booking;

import com.meridianair.ams.domain.FlightOffer;
import com.meridianair.ams.domain.Seat;
import com.meridianair.ams.domain.SeatStatus;
import com.meridianair.ams.dto.AdminFlightOfferSummary;
import com.meridianair.ams.dto.CreateFlightOfferRequest;
import com.meridianair.ams.dto.UpdateFlightOfferRequest;
import com.meridianair.ams.repository.FlightOfferRepository;
import com.meridianair.ams.repository.SeatRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AdminFlightOfferService {

    private final FlightOfferRepository flightOfferRepository;
    private final SeatRepository seatRepository;
    private final SeatLayoutGenerator seatLayoutGenerator;

    public AdminFlightOfferService(FlightOfferRepository flightOfferRepository, SeatRepository seatRepository,
                                    SeatLayoutGenerator seatLayoutGenerator) {
        this.flightOfferRepository = flightOfferRepository;
        this.seatRepository = seatRepository;
        this.seatLayoutGenerator = seatLayoutGenerator;
    }

    public Page<AdminFlightOfferSummary> listAll(Pageable pageable) {
        return flightOfferRepository.findAll(pageable).map(this::toSummary);
    }

    @Transactional
    public AdminFlightOfferSummary create(CreateFlightOfferRequest request) {
        if (!request.arrivalTime().isAfter(request.departureTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arrival must be after departure");
        }
        FlightOffer offer = flightOfferRepository.save(new FlightOffer(
                request.flightNumber(), request.origin().toUpperCase(), request.destination().toUpperCase(),
                request.departureTime(), request.arrivalTime(), request.price(), request.totalSeats()
        ));

        seatRepository.saveAll(seatLayoutGenerator.generate(offer, request.totalSeats()));

        return toSummary(offer);
    }

    /**
     * Runs under the same pessimistic lock booking creation uses
     * (findByIdForUpdate) for the FlightOffer row itself - guards
     * concurrent admin edits to price/schedule/capacity. Capacity
     * shrinkage additionally locks each candidate Seat individually
     * before deleting it (see shrinkSeatCapacity), because a seat could
     * be booked by a passenger in the moment between reading "this seat
     * is available" and actually removing it.
     */
    @Transactional
    public AdminFlightOfferSummary update(UUID id, UpdateFlightOfferRequest request) {
        FlightOffer offer = flightOfferRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));

        if (!request.arrivalTime().isAfter(request.departureTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arrival must be after departure");
        }

        int currentTotal = offer.getTotalSeats();
        int newTotal = request.totalSeats();

        if (newTotal > currentTotal) {
            seatRepository.saveAll(seatLayoutGenerator.generateAdditional(offer, currentTotal, newTotal));
        } else if (newTotal < currentTotal) {
            shrinkSeatCapacity(offer, currentTotal - newTotal);
        }

        offer.applyAdminUpdate(request.price(), newTotal, request.departureTime(), request.arrivalTime());
        return toSummary(flightOfferRepository.save(offer));
    }

    @Transactional
    public void delete(UUID id) {
        FlightOffer offer = flightOfferRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));

        boolean hasActiveBookings = seatRepository.countByFlightOfferAndStatus(offer, SeatStatus.BOOKED) > 0
                || seatRepository.countByFlightOfferAndStatus(offer, SeatStatus.HELD) > 0;
        if (hasActiveBookings) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Can't delete a flight with active bookings or held seats - cancel them first");
        }

        seatRepository.deleteAll(seatRepository.findByFlightOfferOrderBySeatNumberAsc(offer));
        flightOfferRepository.delete(offer);
    }

    private void shrinkSeatCapacity(FlightOffer offer, int seatsToRemove) {
        List<Seat> candidates = seatRepository.findByFlightOfferAndStatus(offer, SeatStatus.AVAILABLE);
        List<Seat> toDelete = new ArrayList<>();

        for (Seat candidate : candidates) {
            if (toDelete.size() >= seatsToRemove) break;
            // re-lock and re-check - a seat could be booked between the
            // query above and this point
            seatRepository.findByIdForUpdate(candidate.getId())
                    .filter(locked -> locked.getStatus() == SeatStatus.AVAILABLE)
                    .ifPresent(toDelete::add);
        }

        if (toDelete.size() < seatsToRemove) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Can't shrink capacity by " + seatsToRemove + " - only " + toDelete.size()
                            + " seat(s) are currently free to remove");
        }

        seatRepository.deleteAll(toDelete);
    }

    private AdminFlightOfferSummary toSummary(FlightOffer offer) {
        long available = seatRepository.countByFlightOfferAndStatus(offer, SeatStatus.AVAILABLE);
        return new AdminFlightOfferSummary(
                offer.getId(), offer.getFlightNumber(), offer.getOrigin(), offer.getDestination(),
                offer.getDepartureTime(), offer.getArrivalTime(), offer.getPrice(),
                offer.getTotalSeats(), (int) available
        );
    }
}
