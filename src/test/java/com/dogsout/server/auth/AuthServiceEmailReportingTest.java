package com.dogsout.server.auth;

import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Registration answers "check your email" whether or not the mail provider took
 * the message. These pin the part that lets the app tell the difference — a
 * recipient we cannot reach used to be indistinguishable from a slow inbox.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceEmailReportingTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private EmailService emailService;
    @Mock private ObjectMapper objectMapper;
    @Mock private RateLimiter rateLimiter;

    @InjectMocks private AuthService authService;

    private static final RegisterRequest REQUEST =
            new RegisterRequest("someone@example.com", "Test Person", "DogsOut123!");

    private void givenANewAccountCanBeCreated() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void reportsThatTheVerificationEmailWentOut() {
        givenANewAccountCanBeCreated();

        MessageResponse response = authService.register(REQUEST);

        assertThat(response.emailSent()).isTrue();
    }

    @Test
    void reportsAFailedSendWithoutFailingTheRegistration() {
        givenANewAccountCanBeCreated();
        doThrow(new IllegalStateException("Resend API error 403"))
                .when(emailService).sendVerificationEmail(anyString(), anyString());

        // The account still exists — the send is best effort, because the code is a
        // live credential that never travels in the response body.
        MessageResponse response = authService.register(REQUEST);

        assertThat(response.emailSent()).isFalse();
        assertThat(response.message()).contains("Registration successful");
    }

    @Test
    void leavesTheFlagOutOfResponsesThatSendNoMail() {
        // Serialized with @JsonInclude(NON_NULL), so null keeps `emailSent` out of
        // the JSON for every endpoint that has no email to report on.
        assertThat(new MessageResponse("Password reset successfully.").emailSent()).isNull();
    }
}
