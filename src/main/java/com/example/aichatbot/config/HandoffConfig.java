package com.example.aichatbot.config;

import com.example.aichatbot.notification.TelegramConfig;
import com.example.aichatbot.notification.TelegramNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class HandoffConfig {

    @Bean
    public TelegramNotifier telegramNotifier(TelegramConfig telegramConfig,
                                             @Value("${app.store-name}") String storeName,
                                             @Value("${app.timezone}") String timezone) {
        return new TelegramNotifier(telegramConfig, TelegramNotifier.newRestClient(), storeName, ZoneId.of(timezone));
    }

    /**
     * Small, bounded pool for Telegram alerts so a slow or down Telegram can never delay a chat reply.
     * If the queue is full the alert is dropped (the handoff row is still saved, notified=false).
     */
    @Bean(name = "handoffAlertExecutor", destroyMethod = "shutdownNow")
    public Executor handoffAlertExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(50), runnable -> {
                    Thread thread = new Thread(runnable, "handoff-alert");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
