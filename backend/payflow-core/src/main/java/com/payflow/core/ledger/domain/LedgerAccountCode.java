package com.payflow.core.ledger.domain;

import java.util.Locale;

/**
 * The well-known chart-of-accounts entries capture/refund posting uses -
 * see ADR-0008. Each code carries its correct account type so a caller can
 * never accidentally create "merchant_payable" as an ASSET.
 */
public enum LedgerAccountCode {
    PROVIDER_SETTLEMENT_RECEIVABLE(LedgerAccountType.ASSET),
    MERCHANT_PAYABLE(LedgerAccountType.LIABILITY);

    private final LedgerAccountType accountType;

    LedgerAccountCode(LedgerAccountType accountType) {
        this.accountType = accountType;
    }

    public LedgerAccountType accountType() {
        return accountType;
    }

    public String dbCode() {
        return name().toLowerCase(Locale.ROOT);
    }
}
