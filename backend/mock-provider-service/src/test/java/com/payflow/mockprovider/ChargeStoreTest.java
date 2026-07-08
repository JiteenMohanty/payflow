package com.payflow.mockprovider;

import com.payflow.mockprovider.domain.MockCharge;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ChargeStoreTest {

    private final ChargeStore store = new ChargeStore();

    @Test
    void aRepeatedMerchantReferenceReturnsTheSameChargeInsteadOfCreatingASecondOne() {
        MockCharge first = store.create(new BigDecimal("10.00"), "USD", "payment-1");
        MockCharge second = store.create(new BigDecimal("10.00"), "USD", "payment-1");

        assertThat(second.getChargeId()).isEqualTo(first.getChargeId());
    }

    @Test
    void differentMerchantReferencesCreateDifferentCharges() {
        MockCharge first = store.create(new BigDecimal("10.00"), "USD", "payment-1");
        MockCharge second = store.create(new BigDecimal("10.00"), "USD", "payment-2");

        assertThat(second.getChargeId()).isNotEqualTo(first.getChargeId());
    }

    @Test
    void aNullOrBlankMerchantReferenceNeverDedupesAcrossCalls() {
        MockCharge first = store.create(new BigDecimal("10.00"), "USD", null);
        MockCharge second = store.create(new BigDecimal("10.00"), "USD", "");
        MockCharge third = store.create(new BigDecimal("10.00"), "USD", null);

        assertThat(first.getChargeId()).isNotEqualTo(second.getChargeId());
        assertThat(first.getChargeId()).isNotEqualTo(third.getChargeId());
    }

    @Test
    void aChargeIsFindableByItsId() {
        MockCharge created = store.create(new BigDecimal("10.00"), "USD", "payment-1");

        assertThat(store.find(created.getChargeId())).contains(created);
    }
}
