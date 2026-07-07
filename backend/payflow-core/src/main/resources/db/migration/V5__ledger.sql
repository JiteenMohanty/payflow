CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations (id),
    code VARCHAR(50) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ledger_accounts_org_code UNIQUE (organization_id, code)
);

CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations (id),
    payment_id UUID NOT NULL REFERENCES payments (id),
    transaction_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_transactions_organization_id ON ledger_transactions (organization_id);
CREATE INDEX idx_ledger_transactions_payment_id ON ledger_transactions (payment_id);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ledger_transaction_id UUID NOT NULL REFERENCES ledger_transactions (id),
    ledger_account_id UUID NOT NULL REFERENCES ledger_accounts (id),
    entry_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_entries_ledger_transaction_id ON ledger_entries (ledger_transaction_id);
CREATE INDEX idx_ledger_entries_ledger_account_id ON ledger_entries (ledger_account_id);

-- Enforces ADR-0008's "entries under one ledger_transaction_id net to zero
-- per currency" invariant. A plain CHECK constraint can't aggregate across
-- rows, so this is a deferred constraint trigger: it fires once per inserted
-- row but only actually validates at COMMIT time, by which point both the
-- debit and credit legs of a posting have been inserted.
CREATE OR REPLACE FUNCTION check_ledger_transaction_balance() RETURNS TRIGGER AS $$
DECLARE
    unbalanced RECORD;
BEGIN
    SELECT currency, SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE -amount END) AS net
    INTO unbalanced
    FROM ledger_entries
    WHERE ledger_transaction_id = NEW.ledger_transaction_id
    GROUP BY currency
    HAVING SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE -amount END) <> 0
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'Ledger transaction % does not balance for currency %: net %',
            NEW.ledger_transaction_id, unbalanced.currency, unbalanced.net;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_ledger_entries_balance
    AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION check_ledger_transaction_balance();

-- Enforces "append-only" at the database level regardless of which role
-- connects (a REVOKE would not restrict the table owner, and the
-- application connects as the same role that ran these migrations).
-- Corrections are always new reversing entries in a new ledger_transactions
-- row, never an edit to history.
CREATE OR REPLACE FUNCTION prevent_ledger_entries_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_no_update
    BEFORE UPDATE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_entries_mutation();

CREATE TRIGGER trg_ledger_entries_no_delete
    BEFORE DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_entries_mutation();
