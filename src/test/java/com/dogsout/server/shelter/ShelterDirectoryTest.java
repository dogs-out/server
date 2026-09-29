package com.dogsout.server.shelter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The directory is hand-edited JSON, so the checks a reviewer would do by eye are done here. */
class ShelterDirectoryTest {

    private final List<Shelter> shelters = ShelterController.load(new ObjectMapper());

    @Test
    void coversEveryCantonAndBundesland() {
        Map<String, Long> perCountry = shelters.stream()
                .collect(Collectors.groupingBy(Shelter::country, Collectors.counting()));
        assertThat(perCountry).containsEntry("CH", 26L).containsEntry("AT", 9L).containsEntry("DE", 16L);
        assertThat(shelters.stream().map(s -> s.country() + "/" + s.region()).distinct()).hasSize(shelters.size());
    }

    @Test
    void everyEntryIsComplete() {
        assertThat(shelters).allSatisfy(s -> {
            assertThat(s.id()).isNotBlank();
            assertThat(s.name()).isNotBlank();
            assertThat(s.town()).isNotBlank();
            assertThat(s.website()).startsWith("https://");
            // Inside the DACH box, so a swapped latitude/longitude cannot slip through
            assertThat(s.latitude()).isBetween(45.5, 55.5);
            assertThat(s.longitude()).isBetween(5.5, 17.5);
        });
        assertThat(shelters.stream().map(Shelter::id).distinct()).hasSize(shelters.size());
    }
}
