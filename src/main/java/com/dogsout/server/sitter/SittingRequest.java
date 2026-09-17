package com.dogsout.server.sitter;

import com.dogsout.server.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * An owner asking for a sitter for a particular window of time.
 *
 * <p>The sitter feature until now was a list of people: an owner browsed sitters
 * and wrote to one, hoping they were free. This is the other direction — the need
 * is posted with its hours, and any sitter who is free then can pick it up. It
 * turns "is anyone available?" into a question the app can answer.
 *
 * <p>Which dogs is part of the request because it changes the answer: looking
 * after one calm terrier and looking after three is not the same job.
 */
@Entity
@Table(name = "sitting_requests")
@Getter
@Setter
@NoArgsConstructor
public class SittingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    private Instant startsAt;
    private Instant endsAt;

    /** Dog ids joined by "||", the same shape the tag columns use. */
    @Column(name = "dog_ids")
    private String dogIds;

    @Column(columnDefinition = "TEXT")
    private String note;

    /**
     * Whether it is still taking offers.
     *
     * <p>Deliberately only two values, with the accepted sitter carried in its own
     * column rather than in a third status. Hibernate writes a CHECK constraint
     * for an enum column and does not rewrite it under ddl-auto=update, so adding
     * a value here breaks production while passing every local test — it has cost
     * us a release already. "Accepted" is CLOSED with a sitter attached, which
     * needs no new value and reads the same.
     */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(16)")
    private SittingRequestStatus status = SittingRequestStatus.OPEN;

    /**
     * Who got the job, once the owner accepted one of the offers.
     *
     * <p>Null on an open request, and still null on one the owner closed without
     * finding anybody — the distinction the rating reminder turns on.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sitter_id")
    private User sitter;

    private Instant acceptedAt;

    // ─── Handover details ─────────────────────────────────────────────────────
    // Filled in by the owner once somebody is coming. Deliberately not asked for
    // at posting time: most requests never get taken, and nobody types their
    // address and their vet's number into a form on the chance that they might.

    /** Free text, one instruction per line. Feeding, keys, the walk they like. */
    @Column(columnDefinition = "TEXT")
    private String todoList;

    /** Who the sitter rings if something is wrong and the owner is unreachable. */
    private String emergencyPhone;

    /** Where the sitting happens, as text — from the map pin or the owner's place. */
    private String addressLabel;
    private Double addressLatitude;
    private Double addressLongitude;

    /** Set when the details were last saved, so a re-send can say "updated". */
    private Instant detailsSharedAt;

    /**
     * When the owner was asked to rate. Stamped before the push goes out so a
     * restart or a second scheduler pass cannot ask twice.
     */
    private Instant ratingReminderSentAt;

    @CreationTimestamp
    private Instant createdAt;

    /** True once the window has passed, which is what takes it off the job board. */
    public boolean isOver(Instant now) {
        return endsAt != null && endsAt.isBefore(now);
    }

    /** Accepted is closed-with-a-sitter; see the note on {@link #status}. */
    public boolean isAccepted() {
        return sitter != null;
    }
}
