package com.dogsout.server.school;

import com.dogsout.server.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
// No unique constraint: with sign-up per date a person has one row per date.
// Duplicates are prevented in SchoolService.
@Table(name = "school_event_participants")
@Getter
@Setter
@NoArgsConstructor
public class SchoolEventParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id")
    private SchoolEvent event;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** The class this sign-up is for; null for a one-off or a whole course. */
    private java.time.LocalDate occurrenceDate;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant joinedAt;
}
