CREATE TABLE IF NOT EXISTS queue_event (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    status     VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    capacity   INTEGER,
    open_time  TIMESTAMP,
    close_time TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS queue_entry (
    id           BIGSERIAL PRIMARY KEY,
    event_id     BIGINT       NOT NULL REFERENCES queue_event(id),
    user_id      VARCHAR(255) NOT NULL,
    status       VARCHAR(50)  NOT NULL DEFAULT 'WAITING',
    joined_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    admitted_at  TIMESTAMP,
    expired_at   TIMESTAMP,
    cancelled_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_queue_entry_event_user ON queue_entry(event_id, user_id);
CREATE INDEX IF NOT EXISTS idx_queue_entry_event_status ON queue_entry(event_id, status);
CREATE INDEX IF NOT EXISTS idx_queue_entry_expiry ON queue_entry(status, joined_at);

CREATE TABLE IF NOT EXISTS queue_audit_log (
    id         BIGSERIAL PRIMARY KEY,
    entry_id   BIGINT      NOT NULL REFERENCES queue_entry(id),
    action     VARCHAR(50) NOT NULL,
    payload    TEXT,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);
