package com.dogsout.server.chat;

import com.dogsout.server.matching.Match;
import com.dogsout.server.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @ManyToOne(optional = false)
    @JoinColumn(name = "receiver_id")
    private User receiver;

    @ManyToOne(optional = false)
    @JoinColumn(name = "match_id")
    private Match match;

    @Column(nullable = false)
    private String content;

    /**
     * Set when this message is a sitter's offer on a sitting request, so the
     * owner's side can render it with an Accept button instead of as plain text.
     *
     * <p>A nullable id rather than a message-kind enum: Hibernate writes a CHECK
     * constraint for an enum column and leaves it stale under ddl-auto=update,
     * which has broken production here before. Null means an ordinary message.
     */
    @Column(name = "sitting_request_id")
    private Long sittingRequestId;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant sentAt;

    private Boolean isRead = false;
}