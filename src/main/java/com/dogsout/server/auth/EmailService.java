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
import java.util.LinkedHashMap;
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
        String text = """
                Welcome to Dogs Out!

                Your verification code is: %s

                This code expires in 15 minutes.
                If you did not create an account, you can ignore this email.
                """.formatted(code);
        String html = wrapHtml("Verify your account", """
                <p style="margin:0 0 20px;font-size:15px;color:#0D2818;line-height:1.6;">Welcome to Dogs Out! Use the code below to verify your account:</p>
                <div style="background-color:#EEFBF3;border:1px solid rgba(46,158,107,0.22);border-radius:12px;padding:22px;text-align:center;margin-bottom:20px;">
                  <span style="font-size:32px;font-weight:800;letter-spacing:8px;color:#2E9E6B;font-family:'SF Mono',Menlo,monospace;">%s</span>
                </div>
                <p style="margin:0;font-size:13px;color:#4A7760;">This code expires in 15 minutes. If you did not create an account, you can safely ignore this email.</p>
                """.formatted(code));
        send(toEmail, "Verify your Dogs Out account", text, html);
    }

    public void sendReportEmail(String toEmail, String subject, String body) {
        send(toEmail, subject, body, null);
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String text = """
                You requested a password reset for your Dogs Out account.

                Your password reset token is:
                %s

                Enter this token in the Dogs Out app to set a new password.
                This token expires in 1 hour.

                If you did not request a password reset, you can safely ignore this email.
                """.formatted(token);
        String html = wrapHtml("Reset your password", """
                <p style="margin:0 0 20px;font-size:15px;color:#0D2818;line-height:1.6;">You requested a password reset. Enter this token in the Dogs Out app to set a new password:</p>
                <div style="background-color:#EEFBF3;border:1px solid rgba(46,158,107,0.22);border-radius:12px;padding:20px;text-align:center;margin-bottom:20px;word-break:break-all;">
                  <span style="font-size:16px;font-weight:700;letter-spacing:1px;color:#2E9E6B;font-family:'SF Mono',Menlo,monospace;">%s</span>
                </div>
                <p style="margin:0;font-size:13px;color:#4A7760;">This token expires in 1 hour. If you did not request a password reset, you can safely ignore this email.</p>
                """.formatted(token));
        send(toEmail, "Reset your Dogs Out password", text, html);
    }

    /** One consistent branded shell (inline styles only — email clients ignore stylesheets). */
    private String wrapHtml(String heading, String bodyHtml) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:32px 16px;background-color:#EEFBF3;font-family:-apple-system,Helvetica,Arial,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    <tr><td align="center">
                      <table role="presentation" width="480" cellpadding="0" cellspacing="0" style="max-width:480px;width:100%%;background-color:#ffffff;border-radius:20px;overflow:hidden;border:1px solid rgba(46,158,107,0.22);">
                        <tr><td style="background-color:#2E9E6B;padding:26px 32px;text-align:center;">
                          <span style="font-size:22px;font-weight:800;color:#ffffff;letter-spacing:-0.3px;">🐾 Dogs Out</span>
                        </td></tr>
                        <tr><td style="padding:32px;">
                          <h1 style="margin:0 0 16px;font-size:19px;color:#0D2818;">%s</h1>
                          %s
                        </td></tr>
                        <tr><td style="padding:0 32px 26px;">
                          <p style="margin:0;font-size:12px;color:#4A7760;line-height:1.5;">This is an automated message from Dogs Out.</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(heading, bodyHtml);
    }

    private void send(String toEmail, String subject, String text, String html) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("from", fromAddress);
            body.put("to", List.of(toEmail));
            body.put("subject", subject);
            body.put("text", text);
            if (html != null) {
                body.put("html", html);
            }
            String requestBody = objectMapper.writeValueAsString(body);
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