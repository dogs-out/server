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
     * Whether it is still open. Nothing here assigns a sitter: the conversation
     * happens in the chat, and an owner closes the request when they have someone.
     * Guessing at that from messages would be wrong more often than not.
     */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(16)")
    private SittingRequestStatus status = SittingRequestStatus.OPEN;

    @CreationTimestamp
    private Instant createdAt;
}
