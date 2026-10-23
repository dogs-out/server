package com.dogsout.server.market;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ListingMessageRepository extends JpaRepository<ListingMessage, Long> {
    List<ListingMessage> findByConversationOrderBySentAtAsc(ListingConversation conversation);
    Optional<ListingMessage> findFirstByConversationOrderBySentAtDesc(ListingConversation conversation);
    void deleteByConversation(ListingConversation conversation);
}
