package com.example.aichatbot.service;

import com.example.aichatbot.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    @Test
    void allowsUpToTheConfiguredLimitPerMinute() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        RateLimiter limiter = new RateLimiter(2, clock);

        assertThatCode(() -> {
            limiter.checkAndRecord("alice");
            limiter.checkAndRecord("alice");
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsTheCallThatExceedsTheLimit() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        RateLimiter limiter = new RateLimiter(2, clock);

        limiter.checkAndRecord("alice");
        limiter.checkAndRecord("alice");

        assertThatThrownBy(() -> limiter.checkAndRecord("alice"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void tracksEachUserIndependently() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        RateLimiter limiter = new RateLimiter(1, clock);

        limiter.checkAndRecord("alice");

        assertThatCode(() -> limiter.checkAndRecord("bob")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkAndRecord("alice")).isInstanceOf(TooManyRequestsException.class);
    }
}
