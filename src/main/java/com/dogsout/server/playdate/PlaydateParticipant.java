package com.dogsout.server.playdate;

import com.dogsout.server.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A user's relationship to a playdate. The host has no participant row —
 * hosting is implicit via {@link Playdate#getHost()}. An INVITE_ONLY invite
 * is a row with status INVITED that flips to JOINED on accept; deleting the
 * row means declining or leaving.
 */
@Entity
@Table(name = "playdate_participants",
       uniqueConstraints = @UniqueConstraint(columnNames = {"playdate_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PlaydateParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "playdate_id")
    private Playdate playdate;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipantStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
