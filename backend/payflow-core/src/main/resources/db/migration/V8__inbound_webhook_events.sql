CREATE TABLE inbound_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_code VARCHAR(20) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    -- Received and recorded for audit/dedup, not queried into structurally -
    -- plain TEXT rather than jsonb, same reasoning as idempotency_keys.response_body
    -- (V4) and outbox_events.payload (V7).
    payload TEXT NOT NULL,
    signature_valid BOOLEAN NOT NULL,
    processing_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_inbound_webhook_events_provider_event UNIQUE (provider_code, provider_event_id)
);

-- Reconciliation looks payments up by (provider_code, provider_reference) -
-- this must be a real, enforced invariant, not just a probabilistically
-- unlikely collision, since reconciliation correctness depends on matching
-- exactly one payment. NULL provider_reference values (CREATED/FAILED
-- payments, before authorization) are unaffected - Postgres treats NULLs as
-- distinct from each other under a UNIQUE constraint.
ALTER TABLE payments ADD CONSTRAINT uq_payments_provider_code_reference UNIQUE (provider_code, provider_reference);
