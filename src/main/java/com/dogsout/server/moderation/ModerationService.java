package com.dogsout.server.moderation;

import com.dogsout.server.auth.EmailService;
import com.dogsout.server.chat.Message;
import com.dogsout.server.chat.MessageRepository;
import com.dogsout.server.matching.Match;
import com.dogsout.server.matching.MatchRepository;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ModerationService {

    private static final DateTimeFormatter TRANSCRIPT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Europe/Zurich"));

    private final BlockRepository blockRepository;
    private final MatchRepository matchRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.admin-email}")
    private String adminEmail;

    public void blockUser(String email, Long targetUserId) {
        User me = findUser(email);
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (me.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot block yourself");
        }
        // Idempotent: blocking twice is not an error
        if (blockRepository.findByBlockerAndBlocked(me, target).isPresent()) return;

        Block block = new Block();
        block.setBlocker(me);
        block.setBlocked(target);
        blockRepository.save(block);
    }

    public void unblockUser(String email, Long targetUserId) {
        User me = findUser(email);
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        blockRepository.findByBlockerAndBlocked(me, target).ifPresent(blockRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> getBlockedUsers(String email) {
        User me = findUser(email);
        return blockRepository.findByBlockerOrderByCreatedAtDesc(me).stream()
                .map(b -> new BlockedUserResponse(
                        b.getBlocked().getId(),
                        b.getBlocked().getName(),
                        b.getBlocked().getProfilePicture(),
                        b.getCreatedAt()))
                .toList();
    }

    public void reportUser(String email, Long matchId, ReportRequest request) {
        User me = findUser(email);
        Match match = findParticipantMatch(matchId, me);
        User reported = otherUser(match, me);

        List<Message> messages = messageRepository.findByMatchOrderBySentAtAsc(match);
        StringBuilder transcript = new StringBuilder();
        for (Message m : messages) {
            transcript.append("[%s] %s: %s%n".formatted(
                    TRANSCRIPT_TIME.format(m.getSentAt()), m.getSender().getName(), m.getContent()));
        }
        if (messages.isEmpty()) {
            transcript.append("(no messages exchanged yet)\n");
        }

        String body = """
                A user has been reported in Dogs Out.

                Reporter: %s (id %d, %s)
                Reported: %s (id %d, %s)
                Match id: %d

                Reason: %s
                Message from reporter: %s

                ── Chat transcript ──────────────────────
                %s""".formatted(
                me.getName(), me.getId(), me.getEmail(),
                reported.getName(), reported.getId(), reported.getEmail(),
                match.getId(),
                request.reason(),
                request.message() == null || request.message().isBlank() ? "(none)" : request.message().trim(),
                transcript);

        emailService.sendReportEmail(adminEmail,
                "User report: %s (id %d)".formatted(reported.getName(), reported.getId()), body);
    }

    public void unmatch(String email, Long matchId) {
        User me = findUser(email);
        Match match = findParticipantMatch(matchId, me);
        messageRepository.deleteByMatch(match);
        matchRepository.delete(match);
    }

    private Match findParticipantMatch(Long matchId, User me) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
        boolean participant = match.getUser1().getId().equals(me.getId())
                || match.getUser2().getId().equals(me.getId());
        if (!participant) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your match");
        }
        return match;
    }

    private User otherUser(Match match, User me) {
        return match.getUser1().getId().equals(me.getId()) ? match.getUser2() : match.getUser1();
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
