-- V8 news schema: global articles and stories, ticker links kept separately.
-- For an existing V7 installation, back up the news tables before applying the migration section.

CREATE TABLE IF NOT EXISTS news_article_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    canonical_key VARCHAR(64) NOT NULL,
    article_id VARCHAR(128) NULL,
    headline VARCHAR(1024) NOT NULL,
    source VARCHAR(128) NULL,
    url VARCHAR(2048) NULL,
    full_text LONGTEXT NULL,
    published_at TIMESTAMP(6) NULL,
    first_seen_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    seen_count INT NOT NULL,
    story_id BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_news_canonical_key UNIQUE (canonical_key),
    INDEX idx_news_published (published_at),
    INDEX idx_news_last_seen (last_seen_at),
    INDEX idx_news_story (story_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS news_article_ticker (
    id BIGINT NOT NULL AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    ticker VARCHAR(12) NOT NULL,
    first_seen_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_article_ticker UNIQUE (article_id, ticker),
    CONSTRAINT fk_article_ticker_article FOREIGN KEY (article_id)
        REFERENCES news_article_history(id) ON DELETE CASCADE,
    INDEX idx_article_ticker_symbol (ticker, article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS news_story (
    id BIGINT NOT NULL AUTO_INCREMENT,
    story_key VARCHAR(64) NOT NULL,
    representative_headline VARCHAR(1024) NOT NULL,
    first_seen_at TIMESTAMP(6) NOT NULL,
    last_updated_at TIMESTAMP(6) NOT NULL,
    article_count INT NOT NULL,
    affected_tickers VARCHAR(512) NULL,
    event_type VARCHAR(64) NULL,
    materiality VARCHAR(16) NULL,
    direction VARCHAR(16) NULL,
    confidence DOUBLE NULL,
    classifier_model VARCHAR(128) NULL,
    classifier_prompt_version VARCHAR(128) NULL,
    requires_recondensation BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uk_news_story_key UNIQUE (story_key),
    INDEX idx_story_updated (last_updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS news_story_ticker (
    id BIGINT NOT NULL AUTO_INCREMENT,
    story_id BIGINT NOT NULL,
    ticker VARCHAR(12) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_story_ticker UNIQUE (story_id, ticker),
    CONSTRAINT fk_story_ticker_story FOREIGN KEY (story_id)
        REFERENCES news_story(id) ON DELETE CASCADE,
    INDEX idx_story_ticker_symbol (ticker, story_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS condensed_news_state (
    id BIGINT NOT NULL AUTO_INCREMENT,
    scope_key VARCHAR(128) NOT NULL,
    generated_at TIMESTAMP(6) NOT NULL,
    window_start TIMESTAMP(6) NOT NULL,
    window_end TIMESTAMP(6) NOT NULL,
    story_watermark BIGINT NOT NULL,
    model VARCHAR(128) NULL,
    prompt_version VARCHAR(128) NULL,
    trigger_type VARCHAR(48) NOT NULL,
    summary_json LONGTEXT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_condensed_scope_time (scope_key, generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Existing V7 databases should be migrated deliberately because duplicate per-ticker
-- rows must be merged. The safest first production rollout is to archive/drop only the
-- four news tables and recreate them from this file; market snapshots are unaffected.
