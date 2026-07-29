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

    @GetMapping("/search")
    public ResponseEntity<List<PlaceResult>> search(@RequestParam String query,
                                                    @RequestParam(required = false) Double lat,
                                                    @RequestParam(required = false) Double lng) {
        return ResponseEntity.ok(placesService.searchParks(query, lat, lng));
    }
}
