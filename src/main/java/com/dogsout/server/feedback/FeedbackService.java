package com.dogsout.server.feedback;

import com.dogsout.server.auth.EmailService;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.admin-email}")
    private String adminEmail;

    public void submitFeedback(String email, FeedbackRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String body = """
                New feedback submitted in Dogs Out.

                From: %s (%s)

                %s
                """.formatted(user.getName(), user.getEmail(), request.message());

        emailService.sendReportEmail(adminEmail, "Feedback from %s".formatted(user.getName()), body);
    }
}
