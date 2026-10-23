package com.dogsout.server.bones;

import com.dogsout.server.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Where AdMob reports a finished rewarded ad (server-side verification). The app
 * sets its user id on the ad; this grants that user one bone once the signature
 * checks out. Set as the "Server-side verification" URL of the rewarded ad unit:
 * https://api.dogsout.app/bones/admob-ssv
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AdMobCallbackController {

    private final AdMobVerifier verifier;
    private final AdRewardTransactionRepository transactions;
    private final UserRepository userRepository;
    private final BoneService boneService;

    @GetMapping("/bones/admob-ssv")
    @Transactional
    public ResponseEntity<Void> callback(HttpServletRequest request) {
        String raw = request.getQueryString();
        // AdMob's "verify URL" button calls with no parameters; it only wants a 200.
        if (raw == null || raw.isBlank()) return ResponseEntity.ok().build();
        if (!verifier.verify(raw)) {
            log.warn("Rejected an AdMob reward callback with a bad signature");
            return ResponseEntity.status(403).build();
        }
        Map<String, String> params = AdMobVerifier.parse(raw);
        String txId = params.get("transaction_id");
        Long userId = parseLong(params.get("user_id"));
        // Signed but without our user id: a test callback. Nothing to pay out.
        if (txId == null || userId == null) return ResponseEntity.ok().build();
        if (transactions.existsById(txId)) return ResponseEntity.ok().build();

        userRepository.findById(userId).ifPresent(user -> {
            // Checked rather than caught: an exception from rewardAd would mark this
            // whole transaction for rollback, the record of the callback included.
            if (boneService.status(user).adRewardsLeft() > 0) boneService.rewardAd(user);
            else log.info("Ad reward for user {} over the daily cap", userId);
        });
        AdRewardTransaction tx = new AdRewardTransaction();
        tx.setTransactionId(txId);
        tx.setUserId(userId);
        tx.setCreatedAt(Instant.now());
        transactions.save(tx);
        return ResponseEntity.ok().build();
    }

    private static Long parseLong(String s) {
        try {
            return s == null ? null : Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
