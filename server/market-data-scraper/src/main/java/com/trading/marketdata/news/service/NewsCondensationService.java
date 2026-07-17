package com.trading.marketdata.news.service;

import com.trading.marketdata.news.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class NewsCondensationService {
    private static final Logger log = LoggerFactory.getLogger(NewsCondensationService.class);
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private final NewsStoryRepository stories;
    private final CondensedNewsStateRepository states;
    private final OpenAiNewsCondenserService condenser;

    @Value("${news.history.window-hours:72}") int hours;
    @Value("${news.condensation.enabled:false}") boolean enabled;
    @Value("${news.condensation.scope:AI_MEMORY}") String scope;
    @Value("${news.condensation.critical-cooldown-minutes:120}") long criticalCooldownMinutes;

    public NewsCondensationService(NewsStoryRepository stories,
                                   CondensedNewsStateRepository states,
                                   OpenAiNewsCondenserService condenser) {
        this.stories = stories;
        this.states = states;
        this.condenser = condenser;
    }

    @Transactional
    public synchronized void condenseNow(String trigger) {
        if (!enabled) return;
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofHours(hours));
        List<NewsStoryEntity> recentStories = stories.findByLastUpdatedAtGreaterThanEqualOrderByLastUpdatedAtDesc(start);
        if (recentStories.isEmpty()) {
            log.info("NEWS_CONDENSATION_SKIPPED trigger={} reason=NO_STORIES", trigger);
            return;
        }

        CondensedNewsStateEntity state = new CondensedNewsStateEntity();
        state.setScopeKey(scope);
        state.setGeneratedAt(end);
        state.setWindowStart(start);
        state.setWindowEnd(end);
        state.setStoryWatermark(recentStories.stream().map(NewsStoryEntity::getId)
                .filter(Objects::nonNull).mapToLong(Long::longValue).max().orElse(0));
        state.setModel(condenser.model());
        state.setPromptVersion(condenser.promptVersion());
        state.setTriggerType(trigger);
        state.setSummaryJson(condenser.condense(recentStories));
        states.save(state);

        recentStories.stream().filter(NewsStoryEntity::isRequiresRecondensation).forEach(story -> {
            story.setRequiresRecondensation(false);
            stories.save(story);
        });
        log.info("NEWS_CONDENSATION trigger={} storyCount={} stateId={} promptVersion={}",
                trigger, recentStories.size(), state.getId(), state.getPromptVersion());
    }

    public void condenseIfCritical() {
        if (!enabled) return;
        CondensedNewsStateEntity latest = latest().orElse(null);
        if (latest != null && latest.getGeneratedAt().isAfter(Instant.now().minus(Duration.ofMinutes(criticalCooldownMinutes)))) return;
        boolean critical = stories
                .findByLastUpdatedAtGreaterThanEqualOrderByLastUpdatedAtDesc(Instant.now().minus(Duration.ofHours(24)))
                .stream().anyMatch(NewsStoryEntity::isRequiresRecondensation);
        if (critical) condenseNow("CRITICAL_EVENT");
    }

    public void startupCatchUp() {
        if (!enabled) return;
        ZonedDateTime now = ZonedDateTime.now(NEW_YORK);
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return;

        ZonedDateTime firstRun = now.toLocalDate().atTime(4, 0).atZone(NEW_YORK);
        if (now.isBefore(firstRun)) return;

        CondensedNewsStateEntity latest = latest().orElse(null);
        boolean promptChanged = latest == null || !Objects.equals(latest.getPromptVersion(), condenser.promptVersion());
        if (promptChanged) {
            condenseNow("PROMPT_VERSION_CHANGE");
            return;
        }

        boolean alreadyToday = latest.getGeneratedAt().atZone(NEW_YORK).toLocalDate().equals(now.toLocalDate());
        if (!alreadyToday) condenseNow("STARTUP_CATCH_UP");
    }

    public Optional<CondensedNewsStateEntity> latest() {
        return states.findFirstByScopeKeyOrderByGeneratedAtDesc(scope);
    }
}
