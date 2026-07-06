package com.payflow.mockprovider.domain;

import java.math.BigDecimal;

public class MockCharge {

    private final String chargeId;
    private final BigDecimal amount;
    private final String currency;
    private MockChargeStatus status;

    public MockCharge(String chargeId, BigDecimal amount, String currency) {
        this.chargeId = chargeId;
        this.amount = amount;
        this.currency = currency;
        this.status = MockChargeStatus.AUTHORIZED;
    }

    public String getChargeId() {
        return chargeId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public MockChargeStatus getStatus() {
        return status;
    }

    public void setStatus(MockChargeStatus status) {
        this.status = status;
    }
}
