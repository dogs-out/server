package com.dogsout.server.sitter;

import com.dogsout.server.matching.DiscoverProfile;
import com.dogsout.server.matching.DiscoverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sitters")
@RequiredArgsConstructor
public class SitterController {

    private final DiscoverService discoverService;
    private final SitterService sitterService;
    private final SitterReviewService sitterReviewService;

    @GetMapping("/seekers")
    public ResponseEntity<List<DiscoverProfile>> getSeekers(Authentication auth) {
        return ResponseEntity.ok(discoverService.getSeekerPool(auth.getName()));
    }

    /**
     * @param weekday optional, repeated or comma-separated, e.g. "Monday,Friday" —
     *                sitters free on any one of them. Spring binds both shapes to
     *                the list, so the client may send whichever is convenient.
     */
    @GetMapping("/available")
    public ResponseEntity<List<DiscoverProfile>> getAvailableSitters(
            Authentication auth, @RequestParam(required = false) List<String> weekday) {
        return ResponseEntity.ok(discoverService.getSitterPool(auth.getName(), weekday));
    }

    /** Open jobs any sitter can still take, soonest first. */
    @GetMapping("/requests")
    public ResponseEntity<List<SittingRequestResponse>> openRequests(Authentication auth) {
        return ResponseEntity.ok(sitterService.openRequests(auth.getName()));
    }

    /** What this account has posted, open or closed. */
    @GetMapping("/requests/mine")
    public ResponseEntity<List<SittingRequestResponse>> myRequests(Authentication auth) {
        return ResponseEntity.ok(sitterService.myRequests(auth.getName()));
    }

    @PostMapping("/requests")
    public ResponseEntity<SittingRequestResponse> createRequest(
            Authentication auth, @Valid @RequestBody CreateSittingRequest request) {
        return ResponseEntity.ok(sitterService.createRequest(auth.getName(), request));
    }

    /** Changes a request that nobody has taken yet; only the owner may. */
    @PutMapping("/requests/{id}")
    public ResponseEntity<SittingRequestResponse> updateRequest(
            Authentication auth, @PathVariable Long id, @Valid @RequestBody CreateSittingRequest request) {
        return ResponseEntity.ok(sitterService.updateRequest(auth.getName(), id, request));
    }

    /** A sitter offering to take a job; opens the chat and posts the offer into it. */
    @PostMapping("/requests/{id}/offer")
    public ResponseEntity<ContactSitterResponse> offer(
            Authentication auth, @PathVariable Long id, @RequestBody(required = false) OfferRequest body) {
        return ResponseEntity.ok(sitterService.offer(auth.getName(), id, body == null ? null : body.message()));
    }

    /** The owner accepting one of the offers; takes the job off everyone's board. */
    @PutMapping("/requests/{id}/accept")
    public ResponseEntity<SittingRequestResponse> accept(
            Authentication auth, @PathVariable Long id, @Valid @RequestBody AcceptSitterRequest body) {
        return ResponseEntity.ok(sitterService.accept(auth.getName(), id, body.sitterId()));
    }

    /** The sitter pulling out; the job goes back on the board. */
    @PutMapping("/requests/{id}/cancel")
    public ResponseEntity<SittingRequestResponse> cancelAsSitter(
            Authentication auth, @PathVariable Long id,
            @RequestBody(required = false) CancelSittingRequest body) {
        return ResponseEntity.ok(
                sitterService.cancelAsSitter(auth.getName(), id, body == null ? null : body.reason()));
    }

    /** The owner handing over the to-do list, emergency number and address. */
    @PutMapping("/requests/{id}/details")
    public ResponseEntity<SittingRequestResponse> shareDetails(
            Authentication auth, @PathVariable Long id,
            @Valid @RequestBody SittingDetailsRequest details) {
        return ResponseEntity.ok(sitterService.shareDetails(auth.getName(), id, details));
    }

    /** This account's cancellation record, so the app can warn before a penalty. */
    @GetMapping("/standing")
    public ResponseEntity<SitterStandingResponse> standing(Authentication auth) {
        return ResponseEntity.ok(sitterService.standing(auth.getName()));
    }

    /** Jobs this account was accepted for, as the sitter. */
    @GetMapping("/requests/accepted")
    public ResponseEntity<List<SittingRequestResponse>> acceptedJobs(Authentication auth) {
        return ResponseEntity.ok(sitterService.myJobsAsSitter(auth.getName()));
    }

    // ─── Reviews ──────────────────────────────────────────────────────────────

    /** Sittings this owner still owes a rating for, so the app can prompt on open. */
    @GetMapping("/reviews/pending")
    public ResponseEntity<List<SittingRequestResponse>> pendingReviews(Authentication auth) {
        return ResponseEntity.ok(sitterReviewService.pending(auth.getName()));
    }

    /** The highlight tags an owner may pick, so the client never invents one. */
    @GetMapping("/reviews/tags")
    public ResponseEntity<List<String>> reviewTags() {
        return ResponseEntity.ok(sitterReviewService.allowedTags());
    }

    @PostMapping("/reviews")
    public ResponseEntity<SitterReviewResponse> submitReview(
            Authentication auth, @Valid @RequestBody CreateSitterReview review) {
        return ResponseEntity.ok(sitterReviewService.submit(auth.getName(), review));
    }

    @GetMapping("/{sitterId}/reviews")
    public ResponseEntity<List<SitterReviewResponse>> reviewsFor(
            Authentication auth, @PathVariable Long sitterId) {
        return ResponseEntity.ok(sitterReviewService.forSitter(auth.getName(), sitterId));
    }

    @GetMapping("/{sitterId}/rating")
    public ResponseEntity<SitterRatingSummary> ratingFor(@PathVariable Long sitterId) {
        return ResponseEntity.ok(sitterReviewService.summaryFor(sitterId));
    }

    /** Closes a request once someone has been found; only the owner may. */
    @PutMapping("/requests/{id}/close")
    public ResponseEntity<SittingRequestResponse> closeRequest(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(sitterService.closeRequest(auth.getName(), id));
    }

    @PostMapping("/contact")
    public ResponseEntity<ContactSitterResponse> contact(Authentication auth,
                                                         @Valid @RequestBody ContactSitterRequest request) {
        return ResponseEntity.ok(sitterService.contact(auth.getName(), request.targetUserId()));
    }
}
