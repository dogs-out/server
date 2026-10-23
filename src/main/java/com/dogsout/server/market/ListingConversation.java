package com.dogsout.server.market;

import com.dogsout.server.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A buyer asking a seller about one listing. Deliberately not a match: asking
 * about a lead must not put a stranger among your matches, with everything a
 * match unlocks (walk statuses, invites, the who's-outside list).
 */
@Entity
@Table(name = "listing_conversations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"listing_id", "buyer_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ListingConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "listing_id")
    private Listing listing;

    @ManyToOne(optional = false)
    @JoinColumn(name = "buyer_id")
    private User buyer;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    private Instant lastMessageAt;
    private Instant buyerReadAt;
    private Instant sellerReadAt;
}
