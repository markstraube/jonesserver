# News pipeline V8 corrections

## Corrected invariants

1. A provider article is global. It is unique by canonical key, not by ticker.
2. Ticker subscriptions are represented in `news_article_ticker`.
3. A global article is classified at most once. Existing articles log `ALREADY_CLASSIFIED`.
4. Story creation is serialized and protected by a database unique key fallback.
5. Story ticker membership is additive in `news_story_ticker`; later articles cannot erase earlier tickers.
6. Story semantics are set when the story is created. Later matching articles can add tickers and escalate the critical flag, but do not overwrite direction/event type/materiality.
7. Reports only include story deltas linked to their ticker.
8. Condensation records its trigger and performs a weekday startup catch-up after 04:00 New York time when no state exists for the day.

## New operational log events

- `NEWS_INGEST`
- `NEWS_CLASSIFIER_CALL`
- `NEWS_CLASSIFIER_SKIPPED`
- `NEWS_CLASSIFIER_RESULT`
- `NEWS_STORY_CREATED`
- `NEWS_STORY_ATTACHED`
- `NEWS_CONDENSATION`

A healthy run should show classifier calls less than or equal to the number of new global articles.
