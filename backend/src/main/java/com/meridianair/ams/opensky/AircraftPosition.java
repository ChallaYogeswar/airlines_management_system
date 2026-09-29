package com.meridianair.ams.opensky;

public record AircraftPosition(
        String icao24,
        String callsign,
        String originCountry,
        Double longitude,
        Double latitude,
        Double baroAltitudeM,
        Boolean onGround,
        Double velocityMs,
        Double trueTrackDeg,
        Double verticalRateMs
) {}
