package com.meridianair.ams.web;

import com.meridianair.ams.domain.Flight;
import com.meridianair.ams.engine.FlightStatusEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gives the frontend an initial snapshot to render immediately on load,
 * before its STOMP subscription to /topic/flights is even established -
 * avoids a flash of an empty board while the WebSocket handshake completes.
 */
@RestController
public class FlightController {

    private final FlightStatusEngine engine;

    public FlightController(FlightStatusEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/api/flights")
    public List<Flight> currentBoard() {
        return engine.snapshot();
    }
}
