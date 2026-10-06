package com.dogsout.server.dog;

import com.dogsout.server.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dogs")
@Getter
@Setter
@NoArgsConstructor
public class Dog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String breed;
    private LocalDate dateOfBirth;

    /**
     * The year this dog's birthday greeting went out. Cheaper and more honest than
     * a timestamp: the question is only ever "have we already done this year".
     */
    private Integer birthdayGreetedYear;

    @Column(columnDefinition = "TEXT")
    private String bio;

    /** Storage key of this dog's {@code sortOrder == 0} photo — see {@code User.profilePictureKey}. */
    @Column(name = "profile_picture_key")
    private String profilePictureKey;

    private Integer energyLevel;
    private String socialBehavior;

    @Column(columnDefinition = "TEXT")
    private String loves;

    private String offLeash;
    private Integer kidsComfort;

    /**
     * "MALE" or "FEMALE"; null for dogs saved before it was asked. A string, not an
     * enum: Hibernate's CHECK constraint for enum columns goes stale in production.
     * Owners asked for it because many dogs behave differently with males and females.
     */
    @Column(length = 8)
    private String sex;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}