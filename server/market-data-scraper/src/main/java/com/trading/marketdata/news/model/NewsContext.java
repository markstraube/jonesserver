package com.trading.marketdata.news.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewsContext(int windowHours,
                          Instant generatedAt,
                          CondensedState condensedState,
                          List<StoryDelta> storiesSinceCondensation) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CondensedState(Instant generatedAt,
                                 Instant windowStart,
                                 Instant windowEnd,
                                 String model,
                                 String promptVersion,
                                 String trigger,
                                 Object summary) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StoryDelta(Long storyId,
                             String status,
                             String representativeHeadline,
                             Instant firstSeenAt,
                             Instant lastUpdatedAt,
                             int articleCount,
                             String affectedTickers,
                             String direction,
                             String eventType,
                             String materiality,
                             Double confidence,
                             boolean modelGenerated) {}
}
