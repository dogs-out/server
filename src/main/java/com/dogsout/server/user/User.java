package com.dogsout.server.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    private String password;

    private LocalDate dateOfBirth;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(columnDefinition = "TEXT")
    private String profilePicture;

    private Double latitude;
    private Double longitude;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private AuthProvider authProvider;

    private Boolean isActive;
    private Boolean emailVerified = false;

    private String verificationCode;
    private LocalDateTime verificationCodeExpiry;

    private String resetToken;
    private LocalDateTime resetTokenExpiry;

    // Tokens issued before this moment are rejected (see JwtAuthFilter)
    private Instant passwordChangedAt;

    @Column(unique = true)
    private String appleUserId;

    @Column(columnDefinition = "TEXT")
    private String lifestyleTags;

    @Column(columnDefinition = "TEXT")
    private String personalityTags;

    private String relationshipStatus;

    // Dogsitting — null means "not set": hasDog defaults to true (legacy users
    // all went through dog onboarding), the sitter flags default to false.
    private Boolean hasDog;
    private Boolean isSitter;
    private Boolean lookingForSitter;

    @Column(columnDefinition = "TEXT")
    private String sitterWeekdays;

    private Integer sitterExperienceYears;

    @Column(columnDefinition = "TEXT")
    private String sitterTags;

    private Integer maxDistanceKm;
    private Integer minAge;
    private Integer maxAge;
    private Integer minDogAge;
    private Integer maxDogAge;

    // Push notifications (Expo)
    private String expoPushToken;
    private Boolean notificationsEnabled = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}