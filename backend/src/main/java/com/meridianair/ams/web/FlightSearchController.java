package com.meridianair.ams.web;

import com.meridianair.ams.booking.FlightSearchService;
import com.meridianair.ams.dto.FlightOfferSummary;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@Validated
public class FlightSearchController {

    private final FlightSearchService flightSearchService;

    public FlightSearchController(FlightSearchService flightSearchService) {
        this.flightSearchService = flightSearchService;
    }

    @GetMapping("/api/flights/search")
    public List<FlightOfferSummary> search(
            @RequestParam @NotBlank String origin,
            @RequestParam @NotBlank String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return flightSearchService.search(origin, destination, date);
    }

    @GetMapping("/api/flights/offers/{id}")
    public FlightOfferSummary getById(@org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
        return flightSearchService.getById(id);
    }
}
