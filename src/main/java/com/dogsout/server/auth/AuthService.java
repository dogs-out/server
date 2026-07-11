package com.dogsout.server.auth;

import com.dogsout.server.user.AuthProvider;
import com.dogsout.server.user.Role;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.RSAPublicKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${google.client-secret}")
    private String googleClientSecret;

    @Value("${apple.client-id}")
    private String appleClientId;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword() != null ? user.getPassword() : "")
                .roles(user.getRole() != null ? user.getRole().name() : Role.USER.name())
                .build();
    }

    public MessageResponse register(RegisterRequest request) {
        rateLimiter.check(request.email());
        rateLimiter.recordFailure(request.email());
        Optional<User> existing = userRepository.findByEmail(request.email());
        if (existing.isPresent()) {
            User user = existing.get();
            if (Boolean.TRUE.equals(user.getEmailVerified())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
            }
            // Unverified account — resend a fresh code
            assignVerificationCode(user);
            userRepository.save(user);
            sendVerificationEmailSafely(user.getEmail(), user.getVerificationCode());
            return new MessageResponse("A new verification code has been sent to your email.");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setName(request.name());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setIsActive(true);
        user.setEmailVerified(false);
        assignVerificationCode(user);
        userRepository.save(user);

        sendVerificationEmailSafely(user.getEmail(), user.getVerificationCode());
        return new MessageResponse("Registration successful. Please check your email to verify your account.");
    }

    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!request.code().equals(user.getVerificationCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification code");
        }
        if (user.getVerificationCodeExpiry() == null || LocalDateTime.now().isAfter(user.getVerificationCodeExpiry())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification code has expired");
        }
        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiry(null);
        userRepository.save(user);
        return new AuthResponse(jwtUtil.generateToken(user.getEmail()), user.getEmail(), user.getName(), true);
    }

    public AuthResponse login(LoginRequest request) {
        rateLimiter.check(request.email());
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    rateLimiter.recordFailure(request.email());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "No account found with that email address");
                });
        if (user.getPassword() == null) {
            String provider = user.getAuthProvider() == AuthProvider.APPLE ? "Apple" : "Google";
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This account doesn't have a password set. Please sign in with " + provider + ", or use \"Forgot password\" to add one.");
        }
        if (Boolean.FALSE.equals(user.getEmailVerified())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Please verify your email address before logging in");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            rateLimiter.recordFailure(request.email());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        rateLimiter.reset(request.email());
        return new AuthResponse(jwtUtil.generateToken(user.getEmail()), user.getEmail(), user.getName(), false);
    }

    public AuthResponse googleAuth(GoogleAuthRequest request) {
        String email = exchangeGoogleCodeForEmail(request.code(), request.codeVerifier(), request.redirectUri(), request.clientId());
        boolean[] isNew = {false};
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            isNew[0] = true;
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setName(email.split("@")[0]);
            newUser.setRole(Role.USER);
            newUser.setAuthProvider(AuthProvider.GOOGLE);
            newUser.setIsActive(true);
            newUser.setEmailVerified(true);
            return userRepository.save(newUser);
        });
        if (!isNew[0]) {
            // Google has verified this email is theirs — mark it verified, but don't touch
            // authProvider: it's not exclusive, a password (if any) must keep working too.
            user.setEmailVerified(true);
            userRepository.save(user);
        }
        return new AuthResponse(jwtUtil.generateToken(user.getEmail()), user.getEmail(), user.getName(), isNew[0]);
    }

    public AuthResponse appleAuth(AppleAuthRequest request) {
        AppleTokenClaims claims = verifyAppleToken(request.identityToken());

        // Look up by stable Apple user ID first, then fall back to email
        Optional<User> byAppleId = userRepository.findByAppleUserId(claims.sub());
        if (byAppleId.isPresent()) {
            User user = byAppleId.get();
            return new AuthResponse(jwtUtil.generateToken(user.getEmail()), user.getEmail(), user.getName(), false);
        }

        if (claims.email() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Email not available from Apple. Please sign in again and grant email access.");
        }

        Optional<User> byEmail = userRepository.findByEmail(claims.email());
        if (byEmail.isPresent()) {
            // Existing user — link Apple ID for future logins. Apple has verified this email
            // is theirs, but don't touch authProvider: a password (if any) must keep working too.
            User user = byEmail.get();
            user.setAppleUserId(claims.sub());
            user.setEmailVerified(true);
            userRepository.save(user);
            return new AuthResponse(jwtUtil.generateToken(user.getEmail()), user.getEmail(), user.getName(), false);
        }

        // New user
        User newUser = new User();
        newUser.setEmail(claims.email());
        newUser.setName(claims.email().split("@")[0]);
        newUser.setRole(Role.USER);
        newUser.setAuthProvider(AuthProvider.APPLE);
        newUser.setIsActive(true);
        newUser.setEmailVerified(true);
        newUser.setAppleUserId(claims.sub());
        userRepository.save(newUser);
        return new AuthResponse(jwtUtil.generateToken(newUser.getEmail()), newUser.getEmail(), newUser.getName(), true);
    }

    public MessageResponse forgotPassword(PasswordResetRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account found with that email address"));
        // Allowed even for Google/Apple-only accounts — this doubles as "add a password"
        // so any linked sign-in method keeps working going forward.
        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), token);
        } catch (Exception e) {
            System.err.printf("[DEV] Password reset token for %s: %s%n", user.getEmail(), token);
        }
        return new MessageResponse("A password reset code has been sent to your email.");
    }

    public MessageResponse resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByResetToken(request.token())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token"));
        if (user.getResetTokenExpiry() == null || LocalDateTime.now().isAfter(user.getResetTokenExpiry())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(java.time.Instant.now());
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        return new MessageResponse("Password reset successfully. Please log in with your new password.");
    }

    private void sendVerificationEmailSafely(String email, String code) {
        try {
            emailService.sendVerificationEmail(email, code);
        } catch (Exception e) {
            System.err.printf("[DEV] Verification code for %s: %s%n", email, code);
        }
    }

    private void assignVerificationCode(User user) {
        user.setVerificationCode(String.format("%06d", SECURE_RANDOM.nextInt(1_000_000)));
        user.setVerificationCodeExpiry(LocalDateTime.now().plusMinutes(15));
    }

    @SuppressWarnings("unchecked")
    private String exchangeGoogleCodeForEmail(String code, String codeVerifier, String redirectUri, String clientId) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientId);
        // iOS/Android native clients are public clients — PKCE replaces the secret.
        // Web clients are confidential and require the client secret.
        boolean isNativeClient = redirectUri.startsWith("com.googleusercontent.apps");
        if (!isNativeClient) {
            params.add("client_secret", googleClientSecret);
        }
        params.add("redirect_uri", redirectUri);
        params.add("code_verifier", codeVerifier);
        params.add("grant_type", "authorization_code");
        try {
            Map<String, Object> tokenResponse = restTemplate.postForObject(
                    "https://oauth2.googleapis.com/token",
                    new HttpEntity<>(params, headers),
                    Map.class
            );
            if (tokenResponse == null || !tokenResponse.containsKey("id_token")) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google token exchange failed");
            }
            return extractEmailFromGoogleIdToken((String) tokenResponse.get("id_token"));
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google token exchange failed");
        }
    }

    @SuppressWarnings("unchecked")
    private String extractEmailFromGoogleIdToken(String idToken) {
        RestTemplate restTemplate = new RestTemplate();
        try {
            Map<String, String> response = restTemplate.getForObject(
                    "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken,
                    Map.class
            );
            if (response == null || !response.containsKey("email")) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token");
            }
            return response.get("email");
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token");
        }
    }

    @SuppressWarnings("unchecked")
    private AppleTokenClaims verifyAppleToken(String identityToken) {
        try {
            String[] parts = identityToken.split("\\.");
            if (parts.length != 3) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Apple token format");
            }

            // Parse header to find which key Apple used
            Map<String, String> header = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[0]), Map.class);
            String kid = header.get("kid");

            // Fetch Apple's current public keys
            RestTemplate restTemplate = new RestTemplate();
            Map<String, Object> jwks = restTemplate.getForObject("https://appleid.apple.com/auth/keys", Map.class);
            List<Map<String, String>> keys = (List<Map<String, String>>) jwks.get("keys");

            Map<String, String> jwk = keys.stream()
                    .filter(k -> kid.equals(k.get("kid")))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Apple signing key not found"));

            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.get("n")));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.get("e")));
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));

            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();

            if (!appleClientId.isEmpty()) {
                boolean audienceMatch = claims.getAudience().stream().anyMatch(appleClientId::equals);
                if (!audienceMatch) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Apple token audience mismatch");
                }
            }

            return new AppleTokenClaims(claims.getSubject(), claims.get("email", String.class));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Apple token");
        }
    }

    private record AppleTokenClaims(String sub, String email) {}
}