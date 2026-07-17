package com.trading.marketdata.news.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NewsCondensationJob {
    private final NewsCondensationService service;

    public NewsCondensationJob(NewsCondensationService service) {
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startupCatchUp() {
        service.startupCatchUp();
    }

    @Scheduled(cron = "${news.condensation.pre-open-cron:0 0 4 * * MON-FRI}", zone = "America/New_York")
    public void preOpen() {
        service.condenseNow("SCHEDULED_PREMARKET_OPEN");
    }

    @Scheduled(cron = "${news.condensation.one-hour-before-open-cron:0 30 8 * * MON-FRI}", zone = "America/New_York")
    public void beforeOpen() {
        service.condenseNow("SCHEDULED_ONE_HOUR_BEFORE_OPEN");
    }

    @Scheduled(fixedDelayString = "${news.condensation.material-check-ms:300000}")
    public void materialCheck() {
        service.condenseIfCritical();
    }
}
