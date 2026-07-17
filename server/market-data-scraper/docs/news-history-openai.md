# News history and OpenAI annotations

The snapshot keeps `news` as the current raw feed and adds `newsContext` for the last 72 hours.

## Separation of responsibilities

- `news_article_history`: raw normalized article history and deterministic first/last seen metadata.
- `news_article_annotation`: explicitly model-generated metadata. Every row stores model, prompt version,
  timestamp and confidence; it is not represented as market fact.
- `newsContext`: compact report projection. Full historical article bodies stay in MySQL.

## Activation

1. Apply `docs/news-history-schema.sql`.
2. Set `OPENAI_API_KEY`.
3. Set `news.annotation.enabled=true`.

The official OpenAI Java SDK is used through the Responses API with Structured Outputs. New articles are
annotated at most `news.annotation.max-new-per-snapshot` per snapshot. Existing articles are reused by
`article_id + prompt_version`; changing the prompt version intentionally creates a new classification series.

If MySQL or OpenAI is unavailable, normal quote/options/news snapshot generation continues and
`newsContext` or individual annotations may be absent.
