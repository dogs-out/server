package com.dogsout.server.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
            @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(userService.updateProfile(auth.getName(), request));
    }
}