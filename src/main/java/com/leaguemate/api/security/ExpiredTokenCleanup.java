package com.leaguemate.api.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredTokenCleanup {

    private final HashedOAuth2AuthorizationService authorizationService;

    @Scheduled(cron = "${app.auth.cleanup-cron}")
    public void removeExpiredAuthorizations() {
        int removed = authorizationService.removeExpired();
        if (removed > 0) {
            log.info("Rimosse {} autorizzazioni scadute", removed);
        }
    }
}
