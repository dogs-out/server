package com.dogsout.server.chat;

import com.dogsout.server.matching.Match;
import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByMatchOrderBySentAtAsc(Match match);

    Optional<Message> findTopByMatchOrderBySentAtDesc(Match match);

    long countByMatchAndReceiverAndIsReadFalse(Match match, User receiver);

    List<Message> findByMatchAndReceiverAndIsReadFalse(Match match, User receiver);

    void deleteByMatch(Match match);

    void deleteBySenderOrReceiver(User sender, User receiver);
}
