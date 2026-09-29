package com.meridianair.ams.engine;

import com.meridianair.ams.domain.Flight;
import com.meridianair.ams.domain.FlightStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Authoritative flight-lifecycle state machine. This is the server-side
 * twin of Angular's FlightBoardService (core/flight-board.service.ts) -
 * same states, same transition rules, same tick cadence by default -
 * except this copy is the source of truth, broadcasting every change to
 * all connected clients over /topic/flights. The Angular service now
 * subscribes to this instead of running its own timer, and only falls
 * back to its local simulation if this backend isn't reachable.
 */
@Service
public class FlightStatusEngine {

    private final Map<String, Flight> flights = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;

    public FlightStatusEngine(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        seed();
    }

    public List<Flight> snapshot() {
        return flights.values().stream()
                .sorted(Comparator.comparing(Flight::id))
                .collect(Collectors.toList());
    }

    @Scheduled(fixedRateString = "${ams.flight-engine.tick-interval-ms:3200}")
    public void tick() {
        List<Flight> movable = flights.values().stream()
                .filter(f -> !nextStates(f.status()).isEmpty())
                .collect(Collectors.toList());
        if (movable.isEmpty()) {
            return;
        }

        Flight target = movable.get(ThreadLocalRandom.current().nextInt(movable.size()));
        FlightStatus next = pickNextStatus(target.status());
        Integer delayMinutes = next == FlightStatus.DELAYED
                ? 10 + ThreadLocalRandom.current().nextInt(50)
                : target.delayMinutes();

        Flight updated = target.withStatus(next, delayMinutes);
        flights.put(updated.id(), updated);

        messagingTemplate.convertAndSend("/topic/flights", snapshot());
    }

    /** on-time flights mostly progress to boarding, with a real but low
     * chance of slipping to delayed instead. Every other status has
     * exactly one legal next state, so this only branches for on-time -
     * identical logic to the Angular fallback simulation. */
    private FlightStatus pickNextStatus(FlightStatus current) {
        if (current == FlightStatus.ON_TIME) {
            return ThreadLocalRandom.current().nextDouble() < 0.2
                    ? FlightStatus.DELAYED
                    : FlightStatus.BOARDING;
        }
        return nextStates(current).get(0);
    }

    private List<FlightStatus> nextStates(FlightStatus status) {
        return switch (status) {
            case ON_TIME -> List.of(FlightStatus.BOARDING, FlightStatus.DELAYED);
            case DELAYED -> List.of(FlightStatus.BOARDING);
            case BOARDING -> List.of(FlightStatus.ON_RUNWAY);
            case ON_RUNWAY -> List.of(FlightStatus.DEPARTED);
            case DEPARTED -> List.of();
        };
    }

    private void seed() {
        record Seed(String id, String time, String flightNumber, String destination, String gate) {}
        List<Seed> seeds = List.of(
                new Seed("f1", "06:40", "SpiceJet 757", "Hyderabad", "A14"),
                new Seed("f2", "07:10", "IndiGo 340", "Mumbai", "B02"),
                new Seed("f3", "08:15", "Akasa 123", "Bengaluru", "B03"),
                new Seed("f4", "09:05", "Indigo 667", "Hyderabad", "A09"),
                new Seed("f5", "09:40", "Air India 667", "Hyderabad", "C02"),
                new Seed("f6", "10:20", "Vistara 220", "Kolkata", "A02")
        );
        seeds.forEach(s -> flights.put(s.id(), new Flight(
                s.id(), s.time(), s.flightNumber(), s.destination(), s.gate(),
                FlightStatus.ON_TIME, null, Instant.now()
        )));
    }
}
