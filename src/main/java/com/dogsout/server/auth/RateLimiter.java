package com.dogsout.server.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private record Bucket(int count, Instant windowStart) {}

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** Throws 429 if the key has exceeded MAX_ATTEMPTS within the lockout window. */
    public void check(String key) {
        Bucket b = buckets.get(key);
        if (b == null) return;
        if (Instant.now().isAfter(b.windowStart().plus(LOCKOUT))) {
            buckets.remove(key);
            return;
        }
        if (b.count() >= MAX_ATTEMPTS) {
            long secondsLeft = LOCKOUT.toSeconds() -
                    Duration.between(b.windowStart(), Instant.now()).toSeconds();
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts. Please try again in " + secondsLeft + " seconds.");
        }
    }

    /** Increments the failure counter for a key. Call after a failed attempt. */
    public void recordFailure(String key) {
        buckets.merge(key, new Bucket(1, Instant.now()),
                (existing, ignored) -> {
                    if (Instant.now().isAfter(existing.windowStart().plus(LOCKOUT))) {
                        return new Bucket(1, Instant.now());
                    }
                    return new Bucket(existing.count() + 1, existing.windowStart());
                });
    }

    /** Resets the counter for a key. Call after a successful login. */
    public void reset(String key) {
        buckets.remove(key);
    }
}
