package com.dogsout.server.user;

import com.dogsout.server.auth.JwtUtil;
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
    private final JwtUtil jwtUtil;

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

    @PutMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(
            Authentication auth,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(auth.getName(), request);
        // Old tokens are now revoked — hand the caller a fresh one so they stay signed in
        return ResponseEntity.ok(Map.of("token", jwtUtil.generateToken(auth.getName())));
    }

    @PutMapping("/me/push-token")
    public ResponseEntity<Void> setPushToken(Authentication auth, @RequestBody Map<String, String> body) {
        userService.setPushToken(auth.getName(), body.get("token"));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/notifications")
    public ResponseEntity<Void> setNotificationsEnabled(Authentication auth, @RequestBody Map<String, Boolean> body) {
        userService.setNotificationsEnabled(auth.getName(), Boolean.TRUE.equals(body.get("enabled")));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(Authentication auth) {
        userService.deleteAccount(auth.getName());
        return ResponseEntity.noContent().build();
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

    @PutMapping("/me/photos/order")
    public ResponseEntity<Void> reorderPhotos(Authentication auth, @Valid @RequestBody ReorderPhotosRequest request) {
        userService.reorderPhotos(auth.getName(), request.photoIds());
        return ResponseEntity.noContent().build();
    }
}