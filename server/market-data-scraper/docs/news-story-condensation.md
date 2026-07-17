# News story clustering and condensation

## Runtime flow

1. Every fetched item is persisted in `news_article_history`.
2. Exact duplicates are recognized without an LLM by canonical story key, normalized URL, or normalized headline.
3. Only a genuinely new article is passed to the story classifier when `news.classifier.enabled=true`.
4. The classifier either assigns an existing `news_story` or creates a new one and records model/prompt metadata.
5. The condenser persists a versioned `condensed_news_state` over the last 72 hours.
6. Reports contain the latest condensed state plus `NEW`/`UPDATED` stories whose `lastUpdatedAt` is later than the condensation timestamp.

## Scheduled condensation

The configured cron expressions use `America/New_York`, so daylight-saving changes are handled by the scheduler:

- 04:00 ET: start of the normal US pre-market
- 08:30 ET: one hour before the Nasdaq regular session
- no ordinary regular-session or after-market condensation
- critical classifier events may trigger a condensation, with a 120-minute cooldown

## Prompts

The property value is a resource version, not the prompt text:

- `news.classifier.prompt-version=market-news-classifier-v1` loads `src/main/resources/prompts/market-news-classifier-v1.md`
- `news.condensation.prompt-version=market-news-condenser-v1` loads `src/main/resources/prompts/market-news-condenser-v1.md`

Both the model and prompt version are persisted with generated data.

## OpenAI calls

- Classifier: at most once for each newly persisted article that survives exact deduplication. Exact duplicates generate no call.
- Condenser: normally twice per US trading day. A critical-event run is additional but rate-limited by the cooldown.
- Snapshot creation itself never calls the condenser and does not reclassify known news.
