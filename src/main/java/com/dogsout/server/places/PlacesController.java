package com.dogsout.server.places;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
public class PlacesController {

    private final PlacesService placesService;

    /**
     * @param kind "park" (default, for playdates) or "area" — towns, postcodes and
     *             addresses, for setting your own location without GPS
     */
    @GetMapping("/search")
    public ResponseEntity<List<PlaceResult>> search(@RequestParam String query,
                                                    @RequestParam(required = false) Double lat,
                                                    @RequestParam(required = false) Double lng,
                                                    @RequestParam(defaultValue = "park") String kind) {
        return ResponseEntity.ok("area".equalsIgnoreCase(kind)
                ? placesService.searchAreas(query, lat, lng)
                : placesService.searchParks(query, lat, lng));
    }
}
