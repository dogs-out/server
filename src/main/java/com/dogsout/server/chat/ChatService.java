package com.dogsout.server.chat;

import com.dogsout.server.ProfanityFilter;
import com.dogsout.server.moderation.BlockRepository;
import com.dogsout.server.matching.Match;
import com.dogsout.server.matching.MatchRepository;
import com.dogsout.server.matching.MatchStatus;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ChatService {

    private final MessageRepository messageRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final ProfanityFilter profanityFilter;
    private final BlockRepository blockRepository;

    public List<MessageResponse> getMessages(String email, Long matchId) {
        User me = findUser(email);
        Match match = findMatchedMatch(matchId, me);

        // Opening the chat marks everything addressed to me as read
        List<Message> unread = messageRepository.findByMatchAndReceiverAndIsReadFalse(match, me);
        unread.forEach(m -> m.setIsRead(true));
        messageRepository.saveAll(unread);

        return messageRepository.findByMatchOrderBySentAtAsc(match)
                .stream().map(this::toResponse).toList();
    }

    public MessageResponse sendMessage(String email, Long matchId, SendMessageRequest request) {
        User me = findUser(email);
        Match match = findMatchedMatch(matchId, me);
        if (profanityFilter.containsProfanity(request.content())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your message contains inappropriate language.");
        }
        User other = match.getUser1().getId().equals(me.getId()) ? match.getUser2() : match.getUser1();

        Message message = new Message();
        message.setSender(me);
        message.setReceiver(other);
        message.setMatch(match);
        message.setContent(request.content().trim());
        return toResponse(messageRepository.save(message));
    }

    private Match findMatchedMatch(Long matchId, User me) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
        boolean participant = match.getUser1().getId().equals(me.getId())
                || match.getUser2().getId().equals(me.getId());
        if (!participant) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your match");
        }
        if (match.getStatus() != MatchStatus.MATCHED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can only chat with matched users");
        }
        Long otherId = match.getUser1().getId().equals(me.getId())
                ? match.getUser2().getId() : match.getUser1().getId();
        if (blockRepository.existsBlockBetween(me.getId(), otherId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This chat is no longer available");
        }
        return match;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private MessageResponse toResponse(Message m) {
        return new MessageResponse(m.getId(), m.getSender().getId(), m.getContent(), m.getSentAt(),
                Boolean.TRUE.equals(m.getIsRead()));
    }
}
