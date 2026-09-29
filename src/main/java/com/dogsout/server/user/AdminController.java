package com.dogsout.server.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Admin-only operations; SecurityConfig restricts /admin/** to ROLE_ADMIN. */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    /** Removes an account on someone's behalf — acting on reports, or clearing out testers. */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, Authentication auth) {
        userService.deleteAccountById(id, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
