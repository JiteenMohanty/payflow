# ADR-0008: Immutable Double-Entry Ledger

**Status:** Accepted
**Date:** 2026-07-06

## Context

PayFlow's ledger is the financial record a merchant and PayFlow itself both rely on to answer "what happened to this money." A ledger that can be edited in place cannot be trusted for reconciliation or audit — an `UPDATE` to a historical row destroys the very history the ledger exists to preserve, and makes "why does this number not match what we reported last week" unanswerable.

## Decision

Model the ledger as immutable and append-only:

- `ledger_transactions` is a journal entry header (one per capture, refund, or adjustment), linked to the payment/refund that caused it.
- `ledger_entries` are the debit/credit lines under a transaction, each referencing a `ledger_accounts` row, always a positive `amount` with an explicit `entry_type` (`DEBIT`/`CREDIT`).
- A capture posts: `Dr Provider Settlement Receivable` / `Cr Merchant Payable` for the captured amount. A refund posts the reverse: `Dr Merchant Payable` / `Cr Provider Settlement Receivable`. This models money PayFlow expects to receive from the provider against money PayFlow owes the merchant — a clean two-account model that platform fees or multi-party splits (future work) extend to N-way entries under the same transaction, not a new mechanism.
- The application's database role has no `UPDATE`/`DELETE` grant on `ledger_entries`; corrections are always new reversing entries in a new `ledger_transactions` row.
- A DB check constraint enforces that the entries under one `ledger_transaction_id` net to zero per currency.

## Alternatives Considered

- **Mutable ledger rows with an `updated_at`/status field**: rejected — this is the exact failure mode the decision exists to prevent; "the balance changed because we fixed a row" is unauditable.
- **Single-entry running-balance ledger (one row per payment with a running total)**: rejected — doesn't produce a double-entry audit trail (no way to see "money moved from where to where"), and doesn't generalize cleanly to platform fees or multi-party settlement later.
- **Event-sourcing the entire ledger (rebuild balances by replaying all events)**: considered, but the `ledger_entries` append-only table already gives replay-ability (it *is* the event log) without adding a separate event store and projection-rebuild mechanism the team doesn't need yet.

## Consequences

- Any correction, however small, is a new transaction, which means the ledger's history is always a complete, honest record — valuable for both merchant trust and PayFlow's own auditability.
- Reading a merchant's current balance means summing entries rather than reading a single row — acceptable given a per-organization index on `ledger_entries` and expected transaction volumes; a materialized running-balance view is a future optimization if needed, not a day-one requirement.
- Ledger posting must happen in the same transaction as the payment/refund state change (enforced by `LedgerService` being called from within `PaymentService`/`RefundService`'s transactional method, never from an async handler) — this is what makes "captured but not yet in the ledger" an impossible state rather than a race condition to defend against.
