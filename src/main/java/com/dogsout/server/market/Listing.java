package com.dogsout.server.market;

import com.dogsout.server.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Something dog-related someone wants to pass on: a lead, a bed, a crate.
 *
 * <p>Dogs Out handles no money and no delivery. A listing is a notice; buyer and
 * seller sort out pickup and payment (cash, TWINT) between themselves in the
 * listing's chat. Category and status are strings, not enums: Hibernate's CHECK
 * constraint for enum columns goes stale in production when a value is added.
 */
@Entity
@Table(name = "listings", indexes = @Index(name = "idx_listings_status", columnList = "status"))
@Getter
@Setter
@NoArgsConstructor
public class Listing {

    public static final String ACTIVE = "ACTIVE";
    public static final String RESERVED = "RESERVED";
    public static final String SOLD = "SOLD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "seller_id")
    private User seller;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(length = 1000)
    private String description;

    /** In Rappen; null means free ("zu verschenken"). */
    private Integer priceRappen;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 12)
    private String status = ACTIVE;

    /** Hidden the moment someone reports it, until somebody has looked. */
    private Boolean hiddenByReport;

    /** Where it can be picked up: the seller's location when it was listed. */
    private Double latitude;
    private Double longitude;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
