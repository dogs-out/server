package com.dogsout.server.sitter;

import com.dogsout.server.matching.Match;
import com.dogsout.server.matching.MatchRepository;
import com.dogsout.server.matching.MatchStatus;
import com.dogsout.server.moderation.BlockRepository;
import com.dogsout.server.notification.PushNotificationService;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import com.dogsout.server.websocket.ChatSocketEvent;
import com.dogsout.server.websocket.ChatSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class SitterService {

    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final BlockRepository blockRepository;
    private final ChatSocketHandler chatSocketHandler;
    private final PushNotificationService pushNotificationService;

    /**
     * Dogsitting contact opens a chat immediately in either direction — a sitter
     * reaching an owner from the seeker pool, or an owner reaching a sitter from the
     * available-sitters pool. Both sides opted into their pool, so no mutual like is
     * required. Chat is authorised through MATCHED matches, so this finds-or-creates
     * a Match row with that status.
     */
    public ContactSitterResponse contact(String email, Long targetUserId) {
        User me = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (me.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot contact yourself");
        }
        if (!Boolean.TRUE.equals(me.getIsSitter()) && !Boolean.TRUE.equals(me.getLookingForSitter())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Enable a dogsitting role to contact people");
        }
        if (blockRepository.existsBlockBetween(me.getId(), target.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This user is not available");
        }

        // Reuse any existing row in either direction — the unique constraint on
        // (user1, user2) forbids a second one, and an existing MATCHED row already
        // carries the chat history.
        Optional<Match> existing = matchRepository.findFirstByUser1AndUser2OrderByIdAsc(me, target)
                .or(() -> matchRepository.findFirstByUser1AndUser2OrderByIdAsc(target, me));

        if (existing.isPresent()) {
            Match match = existing.get();
            if (match.getStatus() == MatchStatus.MATCHED) {
                return new ContactSitterResponse(match.getId());
            }
            // An earlier one-sided like or pass is superseded by the sitter contact
            match.setStatus(MatchStatus.MATCHED);
            matchRepository.save(match);
            notifyTarget(me, target, match.getId());
            return new ContactSitterResponse(match.getId());
        }

        // Checked only before opening a *new* chat: an existing match stays reachable
        // even if the other side has since switched their dogsitting toggles off.
        boolean sitterToOwner = Boolean.TRUE.equals(me.getIsSitter())
                && Boolean.TRUE.equals(target.getLookingForSitter());
        boolean ownerToSitter = Boolean.TRUE.equals(me.getLookingForSitter())
                && Boolean.TRUE.equals(target.getIsSitter());
        if (!sitterToOwner && !ownerToSitter) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This user is not available for dogsitting");
        }

        Match match = new Match();
        match.setUser1(me);
        match.setUser2(target);
        match.setStatus(MatchStatus.MATCHED);
        Match saved = matchRepository.save(match);
        notifyTarget(me, target, saved.getId());
        return new ContactSitterResponse(saved.getId());
    }

    private void notifyTarget(User me, User target, Long matchId) {
        chatSocketHandler.sendToUser(target.getId(), ChatSocketEvent.newMatch(matchId));
        if (!chatSocketHandler.isOnline(target.getId())) {
            pushNotificationService.send(target, "New sitter contact 🐾",
                    me.getName() + " is available to sit for your dog. Say hi!",
                    java.util.Map.of("type", "NEW_MATCH", "matchId", matchId));
        }
    }
}
