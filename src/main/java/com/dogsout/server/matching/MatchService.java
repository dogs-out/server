package com.dogsout.server.matching;

import com.dogsout.server.chat.Message;
import com.dogsout.server.chat.MessageRepository;
import com.dogsout.server.moderation.BlockRepository;
import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.user.User;
import com.dogsout.server.websocket.ChatSocketEvent;
import com.dogsout.server.websocket.ChatSocketHandler;
import com.dogsout.server.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final BlockRepository blockRepository;
    private final ChatSocketHandler chatSocketHandler;
    private final PushNotificationService pushNotificationService;

    public SwipeResponse swipe(String email, SwipeRequest request) {
        User me = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        User target = userRepository.findById(request.targetUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found"));

        if (me.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot swipe on yourself");
        }
        if (blockRepository.existsBlockBetween(me.getId(), target.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This user is not available");
        }

        // Already swiped this user — never insert a second row, just report the current state
        Optional<Match> mine = matchRepository.findFirstByUser1AndUser2OrderByIdAsc(me, target);
        if (mine.isPresent()) {
            Match existing = mine.get();
            return new SwipeResponse(existing.getStatus() == MatchStatus.MATCHED, existing.getId());
        }

        boolean isLike = "LIKE".equalsIgnoreCase(request.action());

        if (isLike) {
            // A mutual match already exists from an earlier exchange
            Optional<Match> theirMatched = matchRepository
                    .findFirstByUser1AndUser2AndStatusOrderByIdAsc(target, me, MatchStatus.MATCHED);
            if (theirMatched.isPresent()) {
                return new SwipeResponse(true, theirMatched.get().getId());
            }
            // They liked us first — promote their pending like to a match
            Optional<Match> theirPending = matchRepository
                    .findFirstByUser1AndUser2AndStatusOrderByIdAsc(target, me, MatchStatus.PENDING);
            if (theirPending.isPresent()) {
                Match mutual = theirPending.get();
                mutual.setStatus(MatchStatus.MATCHED);
                matchRepository.save(mutual);
                // The other user finds out live — the swiper sees the match overlay anyway
                chatSocketHandler.sendToUser(target.getId(), ChatSocketEvent.newMatch(mutual.getId()));
                if (!chatSocketHandler.isOnline(target.getId())) {
                    pushNotificationService.send(target, "It's a Match! 🐾",
                            "You and " + me.getName() + " gave each other a treat. Say hi!",
                            java.util.Map.of("type", "NEW_MATCH", "matchId", mutual.getId()));
                }
                return new SwipeResponse(true, mutual.getId());
            }
        }

        Match swipe = new Match();
        swipe.setUser1(me);
        swipe.setUser2(target);
        swipe.setStatus(isLike ? MatchStatus.PENDING : MatchStatus.REJECTED);
        Match saved = matchRepository.save(swipe);

        return new SwipeResponse(false, saved.getId());
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> getMatches(String email) {
        User me = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Blocked users (in either direction) disappear from the match list
        java.util.Set<Long> blocked = new java.util.HashSet<>(blockRepository.findBlockedIdsByBlockerId(me.getId()));
        blocked.addAll(blockRepository.findBlockerIdsByBlockedId(me.getId()));

        return matchRepository.findAllMatchesForUser(me.getId()).stream()
                .filter(match -> {
                    Long otherId = match.getUser1().getId().equals(me.getId())
                            ? match.getUser2().getId() : match.getUser1().getId();
                    return !blocked.contains(otherId);
                })
                .map(match -> toMatchResponse(match, me))
                // newest activity first: last message if any, otherwise the match itself
                .sorted(Comparator.comparing(
                        (MatchResponse m) -> m.lastMessageSentAt() != null ? m.lastMessageSentAt() : m.matchedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private MatchResponse toMatchResponse(Match match, User me) {
        User other = match.getUser1().getId().equals(me.getId()) ? match.getUser2() : match.getUser1();
        Optional<Message> last = messageRepository.findTopByMatchOrderBySentAtDesc(match);
        long unread = messageRepository.countByMatchAndReceiverAndIsReadFalse(match, me);
        return new MatchResponse(
                match.getId(),
                other.getId(),
                other.getName(),
                other.getProfilePicture(),
                match.getCreatedAt(),
                last.map(Message::getContent).orElse(null),
                last.map(Message::getSentAt).orElse(null),
                last.map(m -> m.getSender().getId()).orElse(null),
                unread
        );
    }
}
