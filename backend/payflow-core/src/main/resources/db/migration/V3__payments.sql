CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations (id),
    merchant_id UUID NOT NULL REFERENCES merchants (id),
    -- Resolved at authorize time, not create time - see EDD section 5.1.
    provider_account_id UUID REFERENCES provider_accounts (id),
    provider_code VARCHAR(20),
    provider_reference VARCHAR(255),
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    captured_amount NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (captured_amount >= 0),
    refunded_amount NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (refunded_amount >= 0),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    authorized_at TIMESTAMPTZ,
    captured_at TIMESTAMPTZ
);

CREATE INDEX idx_payments_organization_id ON payments (organization_id);
CREATE INDEX idx_payments_merchant_id ON payments (merchant_id);

CREATE TABLE payment_state_transitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payments (id),
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    actor VARCHAR(20) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_state_transitions_payment_id ON payment_state_transitions (payment_id);
