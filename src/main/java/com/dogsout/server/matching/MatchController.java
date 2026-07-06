package com.dogsout.server.matching;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @PostMapping("/swipe")
    public ResponseEntity<SwipeResponse> swipe(Authentication auth, @RequestBody SwipeRequest request) {
        return ResponseEntity.ok(matchService.swipe(auth.getName(), request));
    }

    @GetMapping
    public ResponseEntity<List<MatchResponse>> getMatches(Authentication auth) {
        return ResponseEntity.ok(matchService.getMatches(auth.getName()));
    }
}
