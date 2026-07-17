-- Development/test migration from V7 to V8.
-- This intentionally resets only derived news history/annotation/condensation data.
-- Market snapshots and all non-news tables are untouched.

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS news_article_annotation;
DROP TABLE IF EXISTS news_article_ticker;
DROP TABLE IF EXISTS news_story_ticker;
DROP TABLE IF EXISTS condensed_news_state;
DROP TABLE IF EXISTS news_article_history;
DROP TABLE IF EXISTS news_story;
SET FOREIGN_KEY_CHECKS = 1;

-- Run docs/news-history-schema.sql immediately afterwards.
