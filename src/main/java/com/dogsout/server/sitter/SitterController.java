package com.dogsout.server.sitter;

import com.dogsout.server.matching.DiscoverProfile;
import com.dogsout.server.matching.DiscoverService;
import jakarta.validation.Valid;
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
@RequestMapping("/sitters")
@RequiredArgsConstructor
public class SitterController {

    private final DiscoverService discoverService;
    private final SitterService sitterService;

    @GetMapping("/seekers")
    public ResponseEntity<List<DiscoverProfile>> getSeekers(Authentication auth) {
        return ResponseEntity.ok(discoverService.getSeekerPool(auth.getName()));
    }

    @PostMapping("/contact")
    public ResponseEntity<ContactSitterResponse> contact(Authentication auth,
                                                         @Valid @RequestBody ContactSitterRequest request) {
        return ResponseEntity.ok(sitterService.contact(auth.getName(), request.targetUserId()));
    }
}
