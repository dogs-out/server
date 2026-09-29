package com.dogsout.server.sos;

import com.dogsout.server.dog.Dog;
import com.dogsout.server.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * An owner's "my dog is lost" alert, pushed to everyone within {@link SosService#RADIUS_KM}.
 *
 * <p>Open until the owner closes it or {@link SosService#OPEN_DAYS} pass. Closed-ness is a
 * timestamp plus a found flag rather than a status enum: Hibernate writes a CHECK constraint
 * for an enum column and leaves it stale under ddl-auto=update, which has broken production
 * here before.
 */
@Entity
@Table(name = "lost_dog_alerts")
@Getter
@Setter
@NoArgsConstructor
public class LostDogAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(optional = false)
    @JoinColumn(name = "dog_id")
    private Dog dog;

    /** Where the dog was last seen — chosen by the owner, not read from their profile. */
    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    private String placeName;

    @Column(length = 1000)
    private String note;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    /** Set when the owner closes the alert; null while it is open. */
    private Instant closedAt;

    /** True when it was closed because the dog is back. */
    private Boolean found;

    public boolean isOpen(Instant now) {
        return closedAt == null && createdAt != null
                && createdAt.isAfter(now.minus(java.time.Duration.ofDays(SosService.OPEN_DAYS)));
    }
}
