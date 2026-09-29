package com.meridianair.ams.config;

import com.meridianair.ams.booking.SeatLayoutGenerator;
import com.meridianair.ams.domain.FlightOffer;
import com.meridianair.ams.repository.FlightOfferRepository;
import com.meridianair.ams.repository.SeatRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

@Component
@Order(2) // after RoleSeeder
public class FlightOfferSeeder implements CommandLineRunner {

    private final FlightOfferRepository flightOfferRepository;
    private final SeatRepository seatRepository;
    private final SeatLayoutGenerator seatLayoutGenerator;

    public FlightOfferSeeder(FlightOfferRepository flightOfferRepository, SeatRepository seatRepository,
                              SeatLayoutGenerator seatLayoutGenerator) {
        this.flightOfferRepository = flightOfferRepository;
        this.seatRepository = seatRepository;
        this.seatLayoutGenerator = seatLayoutGenerator;
    }

    @Override
    public void run(String... args) {
        if (flightOfferRepository.count() > 0) {
            return; // already seeded (e.g. a restart against a persistent DB)
        }

        LocalDate today = LocalDate.now();
        for (int daysOut = 0; daysOut < 14; daysOut++) {
            LocalDate date = today.plusDays(daysOut);
            seedRoute("SpiceJet 757", "MAA", "HYD", date, "06:40", "08:05", new BigDecimal("4899"), 6);
            seedRoute("IndiGo 340", "MAA", "BLR", date, "07:10", "08:15", new BigDecimal("3499"), 8);
            seedRoute("Akasa 123", "DEL", "BLR", date, "08:15", "11:00", new BigDecimal("6299"), 4);
            seedRoute("Indigo 667", "DEL", "HYD", date, "09:05", "11:20", new BigDecimal("5799"), 10);
            seedRoute("Air India 667", "DEL", "HYD", date, "09:40", "11:55", new BigDecimal("7199"), 2);
            seedRoute("Vistara 220", "BLR", "DEL", date, "10:20", "13:00", new BigDecimal("6899"), 12);
        }
    }

    private void seedRoute(String flightNumber, String origin, String destination, LocalDate date,
                            String departClock, String arriveClock, BigDecimal price, int seats) {
        var departureTime = date.atTime(LocalTime.parse(departClock)).toInstant(ZoneOffset.UTC);
        var arrivalTime = date.atTime(LocalTime.parse(arriveClock)).toInstant(ZoneOffset.UTC);
        FlightOffer offer = flightOfferRepository.save(
                new FlightOffer(flightNumber, origin, destination, departureTime, arrivalTime, price, seats));
        seatRepository.saveAll(seatLayoutGenerator.generate(offer, seats));
    }
}
