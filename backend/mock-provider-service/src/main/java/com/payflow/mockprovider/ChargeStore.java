package com.payflow.mockprovider;

import com.payflow.mockprovider.domain.MockCharge;
import com.payflow.mockprovider.domain.MockChargeStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory only - this service simulates a provider's state, it isn't a
 * system of record. State resets on restart, which is fine for a test double.
 */
@Component
public class ChargeStore {

    private final Map<String, MockCharge> charges = new ConcurrentHashMap<>();

    public MockCharge create(BigDecimal amount, String currency) {
        String chargeId = "mock_ch_" + UUID.randomUUID().toString().replace("-", "");
        MockCharge charge = new MockCharge(chargeId, amount, currency);
        charges.put(chargeId, charge);
        return charge;
    }

    public Optional<MockCharge> find(String chargeId) {
        return Optional.ofNullable(charges.get(chargeId));
    }

    public void markCaptured(String chargeId) {
        charges.get(chargeId).setStatus(MockChargeStatus.CAPTURED);
    }
}
