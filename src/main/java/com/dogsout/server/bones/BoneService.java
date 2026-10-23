package com.dogsout.server.bones;

import com.dogsout.server.user.User;
import com.dogsout.server.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Bones: every swipe, treat or pass, costs one. Everyone gets {@value #DAILY}
 * a day, Premium {@value #DAILY_PREMIUM}; extra bones from an ad or a purchase are
 * spent after the daily ones and do not expire.
 *
 * <p>The point is coming back tomorrow rather than swiping the whole area in one
 * evening — which also leaves fewer people for the next evening.
 */
@Service
@RequiredArgsConstructor
public class BoneService {

    public static final int DAILY = 10;
    public static final int DAILY_PREMIUM = 20;
    /** Ads may top up, not replace the limit. */
    public static final int MAX_AD_REWARDS_PER_DAY = 5;
    /** The error reason the app recognises to show the out-of-bones card. */
    public static final String OUT_OF_BONES = "OUT_OF_BONES";

    static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BoneStatus status(User user) {
        rollOver(user);
        return toStatus(user);
    }

    /** Spends one bone for a swipe, or refuses with 429 OUT_OF_BONES. */
    @Transactional
    public BoneStatus spend(User user) {
        rollOver(user);
        int allowance = allowance(user);
        if (user.getBonesUsed() < allowance) {
            user.setBonesUsed(user.getBonesUsed() + 1);
        } else if (nz(user.getBonusBones()) > 0) {
            user.setBonusBones(user.getBonusBones() - 1);
        } else {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, OUT_OF_BONES);
        }
        userRepository.save(user);
        return toStatus(user);
    }

    /** One bone for a watched ad, up to the daily cap. */
    @Transactional
    public BoneStatus rewardAd(User user) {
        rollOver(user);
        if (nz(user.getAdBonesToday()) >= MAX_AD_REWARDS_PER_DAY) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "No more ad rewards today");
        }
        user.setAdBonesToday(nz(user.getAdBonesToday()) + 1);
        user.setBonusBones(nz(user.getBonusBones()) + 1);
        userRepository.save(user);
        return toStatus(user);
    }

    /** Bones from a purchase. */
    @Transactional
    public void grant(User user, int bones) {
        user.setBonusBones(nz(user.getBonusBones()) + bones);
        userRepository.save(user);
    }

    public boolean isPremium(User user) {
        return user.getPremiumUntil() != null && user.getPremiumUntil().isAfter(clock.instant());
    }

    private int allowance(User user) {
        return isPremium(user) ? DAILY_PREMIUM : DAILY;
    }

    /** A new day starts with nothing used and no ads watched. Not saved until something changes. */
    private void rollOver(User user) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        if (!today.equals(user.getBonesDay())) {
            user.setBonesDay(today);
            user.setBonesUsed(0);
            user.setAdBonesToday(0);
        }
        if (user.getBonesUsed() == null) user.setBonesUsed(0);
    }

    private BoneStatus toStatus(User user) {
        int allowance = allowance(user);
        int bonus = nz(user.getBonusBones());
        Instant refills = user.getBonesDay().plusDays(1).atStartOfDay(ZONE).toInstant();
        return new BoneStatus(
                Math.max(0, allowance - user.getBonesUsed()) + bonus,
                allowance, bonus, refills, isPremium(user),
                Math.max(0, MAX_AD_REWARDS_PER_DAY - nz(user.getAdBonesToday())));
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
