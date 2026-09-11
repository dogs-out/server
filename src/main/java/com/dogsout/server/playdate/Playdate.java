package com.dogsout.server.playdate;

import com.dogsout.server.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "playdates")
@Getter
@Setter
@NoArgsConstructor
public class Playdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "host_id")
    private User host;

    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private String parkName;

    private String address;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private Instant startsAt;

    private Integer maxParticipants;

    /**
     * Whether dogsitters without a dog of their own may join.
     *
     * <p>Null means yes: the column arrives after playdates already existed, and
     * those were open to everyone, so null preserves what their hosts agreed to.
     */
    private Boolean sittersWelcome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlaydateVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlaydateStatus status = PlaydateStatus.ACTIVE;

    @CreationTimestamp
    /**
     * When each reminder was sent, so a restart or an overlapping scheduler run
     * cannot notify the same people twice. Null means not sent — a playdate created
     * less than a day out simply never gets the day reminder, which is correct.
     */
    private Instant dayReminderSentAt;
    private Instant hourReminderSentAt;

    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
