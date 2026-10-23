package com.dogsout.server.school;

import com.dogsout.server.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A dog school's page in the app: who they are, when they are open, how to reach
 * them, and the events they run.
 *
 * <p>Anyone can say they are a dog school, so a school is only shown once an admin
 * has approved it — otherwise this is free advertising for whatever someone types.
 * Status is a string, not an enum: Hibernate's CHECK constraint for enum columns
 * goes stale in production when a value is added.
 */
@Entity
@Table(name = "dog_schools")
@Getter
@Setter
@NoArgsConstructor
public class DogSchool {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The account that runs it; one school per account. */
    @OneToOne(optional = false)
    @JoinColumn(name = "owner_id", unique = true)
    private User owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1500)
    private String description;

    @Column(length = 300)
    private String address;

    private Double latitude;
    private Double longitude;

    @Column(length = 40)
    private String phone;

    @Column(length = 200)
    private String website;

    @Column(length = 1000)
    private String openingHours;

    private String logoKey;

    @Column(nullable = false, length = 12)
    private String status = PENDING;

    /** Why it was turned down, shown to the owner. */
    @Column(length = 500)
    private String reviewNote;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
