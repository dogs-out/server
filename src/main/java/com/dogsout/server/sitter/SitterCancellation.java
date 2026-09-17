package com.dogsout.server.sitter;

import com.dogsout.server.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A sitter pulling out of a job they had accepted.
 *
 * <p>Kept as its own row rather than as a counter on the user, because the rule
 * is about a rolling year: a counter can only ever be reset, and resetting it
 * either forgives too early or never. Rows can be counted over whatever window
 * the rule happens to use.
 *
 * <p>Only late ones count against a sitter. Cancelling a week ahead is the
 * system working — the owner has time to find somebody else, and a sitter who
 * knows they cannot make it should say so immediately rather than be
 * discouraged from admitting it.
 */
@Entity
@Table(name = "sitter_cancellations")
@Getter
@Setter
@NoArgsConstructor
public class SitterCancellation {

    /** How close to the start counts as late. */
    public static final long LATE_HOURS = 24;

    /** Late cancellations within a rolling year before the penalty applies. */
    public static final int STRIKES = 3;

    /** How long the penalty lasts once earned. */
    public static final int PENALTY_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sitter_id")
    private User sitter;

    /** Not a foreign key on purpose: the request may be deleted, the strike stands. */
    @Column(name = "request_id")
    private Long requestId;

    /** Within {@link #LATE_HOURS} of the start, which is the only kind that counts. */
    private boolean late;

    /** The start the sitter pulled out of, for the mail and for explaining a penalty. */
    private Instant sittingStartsAt;

    @CreationTimestamp
    private Instant createdAt;
}
