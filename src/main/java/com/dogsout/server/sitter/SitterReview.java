package com.dogsout.server.sitter;

import com.dogsout.server.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * What an owner thought of a sitter, once the sitting is over.
 *
 * <p>Tied to the request rather than to the pair, so the same two people can work
 * together repeatedly and each job is rated on its own. One review per request is
 * enforced by the unique constraint: the reminder can fire more than once across a
 * restart, and a second review would quietly overwrite the first's meaning.
 *
 * <p>Stars are required and the rest is not, because a rating that demands an essay
 * is a rating most people abandon. The tags exist for the same reason — picking
 * "great communication" is a sentence someone would otherwise not have written.
 */
@Entity
@Table(name = "sitter_reviews",
        uniqueConstraints = @UniqueConstraint(columnNames = "request_id"))
@Getter
@Setter
@NoArgsConstructor
public class SitterReview {

    /** The highlight tags an owner may pick, at most {@link #MAX_TAGS} of them. */
    public static final List<String> ALLOWED_TAGS = List.of(
            "Great communication",
            "Dog loved the sitter",
            "Punctual",
            "Sent photo updates",
            "Followed instructions",
            "Went the extra mile",
            "Flexible with timing",
            "Dog came back happy and tired");

    public static final int MAX_TAGS = 3;
    public static final int MIN_STARS = 1;
    public static final int MAX_STARS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private SittingRequest request;

    /** The owner doing the rating. Denormalised from the request so a query can filter on it. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "rater_id")
    private User rater;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sitter_id")
    private User sitter;

    @Column(nullable = false)
    private int stars;

    @Column(columnDefinition = "TEXT")
    private String comment;

    /** Highlight tags joined by "||", the same shape the rest of the app stores tags in. */
    @Column(name = "tags")
    private String tags;

    /**
     * Set when a comment has been reported, so it stops being shown while a human
     * looks at it. Hiding on report rather than after review is deliberate: the
     * alternative leaves hate speech on a stranger's profile for as long as the
     * mail sits unread.
     */
    private Instant hiddenAt;

    @CreationTimestamp
    private Instant createdAt;

    public List<String> tagList() {
        if (tags == null || tags.isBlank()) return List.of();
        return Arrays.stream(tags.split("\\|\\|")).filter(s -> !s.isBlank()).toList();
    }

    public boolean isHidden() {
        return hiddenAt != null;
    }
}
