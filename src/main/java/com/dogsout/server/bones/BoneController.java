package com.dogsout.server.bones;

import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/bones")
@RequiredArgsConstructor
public class BoneController {

    private final BoneService boneService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<BoneStatus> status(Authentication auth) {
        return ResponseEntity.ok(boneService.status(me(auth)));
    }

    private User me(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
