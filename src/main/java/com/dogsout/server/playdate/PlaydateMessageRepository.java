package com.dogsout.server.playdate;

import com.dogsout.server.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaydateMessageRepository extends JpaRepository<PlaydateMessage, Long> {

    List<PlaydateMessage> findByPlaydateOrderBySentAtAsc(Playdate playdate);

    void deleteByPlaydate(Playdate playdate);

    void deleteBySender(User sender);
}
