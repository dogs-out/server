package com.dogsout.server.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        // /ws authenticates itself via JWT in the handshake interceptor
                        .requestMatchers("/auth/**", "/uploads/**", "/error", "/ws", "/legal/**").permitAll()
                        // AdMob verifies the app by fetching this from the developer site in the store listing
                        .requestMatchers("/app-ads.txt").permitAll()
                        // The public website: the overview page and its stylesheet
                        .requestMatchers("/", "/index.html", "/site.css").permitAll()
                        // The admin page itself is static and logs in through /auth; its data is not.
                        .requestMatchers("/admin", "/admin/", "/admin/index.html", "/admin/admin.js", "/admin/admin.css").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Our own website (the admin page) plus the Expo web dev servers. The native
        // app sends no Origin header and is not affected by this list.
        config.setAllowedOrigins(List.of("https://api.dogsout.app", "https://dogsout.app",
                "http://localhost:8081", "http://localhost:19006"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // Response headers are hidden from browser JS unless named here. The native
        // app is unaffected, but the web target would silently never renew.
        config.setExposedHeaders(List.of(JwtAuthFilter.REFRESHED_TOKEN_HEADER));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}