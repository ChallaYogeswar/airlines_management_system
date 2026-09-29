package com.meridianair.ams.web;

import com.meridianair.ams.opensky.AircraftPosition;
import com.meridianair.ams.opensky.RadarBroadcastService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RadarController {

    private final RadarBroadcastService radarBroadcastService;

    public RadarController(RadarBroadcastService radarBroadcastService) {
        this.radarBroadcastService = radarBroadcastService;
    }

    @GetMapping("/api/radar")
    public List<AircraftPosition> currentSnapshot() {
        return radarBroadcastService.snapshot();
    }
}
