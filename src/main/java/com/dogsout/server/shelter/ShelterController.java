package com.dogsout.server.shelter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * The shelter directory — a hand-curated list, not a data feed.
 *
 * <p>Curation is the feature: this is the adopt-a-dog idea in the form that keeps
 * scams, backyard breeders and the stores' live-animal rules out of the app. The list
 * lives in {@code shelters.json} on the server rather than in the app so a correction
 * ships with a deploy instead of waiting on App Store and Play review.
 */
@RestController
@RequestMapping("/shelters")
public class ShelterController {

    private final List<Shelter> shelters;

    public ShelterController(ObjectMapper objectMapper) {
        this.shelters = load(objectMapper);
    }

    static List<Shelter> load(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("shelters.json").getInputStream()) {
            return List.copyOf(objectMapper.readValue(in, new TypeReference<List<Shelter>>() {}));
        } catch (IOException e) {
            throw new UncheckedIOException("shelters.json could not be read", e);
        }
    }

    @GetMapping
    public ResponseEntity<List<Shelter>> list() {
        return ResponseEntity.ok(shelters);
    }
}
