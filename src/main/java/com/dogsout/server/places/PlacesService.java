package com.dogsout.server.places;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side proxy for Google Places API (New) text search. The API key stays
 * on the server — it must never ship inside the app bundle.
 */
@Service
public class PlacesService {

    private static final String PLACES_URL = "https://places.googleapis.com/v1/places:searchText";

    private final RestClient restClient = RestClient.create();

    @Value("${google.places.api-key:}")
    private String apiKey;

    public List<PlaceResult> searchParks(String query, Double lat, Double lng) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Park search is not configured on this server");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("textQuery", query);
        body.put("includedType", "park");
        body.put("maxResultCount", 10);
        if (lat != null && lng != null) {
            body.put("locationBias", Map.of("circle", Map.of(
                    "center", Map.of("latitude", lat, "longitude", lng),
                    "radius", 30000.0)));
        }

        try {
            JsonNode response = restClient.post()
                    .uri(PLACES_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", "places.displayName,places.formattedAddress,places.location")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            List<PlaceResult> results = new ArrayList<>();
            if (response != null && response.has("places")) {
                for (JsonNode place : response.get("places")) {
                    JsonNode location = place.get("location");
                    if (location == null) continue;
                    results.add(new PlaceResult(
                            place.path("displayName").path("text").asText(null),
                            place.path("formattedAddress").asText(null),
                            location.path("latitude").asDouble(),
                            location.path("longitude").asDouble()
                    ));
                }
            }
            return results;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Park search failed");
        }
    }
}
