package com.dogsout.server.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One row per user per day they used the app, in Swiss time. This is what daily
 * and weekly active users are counted from: a single "last active" timestamp can
 * say who is active now, but not how many were active last Tuesday.
 *
 * <p>A plain user id rather than a relation, so the row never stands in the way of
 * deleting an account; the deletion removes these rows itself.
 */
@Entity
@Table(name = "user_activity_days",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "activity_day"}))
@Getter
@Setter
@NoArgsConstructor
public class UserActivityDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // "day" is a reserved word in some SQL dialects, hence the longer column name.
    @Column(name = "activity_day", nullable = false)
    private LocalDate day;

    /** "IOS" or "ANDROID" on that day, when known. */
    @Column(length = 16)
    private String platform;

    public UserActivityDay(Long userId, LocalDate day, String platform) {
        this.userId = userId;
        this.day = day;
        this.platform = platform;
    }
}
