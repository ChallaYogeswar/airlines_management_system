package com.meridianair.ams.booking;

import com.meridianair.ams.domain.FlightOffer;
import com.meridianair.ams.domain.SeatStatus;
import com.meridianair.ams.dto.FlightOfferSummary;
import com.meridianair.ams.repository.FlightOfferRepository;
import com.meridianair.ams.repository.SeatRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FlightSearchService {

    private final FlightOfferRepository flightOfferRepository;
    private final SeatRepository seatRepository;

    public FlightSearchService(FlightOfferRepository flightOfferRepository, SeatRepository seatRepository) {
        this.flightOfferRepository = flightOfferRepository;
        this.seatRepository = seatRepository;
    }

    public List<FlightOfferSummary> search(String origin, String destination, LocalDate date) {
        var from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        var to = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        return flightOfferRepository
                .findByOriginIgnoreCaseAndDestinationIgnoreCaseAndDepartureTimeBetweenOrderByDepartureTimeAsc(
                        origin, destination, from, to)
                .stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    public FlightOfferSummary getById(UUID id) {
        FlightOffer offer = flightOfferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));
        return toSummary(offer);
    }

    /** seatsAvailable is computed live from Seat rows, not read from a
     * stored counter on FlightOffer - see FlightOffer's class comment for
     * why that field was removed entirely rather than kept in sync. */
    private FlightOfferSummary toSummary(FlightOffer offer) {
        long available = seatRepository.countByFlightOfferAndStatus(offer, SeatStatus.AVAILABLE);
        return new FlightOfferSummary(
                offer.getId(), offer.getFlightNumber(), offer.getOrigin(), offer.getDestination(),
                offer.getDepartureTime(), offer.getArrivalTime(), offer.getPrice(), (int) available
        );
    }
}
