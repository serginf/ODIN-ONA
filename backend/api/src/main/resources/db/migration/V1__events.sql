CREATE TABLE IF NOT EXISTS events (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   VARCHAR(36)  NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
    actor       VARCHAR(255),
    event_type  VARCHAR(255) NOT NULL,
    payload_json JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_events_tenant_id  ON events (tenant_id);
CREATE INDEX IF NOT EXISTS idx_events_created_at ON events (created_at);
