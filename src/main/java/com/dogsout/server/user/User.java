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

    /** See Dog.birthdayGreetedYear — same marker, same reason. */
    private Integer birthdayGreetedYear;

    /**
     * When this account accepted the terms. Null means never, including for every
     * account that existed before the gate — they are asked on next launch, which
     * is the point of recording it rather than assuming it.
     */
    private Instant termsAcceptedAt;

    /**
     * Current status, and how long it stands for. Expiry is stored rather than a
     * duration so nothing has to run on a timer to make a status stop being true —
     * every read compares against the clock, and a status simply stops counting.
     */
    @Enumerated(EnumType.STRING)
    private WalkStatus walkStatus;

    private Instant walkStatusExpiresAt;

    /** Optional, and only ever set for the statuses that may carry a point. */
    private Double walkStatusLatitude;
    private Double walkStatusLongitude;

    @Column(columnDefinition = "TEXT")
    private String bio;

    /**
     * Storage key of the photo with {@code sortOrder == 0}, denormalized so that
     * rendering an avatar — chat rows, attendee lists, match cards — costs no join.
     * Always kept in step with {@code user_photos} by {@code UserService}.
     */
    @Column(name = "profile_picture_key")
    private String profilePictureKey;

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

    /**
     * The status as it should be read anywhere: an expired one is no status.
     *
     * <p>On the entity rather than in a service because every reader needs the
     * same answer, and a stale status leaking into one caller's view of a user is
     * exactly the bug this prevents.
     */
    public WalkStatus activeWalkStatus() {
        if (walkStatus == null) return null;
        return walkStatusExpiresAt != null && walkStatusExpiresAt.isBefore(Instant.now())
                ? null : walkStatus;
    }
}