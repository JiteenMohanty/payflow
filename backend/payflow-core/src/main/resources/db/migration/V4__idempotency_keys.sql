CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations (id),
    key VARCHAR(255) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    response_status_code INT,
    -- Opaque blob, never queried structurally - plain text, not jsonb.
    response_body TEXT,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_keys_org_key UNIQUE (organization_id, key)
);

CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);
