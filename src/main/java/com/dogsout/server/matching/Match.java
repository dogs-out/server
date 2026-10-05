package com.dogsout.server.matching;

import com.dogsout.server.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "matches", uniqueConstraints = @UniqueConstraint(columnNames = {"user1_id", "user2_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user1_id")
    private User user1;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user2_id")
    private User user2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    /**
     * Whether user1 — who liked first — has seen the "It's a Match!" screen. The
     * second swiper sees it the moment they swipe; the first liker only finds out
     * later, so the app shows it to them on their next visit. False is "still to
     * show"; null means there is nothing to celebrate (matches from before this
     * existed, or a chat opened through Dogsitting, which is not a mutual like).
     */
    private Boolean user1Celebrated;
}