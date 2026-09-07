package com.dogsout.server.notification;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Async powers the fire-and-forget push sends; scheduling drives the playdate
 * reminders in {@code PlaydateReminderService}.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
