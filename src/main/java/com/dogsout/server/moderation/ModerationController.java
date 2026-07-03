package com.dogsout.server.moderation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderationService;

    @PostMapping("/users/{userId}/block")
    public ResponseEntity<Void> blockUser(Authentication auth, @PathVariable Long userId) {
        moderationService.blockUser(auth.getName(), userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{userId}/block")
    public ResponseEntity<Void> unblockUser(Authentication auth, @PathVariable Long userId) {
        moderationService.unblockUser(auth.getName(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me/blocks")
    public ResponseEntity<List<BlockedUserResponse>> getBlockedUsers(Authentication auth) {
        return ResponseEntity.ok(moderationService.getBlockedUsers(auth.getName()));
    }

    @PostMapping("/matches/{matchId}/report")
    public ResponseEntity<Void> reportUser(Authentication auth, @PathVariable Long matchId,
                                           @Valid @RequestBody ReportRequest request) {
        moderationService.reportUser(auth.getName(), matchId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/matches/{matchId}")
    public ResponseEntity<Void> unmatch(Authentication auth, @PathVariable Long matchId) {
        moderationService.unmatch(auth.getName(), matchId);
        return ResponseEntity.noContent().build();
    }
}
