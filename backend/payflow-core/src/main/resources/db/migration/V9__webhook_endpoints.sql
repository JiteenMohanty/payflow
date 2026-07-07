CREATE TABLE webhook_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations (id),
    url VARCHAR(2048) NOT NULL,
    secret_encrypted BYTEA NOT NULL,
    subscribed_events JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_webhook_endpoints_organization_id ON webhook_endpoints (organization_id);

CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_endpoint_id UUID NOT NULL REFERENCES webhook_endpoints (id),
    event_type VARCHAR(50) NOT NULL,
    -- Opaque, redelivered verbatim, never queried into structurally - plain
    -- TEXT rather than jsonb, same reasoning as outbox_events.payload (V7).
    payload TEXT NOT NULL,
    attempt_number INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL,
    response_code INT,
    next_retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_webhook_deliveries_endpoint_id ON webhook_deliveries (webhook_endpoint_id);
-- WebhookRetryJob's own query shape (M9): "select FAILED where
-- next_retry_at <= now" - see EDD section 7.5. Not consumed by any code
-- yet, but the index belongs with the schema that motivates it.
CREATE INDEX idx_webhook_deliveries_status_next_retry ON webhook_deliveries (status, next_retry_at);
