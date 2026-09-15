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

    @GetMapping("/seekers")
    public ResponseEntity<List<DiscoverProfile>> getSeekers(Authentication auth) {
        return ResponseEntity.ok(discoverService.getSeekerPool(auth.getName()));
    }

    /** @param weekday optional, e.g. "Monday" — only sitters who said they are free then. */
    @GetMapping("/available")
    public ResponseEntity<List<DiscoverProfile>> getAvailableSitters(
            Authentication auth, @RequestParam(required = false) String weekday) {
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
