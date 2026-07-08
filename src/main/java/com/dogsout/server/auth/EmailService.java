package com.dogsout.server.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Sends mail via Resend's HTTPS API rather than SMTP, since most PaaS hosts (Railway included) block outbound SMTP ports. */
@Service
public class EmailService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${resend.api-key:}")
    private String apiKey;

    @Value("${resend.from-email:onboarding@resend.dev}")
    private String fromAddress;

    public void sendVerificationEmail(String toEmail, String code) {
        send(toEmail, "Verify your Dogs Out account", """
                Welcome to Dogs Out!

                Your verification code is: %s

                This code expires in 15 minutes.
                If you did not create an account, you can ignore this email.
                """.formatted(code));
    }

    public void sendReportEmail(String toEmail, String subject, String body) {
        send(toEmail, subject, body);
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        send(toEmail, "Reset your Dogs Out password", """
                You requested a password reset for your Dogs Out account.

                Your password reset token is:
                %s

                Enter this token in the Dogs Out app to set a new password.
                This token expires in 1 hour.

                If you did not request a password reset, you can safely ignore this email.
                """.formatted(token));
    }

    private void send(String toEmail, String subject, String text) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "from", fromAddress,
                    "to", List.of(toEmail),
                    "subject", subject,
                    "text", text
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Resend API error " + response.statusCode() + ": " + response.body());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to send email via Resend", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to send email via Resend", e);
        }
    }
}