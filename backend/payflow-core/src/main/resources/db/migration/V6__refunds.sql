CREATE TABLE refunds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations (id),
    payment_id UUID NOT NULL REFERENCES payments (id),
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(255),
    provider_reference VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refunds_organization_id ON refunds (organization_id);
CREATE INDEX idx_refunds_payment_id ON refunds (payment_id);

-- Deferred from V5: ledger_transactions.refund_id can only be added now that
-- refunds exists to reference. NULL for CAPTURE-type transactions.
ALTER TABLE ledger_transactions ADD COLUMN refund_id UUID REFERENCES refunds (id);
