CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE hammerly.auctions
    ADD CONSTRAINT auctions_start_price_positive CHECK (start_price > 0),
    ADD CONSTRAINT auctions_current_bid_valid CHECK (current_bid >= start_price),
    ADD CONSTRAINT auctions_status_valid CHECK (status IN ('active', 'ended')),
    ADD CONSTRAINT auctions_time_window_valid CHECK (end_time >= start_time);

ALTER TABLE hammerly.bids
    ADD CONSTRAINT bids_amount_positive CHECK (amount > 0);

CREATE TABLE hammerly.knowledge_documents (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    source TEXT NOT NULL CHECK (char_length(source) BETWEEN 1 AND 500),
    content TEXT NOT NULL CHECK (char_length(content) > 0),
    content_hash CHAR(64) NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    failure_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (source, content_hash)
);

CREATE TABLE hammerly.knowledge_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES hammerly.knowledge_documents(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    content TEXT NOT NULL CHECK (char_length(content) > 0),
    embedding vector(1536) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (document_id, chunk_index)
);

CREATE INDEX knowledge_chunks_embedding_hnsw_idx
    ON hammerly.knowledge_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX knowledge_documents_status_updated_idx
    ON hammerly.knowledge_documents (status, updated_at DESC);

CREATE TABLE hammerly.knowledge_base_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO hammerly.knowledge_base_state (id, version) VALUES (1, 0);

CREATE TABLE hammerly.outbox_events (
    id UUID PRIMARY KEY,
    event_type TEXT NOT NULL,
    event_version INTEGER NOT NULL CHECK (event_version > 0),
    aggregate_id UUID NOT NULL,
    topic TEXT NOT NULL,
    payload JSONB NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RETRY', 'PROCESSING', 'PUBLISHED')),
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ
);

CREATE INDEX outbox_events_relay_idx
    ON hammerly.outbox_events (status, next_attempt_at, created_at)
    WHERE published_at IS NULL;
