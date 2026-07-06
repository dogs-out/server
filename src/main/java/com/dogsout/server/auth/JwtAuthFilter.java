package com.dogsout.server.auth;

import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        if (jwtUtil.isValid(token)) {
            try {
                String email = jwtUtil.extractEmail(token);
                if (!isRevokedByPasswordChange(token, email)) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (UsernameNotFoundException ignored) {
                // Token is valid but the account was deleted — treat as unauthenticated
            }
        }

        chain.doFilter(request, response);
    }

    /** Tokens issued before the user's last password change are no longer accepted. */
    private boolean isRevokedByPasswordChange(String token, String email) {
        Instant changedAt = userRepository.findByEmail(email)
                .map(User::getPasswordChangedAt)
                .orElse(null);
        if (changedAt == null) return false;
        Date issuedAt = jwtUtil.extractIssuedAt(token);
        if (issuedAt == null) return true;
        // JWT iat has second precision — compare at second granularity so a token
        // issued in the same second as the change (the fresh replacement) stays valid
        return issuedAt.toInstant().truncatedTo(ChronoUnit.SECONDS)
                .isBefore(changedAt.truncatedTo(ChronoUnit.SECONDS));
    }
}
