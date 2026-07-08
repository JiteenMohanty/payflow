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
    // Real providers require an idempotency key on charge creation for
    // exactly this reason: without it, a retried authorize() call after a
    // lost response (M11's resilience.Retry, now wrapping ProviderClient
    // calls) would create a second, orphaned charge here that PayFlow has
    // no local record of at all - a materially worse failure mode than the
    // capture/refund case, where retrying an already-applied operation just
    // hits a 409 the ReconciliationSweeper backstop (M9) eventually
    // corrects. computeIfAbsent is atomic under concurrent access, so two
    // concurrent retries with the same reference can never both create a
    // charge.
    private final Map<String, MockCharge> chargesByMerchantReference = new ConcurrentHashMap<>();

    public MockCharge create(BigDecimal amount, String currency, String merchantReference) {
        if (merchantReference == null || merchantReference.isBlank()) {
            return createNew(amount, currency);
        }
        return chargesByMerchantReference.computeIfAbsent(merchantReference, ref -> createNew(amount, currency));
    }

    private MockCharge createNew(BigDecimal amount, String currency) {
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
