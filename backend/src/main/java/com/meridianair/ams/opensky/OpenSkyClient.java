package com.meridianair.ams.opensky;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin client for OpenSky Network's free, keyless /states/all endpoint -
 * genuinely free, no registration required for anonymous access, subject
 * to a modest daily rate limit.
 *
 * The response is deliberately awkward to consume: each aircraft "state
 * vector" is a plain JSON array with fixed, positional, mixed-type fields
 * rather than a named object -
 * see https://openskynetwork.github.io/opensky-api/rest.html#response
 * That's why this parses a raw JsonNode by index instead of letting
 * Jackson bind straight to a record - there's no way to map a
 * heterogeneous positional array onto named fields declaratively.
 */
@Component
public class OpenSkyClient {

    private static final Logger log = LoggerFactory.getLogger(OpenSkyClient.class);

    private final RestClient restClient;
    private final OpenSkyProperties properties;

    public OpenSkyClient(RestClient.Builder restClientBuilder, OpenSkyProperties properties) {
        this.properties = properties;
        // Using the Boot-autoconfigured RestClient.Builder (rather than
        // RestClient.builder() directly) so this inherits the same Jackson
        // message converters the rest of the app uses.
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    public List<AircraftPosition> fetchStates() {
        OpenSkyProperties.BoundingBox box = properties.boundingBox();
        try {
            JsonNode body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/states/all")
                            .queryParam("lamin", box.lamin())
                            .queryParam("lomin", box.lomin())
                            .queryParam("lamax", box.lamax())
                            .queryParam("lomax", box.lomax())
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            return parseStates(body);
        } catch (Exception ex) {
            // Anonymous OpenSky access is rate-limited and occasionally
            // returns 429/503 - never let a bad poll take the scheduler
            // down; the caller keeps serving its last good snapshot.
            log.warn("OpenSky fetch failed, keeping last known snapshot: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<AircraftPosition> parseStates(JsonNode body) {
        List<AircraftPosition> result = new ArrayList<>();
        if (body == null || !body.has("states") || body.get("states").isNull()) {
            return result;
        }
        for (JsonNode state : body.get("states")) {
            result.add(new AircraftPosition(
                    text(state, 0),    // icao24
                    text(state, 1),    // callsign
                    text(state, 2),    // origin_country
                    number(state, 5),  // longitude
                    number(state, 6),  // latitude
                    number(state, 7),  // baro_altitude (m)
                    bool(state, 8),    // on_ground
                    number(state, 9),  // velocity (m/s)
                    number(state, 10), // true_track (deg)
                    number(state, 11)  // vertical_rate (m/s)
            ));
        }
        return result;
    }

    private String text(JsonNode arr, int idx) {
        JsonNode n = arr.get(idx);
        if (n == null || n.isNull()) return null;
        String v = n.asText().strip();
        return v.isEmpty() ? null : v;
    }

    private Double number(JsonNode arr, int idx) {
        JsonNode n = arr.get(idx);
        return (n == null || n.isNull()) ? null : n.asDouble();
    }

    private Boolean bool(JsonNode arr, int idx) {
        JsonNode n = arr.get(idx);
        return (n == null || n.isNull()) ? null : n.asBoolean();
    }
}
