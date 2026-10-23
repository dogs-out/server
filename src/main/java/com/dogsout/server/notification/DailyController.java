package com.dogsout.server.notification;

import com.dogsout.server.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Today's news for the once-a-day card in the app. */
@RestController
@RequiredArgsConstructor
public class DailyController {

    private final DailyNudgeService dailyNudgeService;
    private final UserRepository userRepository;

    @GetMapping("/daily")
    public ResponseEntity<DailyDigest> today(Authentication auth) {
        var me = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(dailyNudgeService.digest(me));
    }
}
