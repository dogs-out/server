package com.dogsout.server.school;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A course, workshop or meetup a dog school runs. Like a playdate, but with a
 * price and pictures — and the money changes hands at the school, never here.
 */
@Entity
@Table(name = "school_events")
@Getter
@Setter
@NoArgsConstructor
public class SchoolEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "school_id")
    private DogSchool school;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private Instant startsAt;

    private Instant endsAt;

    /**
     * A weekly course: the weekdays it runs on ("TUESDAY,FRIDAY"), null for a one-off.
     * startsAt is then the first class and endsAt the end of the last (see WeeklySeries);
     * signing up is for the whole course.
     */
    @Column(length = 80)
    private String repeatWeekdays;

    /** How long one class of a course lasts; null for a one-off. */
    private Integer occurrenceMinutes;

    /**
     * How people sign up for a weekly event: "DATE" — each picks the classes they
     * want, with places per class — or "COURSE": once, for all classes, because
     * they build on each other. Null for a one-off (there is only one date).
     */
    @Column(length = 8)
    private String signupMode;

    /** Single classes the school called off, "2026-12-25,2027-01-01". */
    @Column(length = 2000)
    private String cancelledDates;

    @Column(length = 200)
    private String placeName;

    @Column(length = 300)
    private String address;

    private Double latitude;
    private Double longitude;

    /** Rappen per dog; null means free. */
    private Integer priceRappen;

    /** Places; null means no limit. */
    private Integer maxParticipants;

    private boolean cancelled;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
