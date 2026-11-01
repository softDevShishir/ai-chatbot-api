package com.example.aichatbot.service;

import com.example.aichatbot.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-user sliding-window rate limit on
 * {@code POST /api/chats/{id}/messages}. Resets if the app restarts — fine
 * for a single-instance demo deployment, documented as a limitation.
 */
@Component
public class RateLimiter {

    private static final long WINDOW_MILLIS = 60_000;

    private final int limitPerMinute;
    private final Clock clock;
    private final Map<String, Deque<Long>> timestampsByUser = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiter(@Value("${chat.rate-limit-per-minute}") int limitPerMinute) {
        this(limitPerMinute, Clock.systemUTC());
    }

    RateLimiter(int limitPerMinute, Clock clock) {
        this.limitPerMinute = limitPerMinute;
        this.clock = clock;
    }

    /**
     * Records one call for {@code user}, throwing if that pushes them over
     * the per-minute limit.
     *
     * @throws TooManyRequestsException if the user has already made {@code CHAT_RATE_LIMIT_PER_MINUTE}
     *                                   calls in the last 60 seconds
     */
    public void checkAndRecord(String user) {
        long now = clock.millis();
        long windowStart = now - WINDOW_MILLIS;
        Deque<Long> timestamps = timestampsByUser.computeIfAbsent(user, key -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limitPerMinute) {
                throw new TooManyRequestsException(
                        "Rate limit exceeded: max " + limitPerMinute + " messages per minute");
            }
            timestamps.addLast(now);
        }
    }
}
