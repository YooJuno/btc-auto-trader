package com.btcautotrader.upbit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;

@Component
public class UpbitRateLimiter {
    private final boolean enabled;
    private final long minIntervalMs;
    private final int maxRequestsPerSecond;
    private final int maxRequestsPerMinute;

    private final Object monitor = new Object();
    private final Deque<Long> secondWindow = new ArrayDeque<>();
    private final Deque<Long> minuteWindow = new ArrayDeque<>();
    private long lastRequestAtMs;

    public UpbitRateLimiter(
            @Value("${upbit.rate-limit.enabled:true}") boolean enabled,
            @Value("${upbit.rate-limit.min-interval-ms:120}") long minIntervalMs,
            @Value("${upbit.rate-limit.max-requests-per-second:8}") int maxRequestsPerSecond,
            @Value("${upbit.rate-limit.max-requests-per-minute:240}") int maxRequestsPerMinute
    ) {
        this.enabled = enabled;
        this.minIntervalMs = Math.max(0, minIntervalMs);
        this.maxRequestsPerSecond = Math.max(1, maxRequestsPerSecond);
        this.maxRequestsPerMinute = Math.max(1, maxRequestsPerMinute);
    }

    /**
     * Reserves a request slot, blocking the CALLING thread only.
     *
     * The previous implementation slept inside synchronized(monitor). Because a saturated minute window
     * can compute a wait of tens of seconds, one thread could hold the monitor for that long and stall
     * every other Upbit call in the JVM — including the fetchCurrentPrice a stop-loss depends on and
     * order submission itself. Compute the wait under the lock, sleep outside it, then re-check.
     */
    public void acquire(String endpoint) {
        if (!enabled) {
            return;
        }

        while (true) {
            long waitMs;
            synchronized (monitor) {
                long now = System.currentTimeMillis();
                trim(now);
                waitMs = computeWaitMs(now);
                if (waitMs <= 0) {
                    waitMs = remainingMinIntervalMs(now);
                }
                if (waitMs <= 0) {
                    // Slot taken atomically under the lock, so concurrent callers cannot double-book it.
                    secondWindow.addLast(now);
                    minuteWindow.addLast(now);
                    lastRequestAtMs = now;
                    return;
                }
            }
            sleep(waitMs, endpoint);
        }
    }

    private long remainingMinIntervalMs(long now) {
        if (minIntervalMs <= 0 || lastRequestAtMs <= 0) {
            return 0;
        }
        long elapsed = now - lastRequestAtMs;
        return elapsed >= minIntervalMs ? 0 : minIntervalMs - elapsed;
    }

    private long computeWaitMs(long now) {
        long secondWaitMs = 0;
        if (secondWindow.size() >= maxRequestsPerSecond && !secondWindow.isEmpty()) {
            long oldestSecond = secondWindow.peekFirst();
            secondWaitMs = 1000 - (now - oldestSecond);
        }

        long minuteWaitMs = 0;
        if (minuteWindow.size() >= maxRequestsPerMinute && !minuteWindow.isEmpty()) {
            long oldestMinute = minuteWindow.peekFirst();
            minuteWaitMs = 60000 - (now - oldestMinute);
        }

        return Math.max(secondWaitMs, minuteWaitMs);
    }

    private void trim(long now) {
        while (!secondWindow.isEmpty() && now - secondWindow.peekFirst() >= 1000) {
            secondWindow.pollFirst();
        }
        while (!minuteWindow.isEmpty() && now - minuteWindow.peekFirst() >= 60000) {
            minuteWindow.pollFirst();
        }
    }

    private static void sleep(long waitMs, String endpoint) {
        if (waitMs <= 0) {
            return;
        }
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting rate limit for endpoint: " + endpoint, ex);
        }
    }
}
