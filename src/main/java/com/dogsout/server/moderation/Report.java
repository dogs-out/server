package com.dogsout.server.moderation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A report as the admin page sees it: who reported whom, why, what the reporter
 * saw, and what was done about it.
 *
 * <p>People are recorded by id plus a copy of their name and email at the time,
 * not as relations. A report has to outlive the accounts in it — repeat behaviour
 * is the point of keeping them — and must never stand in the way of deleting one.
 *
 * <p>Kind and status are plain strings, not enums: Hibernate writes a CHECK
 * constraint for an enum column that goes stale when a value is added later.
 */
@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_status", columnList = "status"),
        @Index(name = "idx_reports_reported", columnList = "reported_id")})
@Getter
@Setter
@NoArgsConstructor
public class Report {

    public static final String OPEN = "OPEN";
    public static final String RESOLVED = "RESOLVED";
    public static final String DISMISSED = "DISMISSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** CHAT, PROFILE or REVIEW. */
    @Column(nullable = false, length = 16)
    private String kind;

    private Long reporterId;
    private String reporterName;
    private String reporterEmail;

    @Column(name = "reported_id")
    private Long reportedId;
    private String reportedName;
    private String reportedEmail;

    private Long matchId;
    private Long reviewId;

    @Column(length = 100)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String message;

    /** What was sent by email: the transcript, profile or review as it was then. */
    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false, length = 16)
    private String status = OPEN;

    @Column(columnDefinition = "TEXT")
    private String adminNote;

    private Instant handledAt;
    private String handledBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
