package com.dogsout.server.user;

import com.dogsout.server.auth.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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

    @PutMapping("/me/status")
    public ResponseEntity<UserResponse> updateStatus(
            Authentication auth, @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(userService.updateStatus(auth.getName(), request));
    }

    /**
     * Every match and their current status.
     *
     * <p>The path still says "walking" because builds already in testers' hands
     * call it; the list stopped being only walkers, the URL has not.
     */
    @GetMapping("/walking")
    public ResponseEntity<List<FriendStatus>> friendStatuses(Authentication auth) {
        return ResponseEntity.ok(userService.friendStatuses(auth.getName()));
    }

    /** The dogs this account may say it is looking after: those of its matches. */
    @GetMapping("/me/sittable-dogs")
    public ResponseEntity<List<SittableDog>> sittableDogs(Authentication auth) {
        return ResponseEntity.ok(userService.sittableDogs(auth.getName()));
    }

    /** Empty or absent userIds means every match; otherwise only those, and only if matched. */
    @PostMapping("/me/status/invite")
    public ResponseEntity<Void> inviteMatchesToWalk(
            Authentication auth, @RequestBody(required = false) InviteToWalkRequest request) {
        userService.inviteMatchesToWalk(auth.getName(), request == null ? null : request.userIds());
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/me/status/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> setStatusPhoto(
            Authentication auth, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.setStatusPhoto(auth.getName(), file));
    }

    @DeleteMapping("/me/status/photo")
    public ResponseEntity<UserResponse> removeStatusPhoto(Authentication auth) {
        return ResponseEntity.ok(userService.removeStatusPhoto(auth.getName()));
    }

    @PostMapping("/me/terms")
    public ResponseEntity<Void> acceptTerms(Authentication auth) {
        userService.acceptTerms(auth.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(Authentication auth) {
        userService.deleteAccount(auth.getName());
        return ResponseEntity.noContent().build();
    }

    /**
     * Multipart rather than base64 JSON: base64 inflates the body by a third, and the
     * old shape meant the whole image had to be buffered as a String before anything
     * could look at it.
     */
    @PostMapping(value = "/me/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserPhotoResponse> addPhoto(
            Authentication auth,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.addPhoto(auth.getName(), file));
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