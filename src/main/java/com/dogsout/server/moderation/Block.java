package com.dogsout.server.moderation;

import com.dogsout.server.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "blocks", uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "blocker_id")
    private User blocker;

    @ManyToOne(optional = false)
    @JoinColumn(name = "blocked_id")
    private User blocked;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}