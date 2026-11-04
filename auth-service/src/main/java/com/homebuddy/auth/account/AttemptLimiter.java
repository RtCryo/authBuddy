package com.homebuddy.auth.account;

import com.homebuddy.auth.AuthProperties;
import com.homebuddy.auth.ServerMode;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding-window limiter for sign-in and registration attempts, applied separately per
 * client IP and per email. Rejected attempts are not recorded, so the window clears once the
 * earlier attempts age out.
 */
@Component
@ServerMode
class AttemptLimiter {

    private static final int SWEEP_THRESHOLD = 10_000;

    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxAttempts;
    private final long windowMillis;

    AttemptLimiter(AuthProperties properties, Clock clock) {
        this.clock = clock;
        this.maxAttempts = properties.rateLimit().maxAttempts();
        this.windowMillis = properties.rateLimit().window().toMillis();
    }

    /** Records an attempt for both keys and returns whether both are within the limit. */
    boolean tryAcquire(String scope, String clientIp, String email) {
        long now = clock.millis();
        if (windows.size() > SWEEP_THRESHOLD) {
            sweep(now);
        }
        boolean ipAllowed = tryAcquire(scope + ":ip:" + clientIp, now);
        boolean emailAllowed = tryAcquire(scope + ":email:" + email, now);
        return ipAllowed && emailAllowed;
    }

    long retryAfterSeconds() {
        return Math.max(1, (windowMillis + 999) / 1000);
    }

    private boolean tryAcquire(String key, long now) {
        boolean[] allowed = new boolean[1];
        windows.compute(
                key,
                (k, window) -> {
                    Deque<Long> deque = window == null ? new ArrayDeque<>() : window;
                    prune(deque, now);
                    if (deque.size() < maxAttempts) {
                        deque.addLast(now);
                        allowed[0] = true;
                    }
                    return deque;
                });
        return allowed[0];
    }

    private void sweep(long now) {
        for (String key : windows.keySet()) {
            windows.computeIfPresent(
                    key,
                    (k, deque) -> {
                        prune(deque, now);
                        return deque.isEmpty() ? null : deque;
                    });
        }
    }

    private void prune(Deque<Long> deque, long now) {
        long cutoff = now - windowMillis;
        while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
            deque.pollFirst();
        }
    }
}
