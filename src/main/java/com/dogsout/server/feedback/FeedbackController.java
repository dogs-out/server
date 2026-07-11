package com.dogsout.server.feedback;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/feedback")
    public ResponseEntity<Void> submitFeedback(Authentication auth, @Valid @RequestBody FeedbackRequest request) {
        feedbackService.submitFeedback(auth.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
