package com.dogsout.server.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(Authentication auth) {
        return ResponseEntity.ok(userService.getMe(auth.getName()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            Authentication auth,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(userService.updateProfile(auth.getName(), request));
    }

    @PostMapping("/me/photos")
    public ResponseEntity<UserPhotoResponse> addPhoto(
            Authentication auth,
            @RequestBody Map<String, String> body
    ) {
        return ResponseEntity.ok(userService.addPhoto(auth.getName(), body.get("imageData")));
    }

    @DeleteMapping("/me/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(Authentication auth, @PathVariable Long photoId) {
        userService.deletePhoto(auth.getName(), photoId);
        return ResponseEntity.noContent().build();
    }
}