package com.ieia.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Periodically purges expired / revoked refresh tokens so the table stays small. */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenRepository repository;

    public RefreshTokenCleanupJob(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "0 30 3 * * *")   // 03:30 every day
    @Transactional
    public void purge() {
        int removed = repository.deleteExpiredAndRevoked(Instant.now());
        if (removed > 0) {
            log.info("Purged {} expired/revoked refresh tokens", removed);
        }
    }
}
