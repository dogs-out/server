package com.dogsout.server.dog;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/dogs")
@RequiredArgsConstructor
public class DogController {

    private final DogService dogService;

    @PostMapping
    public ResponseEntity<DogResponse> createDog(Authentication auth, @RequestBody DogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dogService.createDog(auth.getName(), request));
    }

    @GetMapping("/me")
    public ResponseEntity<List<DogResponse>> getMyDogs(Authentication auth) {
        return ResponseEntity.ok(dogService.getMyDogs(auth.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DogResponse> getDog(@PathVariable Long id) {
        return ResponseEntity.ok(dogService.getDog(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DogResponse> updateDog(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody DogRequest request
    ) {
        return ResponseEntity.ok(dogService.updateDog(auth.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDog(Authentication auth, @PathVariable Long id) {
        dogService.deleteDog(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }
}