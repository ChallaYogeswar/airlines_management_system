package com.meridianair.ams.opensky;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Polls OpenSky on a fixed interval and pushes the result to
 * /topic/radar. Keeps the last successful snapshot in memory so
 * GET /api/radar and any late-connecting client always get something,
 * even mid-rate-limit.
 */
@Service
public class RadarBroadcastService {

    private final OpenSkyClient openSkyClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicReference<List<AircraftPosition>> lastSnapshot =
            new AtomicReference<>(List.of());

    public RadarBroadcastService(OpenSkyClient openSkyClient, SimpMessagingTemplate messagingTemplate) {
        this.openSkyClient = openSkyClient;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedRateString = "${ams.opensky.poll-interval-ms:20000}")
    public void poll() {
        List<AircraftPosition> states = openSkyClient.fetchStates();
        if (!states.isEmpty()) {
            lastSnapshot.set(states);
            messagingTemplate.convertAndSend("/topic/radar", states);
        }
        // an empty result usually means a rate-limited or failed call -
        // deliberately not clearing lastSnapshot, so the radar doesn't
        // go blank because of one bad poll
    }

    public List<AircraftPosition> snapshot() {
        return lastSnapshot.get();
    }
}
