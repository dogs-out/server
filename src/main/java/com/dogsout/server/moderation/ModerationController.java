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

    /**
     * Reports a profile without a match, from Discover or a profile screen.
     *
     * <p>The match-scoped report below needs both people to have swiped right,
     * which is the wrong shape for an offensive name, bio or photo: nobody
     * matches with those, so nobody could report them.
     */
    @PostMapping("/users/{userId}/report")
    public ResponseEntity<Void> reportProfile(Authentication auth, @PathVariable Long userId,
                                              @Valid @RequestBody ReportRequest request) {
        moderationService.reportProfile(auth.getName(), userId, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reports a sitter review's comment — hate speech and the like.
     *
     * <p>Hidden immediately rather than after a human reads the mail: the comment
     * sits on someone else's profile and they have no way to answer it.
     */
    @PostMapping("/reviews/{reviewId}/report")
    public ResponseEntity<Void> reportReview(Authentication auth, @PathVariable Long reviewId,
                                             @Valid @RequestBody ReportRequest request) {
        moderationService.reportReview(auth.getName(), reviewId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/matches/{matchId}")
    public ResponseEntity<Void> unmatch(Authentication auth, @PathVariable Long matchId) {
        moderationService.unmatch(auth.getName(), matchId);
        return ResponseEntity.noContent().build();
    }
}
