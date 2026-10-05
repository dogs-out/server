package com.dogsout.server.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CPU, memory and network of the server and the database, from Railway's own
 * metrics API — the numbers on the Railway dashboard, without opening it.
 *
 * <p>Needs an account token in RAILWAY_API_TOKEN (Railway → Account Settings →
 * Tokens). The project, environment and service ids are the ones Railway puts
 * into every deployment's environment. Without a token this reports itself as
 * not configured rather than failing the admin page.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RailwayMetrics {

    private static final String API = "https://backboard.railway.com/graphql/v2";
    private static final String QUERY = """
            query m($p:String!,$s:String!,$e:String!,$d:DateTime!,$r:Int!){
              metrics(projectId:$p,serviceId:$s,environmentId:$e,startDate:$d,sampleRateSeconds:$r,
                measurements:[CPU_USAGE,MEMORY_USAGE_GB,NETWORK_RX_GB,NETWORK_TX_GB]){
                measurement values{ts value}
              }
            }""";

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${RAILWAY_API_TOKEN:}") private String token;
    @Value("${RAILWAY_PROJECT_ID:}") private String projectId;
    @Value("${RAILWAY_ENVIRONMENT_ID:}") private String environmentId;
    @Value("${RAILWAY_SERVICE_ID:}") private String serviceId;
    /** The Postgres service, which Railway does not tell the app about. */
    @Value("${RAILWAY_DB_SERVICE_ID:c3736ef6-1de6-4d30-a3e5-c0f5fb42a0fe}") private String dbServiceId;

    public boolean configured() {
        return !token.isBlank() && !projectId.isBlank() && !environmentId.isBlank() && !serviceId.isBlank();
    }

    /** Last {@code hours} hours for the server and the database, or why not. */
    public Map<String, Object> fetch(int hours) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!configured()) {
            out.put("configured", false);
            return out;
        }
        out.put("configured", true);
        out.put("server", service(serviceId, hours));
        out.put("database", service(dbServiceId, hours));
        return out;
    }

    private Map<String, Object> service(String id, int hours) {
        try {
            Map<String, Object> vars = Map.of(
                    "p", projectId, "s", id, "e", environmentId,
                    "d", Instant.now().minus(Duration.ofHours(hours)).toString(),
                    "r", hours <= 6 ? 300 : 1800);
            String body = objectMapper.writeValueAsString(Map.of("query", QUERY, "variables", vars));
            HttpRequest req = HttpRequest.newBuilder(URI.create(API))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            JsonNode json = objectMapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
            if (json.has("errors")) return Map.of("error", json.get("errors").get(0).path("message").asText());
            Map<String, Object> series = new LinkedHashMap<>();
            for (JsonNode m : json.path("data").path("metrics")) {
                List<double[]> points = new java.util.ArrayList<>();
                for (JsonNode v : m.path("values")) points.add(new double[]{v.path("ts").asDouble(), v.path("value").asDouble()});
                series.put(m.path("measurement").asText(), points);
            }
            return series;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Railway metrics unavailable: {}", e.getMessage());
            return Map.of("error", "Railway metrics unavailable");
        }
    }
}
