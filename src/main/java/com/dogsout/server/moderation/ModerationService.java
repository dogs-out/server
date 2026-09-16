package com.dogsout.server.moderation;

import com.dogsout.server.auth.EmailService;
import com.dogsout.server.chat.Message;
import com.dogsout.server.chat.MessageRepository;
import com.dogsout.server.matching.Match;
import com.dogsout.server.matching.MatchRepository;
import com.dogsout.server.photo.PhotoRendition;
import com.dogsout.server.photo.PhotoService;
import com.dogsout.server.user.User;
import com.dogsout.server.dog.Dog;
import com.dogsout.server.dog.DogPhoto;
import com.dogsout.server.photo.PhotoRendition;
import com.dogsout.server.user.UserPhoto;
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

    private static final String USER_NOT_FOUND = "User not found";

    private static final DateTimeFormatter TRANSCRIPT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Europe/Zurich"));

    private final BlockRepository blockRepository;
    private final MatchRepository matchRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final com.dogsout.server.dog.DogRepository dogRepository;
    private final com.dogsout.server.user.UserPhotoRepository userPhotoRepository;
    private final com.dogsout.server.dog.DogPhotoRepository dogPhotoRepository;
    private final EmailService emailService;
    private final PhotoService photoService;
    private final com.dogsout.server.sitter.SitterReviewRepository sitterReviewRepository;

    @Value("${app.admin-email}")
    private String adminEmail;

    public void blockUser(String email, Long targetUserId) {
        User me = findUser(email);
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, USER_NOT_FOUND));
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, USER_NOT_FOUND));
        blockRepository.findByBlockerAndBlocked(me, target).ifPresent(blockRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> getBlockedUsers(String email) {
        User me = findUser(email);
        return blockRepository.findByBlockerOrderByCreatedAtDesc(me).stream()
                .map(b -> new BlockedUserResponse(
                        b.getBlocked().getId(),
                        b.getBlocked().getName(),
                        photoService.url(b.getBlocked().getProfilePictureKey(), PhotoRendition.THUMB),
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
                %s%s""".formatted(
                me.getName(), me.getId(), me.getEmail(),
                reported.getName(), reported.getId(), reported.getEmail(),
                match.getId(),
                request.reason(),
                request.message() == null || request.message().isBlank() ? "(none)" : request.message().trim(),
                transcript,
                photoLinks(reported, request.reason()));

        emailService.sendReportEmail(adminEmail,
                "User report: %s (id %d)".formatted(reported.getName(), reported.getId()), body);
    }

    /**
     * Reports a profile, with no match required.
     *
     * <p>The match-based report can only be used once both people have said yes,
     * which is precisely the wrong shape: a profile with an offensive name, bio or
     * photo is one nobody swipes right on, so it never becomes a match and never
     * becomes reportable. It stays visible to everyone else instead.
     *
     * <p>There is no transcript to attach, because there is no conversation — what
     * is being reported is the profile itself, so the mail carries what the
     * reporter could actually see.
     */
    public void reportProfile(String email, Long userId, ReportRequest request) {
        User me = findUser(email);
        if (me.getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot report yourself");
        }
        User reported = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, USER_NOT_FOUND));

        String dogs = dogRepository.findByOwner(reported).stream()
                .map(dog -> "%s (%s)".formatted(dog.getName(), dog.getBreed()))
                .reduce((a, b) -> a + ", " + b)
                .orElse("(none)");

        String body = """
                A profile has been reported in Dogs Out.

                Reporter: %s (id %d, %s)
                Reported: %s (id %d, %s)

                Reason: %s
                Message from reporter: %s

                ── Reported profile ─────────────────────
                Name: %s
                Bio: %s
                Dogs: %s
                Photos: %d
                %s
                Reported from the profile itself, so there is no conversation to attach.""".formatted(
                me.getName(), me.getId(), me.getEmail(),
                reported.getName(), reported.getId(), reported.getEmail(),
                request.reason(),
                request.message() == null || request.message().isBlank() ? "(none)" : request.message().trim(),
                reported.getName(),
                reported.getBio() == null || reported.getBio().isBlank() ? "(empty)" : reported.getBio(),
                dogs,
                userPhotoRepository.findByUserOrderBySortOrderAsc(reported).size(),
                photoLinks(reported, request.reason()));

        emailService.sendReportEmail(adminEmail,
                "Profile report: %s (id %d)".formatted(reported.getName(), reported.getId()), body);
    }

    /**
     * Reports the written part of a sitter review.
     *
     * <p>A review comment is the one piece of free text in the app that a stranger
     * cannot answer: it sits on a sitter's profile, the sitter never agreed to a
     * conversation with the person who wrote it, and unlike a chat message there is
     * nobody on the other end to block. So it is hidden the moment it is reported
     * rather than when a human gets to the mail — the stars and the tags stay, and
     * only the prose goes dark until somebody has looked.
     *
     * <p>Anyone who can see the comment can report it, the sitter most of all.
     */
    public void reportReview(String email, Long reviewId, ReportRequest request) {
        User me = findUser(email);
        var review = sitterReviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));

        if (review.getRater().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You wrote this review");
        }

        boolean alreadyHidden = review.isHidden();
        if (!alreadyHidden) {
            review.setHiddenAt(java.time.Instant.now());
            sitterReviewRepository.save(review);
        }

        User author = review.getRater();
        User about = review.getSitter();
        String body = """
                A sitter review has been reported in Dogs Out.

                Reporter: %s (id %d, %s)
                Review author: %s (id %d, %s)
                Review is about: %s (id %d, %s)

                Reason: %s
                Message from reporter: %s

                ── Reported review ──────────────────────
                Stars: %d/5
                Highlights: %s
                Comment: %s

                The comment is hidden from the app already; it was hidden %s.
                To put it back, clear hidden_at on sitter_reviews id %d.""".formatted(
                me.getName(), me.getId(), me.getEmail(),
                author.getName(), author.getId(), author.getEmail(),
                about.getName(), about.getId(), about.getEmail(),
                request.reason(),
                request.message() == null || request.message().isBlank() ? "(none)" : request.message().trim(),
                review.getStars(),
                review.tagList().isEmpty() ? "(none)" : String.join(", ", review.tagList()),
                review.getComment() == null || review.getComment().isBlank()
                        ? "(none — nothing was written)" : review.getComment(),
                alreadyHidden ? "by an earlier report" : "by this report",
                review.getId());

        emailService.sendReportEmail(adminEmail,
                "Review report: %s on %s (review id %d)".formatted(
                        author.getName(), about.getName(), review.getId()),
                body);
    }

    /**
     * The reports where the pictures are the thing to look at.
     *
     * <p>Inappropriate photos obviously. Fake profile too: a stolen or stock
     * photograph is how that is judged, and a report saying so without them is
     * a report nobody can act on.
     *
     * <p>The client sends these strings verbatim in English; the contains check
     * is a net for a future reason that is also about pictures.
     */
    private static final java.util.Set<String> PHOTO_REASONS =
            java.util.Set.of("inappropriate photos", "fake profile");

    private static boolean wantsPhotos(String reason) {
        if (reason == null) return false;
        String normalised = reason.trim().toLowerCase(java.util.Locale.ROOT);
        return PHOTO_REASONS.contains(normalised) || normalised.contains("photo");
    }

    /**
     * Links to the reported photos, for the reasons above.
     *
     * <p>Not for the rest: a report about a name or a bio has no business putting
     * someone's pictures in an inbox. Where they are the point, the alternative
     * is opening the app and hunting for the profile, which is slower and stops
     * working once the account is gone.
     *
     * <p>These are the ordinary photo URLs. They are already public — every
     * client loads them the same way — so the mail adds no access that did not
     * exist, it only saves the search.
     */
    private String photoLinks(User reported, String reason) {
        if (!wantsPhotos(reason)) return "";

        StringBuilder links = new StringBuilder("\n── Photos ──────────────────────────────\n");
        List<UserPhoto> own = userPhotoRepository.findByUserOrderBySortOrderAsc(reported);
        if (own.isEmpty()) {
            links.append("(no profile photos)\n");
        }
        for (UserPhoto photo : own) {
            links.append(photoService.url(photo.getStorageKey(), PhotoRendition.FEED)).append('\n');
        }

        for (Dog dog : dogRepository.findByOwner(reported)) {
            List<DogPhoto> dogPhotos = dogPhotoRepository.findByDogOrderBySortOrderAsc(dog);
            if (dogPhotos.isEmpty()) continue;
            links.append("\n%s:\n".formatted(dog.getName()));
            for (DogPhoto photo : dogPhotos) {
                links.append(photoService.url(photo.getStorageKey(), PhotoRendition.FEED)).append('\n');
            }
        }
        return links.toString();
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, USER_NOT_FOUND));
    }
}
