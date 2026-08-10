package com.dogsout.server.dog;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/dogs")
@RequiredArgsConstructor
public class DogController {

    private final DogService dogService;

    @PostMapping
    public ResponseEntity<DogResponse> createDog(Authentication auth, @Valid @RequestBody DogRequest request) {
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
            @Valid @RequestBody DogRequest request
    ) {
        return ResponseEntity.ok(dogService.updateDog(auth.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDog(Authentication auth, @PathVariable Long id) {
        dogService.deleteDog(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DogPhotoResponse> addPhoto(
            Authentication auth,
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dogService.addPhoto(auth.getName(), id, file));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(
            Authentication auth,
            @PathVariable Long id,
            @PathVariable Long photoId
    ) {
        dogService.deletePhoto(auth.getName(), id, photoId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/photos/order")
    public ResponseEntity<Void> reorderPhotos(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody ReorderPhotosRequest request
    ) {
        dogService.reorderPhotos(auth.getName(), id, request.photoIds());
        return ResponseEntity.noContent().build();
    }
}