package com.dogsout.server.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@dogsout.app}")
    private String fromAddress;

    public void sendVerificationEmail(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Verify your Dogs Out account");
        message.setText("""
                Welcome to Dogs Out!

                Your verification code is: %s

                This code expires in 15 minutes.
                If you did not create an account, you can ignore this email.
                """.formatted(code));
        mailSender.send(message);
    }

    public void sendReportEmail(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Reset your Dogs Out password");
        message.setText("""
                You requested a password reset for your Dogs Out account.

                Your password reset token is:
                %s

                Enter this token in the Dogs Out app to set a new password.
                This token expires in 1 hour.

                If you did not request a password reset, you can safely ignore this email.
                """.formatted(token));
        mailSender.send(message);
    }
}