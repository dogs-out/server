package com.dogsout.server.sitter;

import com.dogsout.server.photo.PhotoRendition;
import com.dogsout.server.photo.PhotoService;
import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * Ratings an owner leaves for the sitter who looked after their dog.
 *
 * <p>Only the owner of a finished job that somebody actually sat may rate it, and
 * only once. That is a narrow gate on purpose: a rating anybody can leave is a
 * rating that says nothing about whether the two people ever met.
 *
 * <p>Stars are the required part. The comment and the highlight tags are optional
 * because a form that demands prose is a form most people close — and the tags
 * exist so that the common praise ("she sent photos", "he was on time") can be one
 * tap rather than a sentence nobody bothers to type.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SitterReviewService {

    private final SitterReviewRepository sitterReviewRepository;
    private final SittingRequestRepository sittingRequestRepository;
    private final UserRepository userRepository;
    private final PhotoService photoService;

    /** The tags an owner may pick from, for the client to render. */
    public List<String> allowedTags() {
        return SitterReview.ALLOWED_TAGS;
    }

    public SitterReviewResponse submit(String email, CreateSitterReview review) {
        User me = findUser(email);
        SittingRequest job = sittingRequestRepository.findById(review.requestId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        if (!job.getOwner().getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can rate the sitter");
        }
        if (job.getSitter() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nobody sat for this job");
        }
        if (!job.isOver(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wait until the sitting is over");
        }
        if (sitterReviewRepository.existsByRequest(job)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already rated this sitting");
        }

        SitterReview saved = new SitterReview();
        saved.setRequest(job);
        saved.setRater(me);
        saved.setSitter(job.getSitter());
        saved.setStars(review.stars());
        saved.setComment(review.comment() == null || review.comment().isBlank()
                ? null : review.comment().trim());
        saved.setTags(validatedTags(review.tags()));
        return toResponse(sitterReviewRepository.save(saved), me);
    }

    /**
     * Keeps the stored tags to the allowed list and to three.
     *
     * <p>Validated rather than trusted because they are shown on a stranger's
     * profile: an unchecked list is a free-text field with extra steps.
     */
    private static String validatedTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        List<String> kept = tags.stream()
                .filter(SitterReview.ALLOWED_TAGS::contains)
                .distinct()
                .limit(SitterReview.MAX_TAGS)
                .toList();
        if (tags.stream().anyMatch(t -> !SitterReview.ALLOWED_TAGS.contains(t))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown highlight tag");
        }
        if (tags.size() > SitterReview.MAX_TAGS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Pick at most " + SitterReview.MAX_TAGS + " highlights");
        }
        return kept.isEmpty() ? null : String.join("||", kept);
    }

    /** A sitter's reviews for their profile, newest first, hidden ones left out. */
    public List<SitterReviewResponse> forSitter(String email, Long sitterId) {
        User me = findUser(email);
        User sitter = userRepository.findById(sitterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return sitterReviewRepository.findBySitterAndHiddenAtIsNullOrderByCreatedAtDesc(sitter).stream()
                .map(r -> toResponse(r, me))
                .toList();
    }

    public SitterRatingSummary summaryFor(Long sitterId) {
        User sitter = userRepository.findById(sitterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        double average = sitterReviewRepository.averageStars(sitter);
        return new SitterRatingSummary(
                Math.round(average * 10) / 10.0,
                sitterReviewRepository.countBySitterAndHiddenAtIsNull(sitter));
    }

    /** Sittings this owner still owes a rating for, so the app can prompt. */
    public List<SittingRequestResponse> pending(String email) {
        User me = findUser(email);
        Instant now = Instant.now();
        return sittingRequestRepository.findByOwnerOrderByStartsAtAsc(me).stream()
                .filter(r -> r.getSitter() != null && r.isOver(now))
                .filter(r -> !sitterReviewRepository.existsByRequest(r))
                .map(r -> new SittingRequestResponse(
                        r.getId(), me.getId(), me.getName(),
                        photoService.url(me.getProfilePictureKey(), PhotoRendition.THUMB),
                        r.getStartsAt(), r.getEndsAt(), List.of(), List.of(), r.getNote(),
                        r.getStatus().name(), true,
                        r.getSitter().getId(), r.getSitter().getName(),
                        photoService.url(r.getSitter().getProfilePictureKey(), PhotoRendition.THUMB),
                        true, false, true, -1))
                .toList();
    }

    SitterReviewResponse toResponse(SitterReview review, User viewer) {
        User rater = review.getRater();
        return new SitterReviewResponse(
                review.getId(),
                review.getSitter().getId(),
                rater.getId(),
                rater.getName(),
                photoService.url(rater.getProfilePictureKey(), PhotoRendition.THUMB),
                review.getStars(),
                // A hidden comment keeps its stars and tags; only the prose goes.
                review.isHidden() ? null : review.getComment(),
                review.tagList(),
                rater.getId().equals(viewer.getId()),
                review.getCreatedAt());
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
