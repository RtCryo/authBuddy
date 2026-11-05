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
 * earlier attempts age out. A caller can {@link #release(Attempt) release} an accepted attempt that
 * should not count, such as a successful sign-in.
 */
@Component
@ServerMode
class AttemptLimiter {

    private static final int SWEEP_THRESHOLD = 10_000;

    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxAttempts;
    private final long windowMillis;

    /** Time of the last sweep, so a map full of live windows is not rescanned on every request. */
    private volatile long lastSweep = Long.MIN_VALUE / 2;

    AttemptLimiter(AuthProperties properties, Clock clock) {
        this.clock = clock;
        this.maxAttempts = properties.rateLimit().maxAttempts();
        this.windowMillis = properties.rateLimit().window().toMillis();
    }

    /** An attempt recorded for both keys, which the caller may release again. */
    record Attempt(String ipKey, String emailKey, long at) {}

    /**
     * Records the attempt for both keys when both are within the limit and returns it, or returns
     * {@code null} without recording anything when either key is over the limit.
     */
    Attempt tryAcquire(String scope, String clientIp, String email) {
        long now = clock.millis();
        if (windows.size() > SWEEP_THRESHOLD && now - lastSweep >= windowMillis) {
            lastSweep = now;
            sweep(now);
        }
        String ipKey = scope + ":ip:" + clientIp;
        String emailKey = scope + ":email:" + email;
        if (!tryAcquire(ipKey, now)) {
            return null;
        }
        if (!tryAcquire(emailKey, now)) {
            release(ipKey, now);
            return null;
        }
        return new Attempt(ipKey, emailKey, now);
    }

    /** Removes an accepted attempt from both windows so it no longer counts against the limit. */
    void release(Attempt attempt) {
        release(attempt.ipKey(), attempt.at());
        release(attempt.emailKey(), attempt.at());
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

    /** Undoes one attempt recorded for a key at the given time. */
    private void release(String key, long now) {
        windows.computeIfPresent(
                key,
                (k, deque) -> {
                    deque.removeLastOccurrence(now);
                    return deque;
                });
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
