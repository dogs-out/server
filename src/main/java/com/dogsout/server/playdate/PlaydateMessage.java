package com.dogsout.server.playdate;

import com.dogsout.server.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

// Group chat message — no per-user read tracking in v1
@Entity
@Table(name = "playdate_messages")
@Getter
@Setter
@NoArgsConstructor
public class PlaydateMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "playdate_id")
    private Playdate playdate;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(nullable = false)
    private String content;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant sentAt;
}
