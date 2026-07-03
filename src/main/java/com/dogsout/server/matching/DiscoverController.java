package com.dogsout.server.matching;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/discover")
@RequiredArgsConstructor
public class DiscoverController {

    private final DiscoverService discoverService;

    @GetMapping
    public ResponseEntity<List<DiscoverProfile>> getDiscoverFeed(Authentication auth) {
        return ResponseEntity.ok(discoverService.getDiscoverFeed(auth.getName()));
    }

    @GetMapping("/profile/{userId}")
    public ResponseEntity<DiscoverProfile> getProfile(Authentication auth, @PathVariable Long userId) {
        return ResponseEntity.ok(discoverService.getProfile(auth.getName(), userId));
    }
}
