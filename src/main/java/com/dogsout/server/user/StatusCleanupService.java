package com.dogsout.server.user;

import com.dogsout.server.photo.PhotoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Clears statuses whose time is up.
 *
 * <p>An expired status already reads as "no status" everywhere, but its place and
 * photo stayed in the database and the photo in storage. A picture someone shared
 * for an afternoon walk should not outlive the walk, so this deletes it for real.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StatusCleanupService {

    private final UserRepository userRepository;
    private final PhotoService photoService;

    @Scheduled(fixedDelay = 15 * 60 * 1000L, initialDelay = 120_000)
    @Transactional
    public void clearExpired() {
        List<User> expired = userRepository.findByWalkStatusIsNotNullAndWalkStatusExpiresAtBefore(Instant.now());
        for (User user : expired) {
            String photo = user.getWalkStatusPhotoKey();
            UserService.clearStatus(user);
            userRepository.save(user);
            if (photo != null) photoService.delete(photo);
        }
        if (!expired.isEmpty()) log.info("Cleared {} expired statuses", expired.size());
    }
}
