package com.payflow.core.infrastructure.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduledJobCorrelationTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void putsAFreshCorrelationIdOnMdcForTheDurationOfTheJobAndRemovesItAfter() {
        String[] observed = new String[1];

        ScheduledJobCorrelation.runWithFreshCorrelationId(() -> observed[0] = MDC.get(CorrelationIdFilter.MDC_KEY));

        assertThat(observed[0]).isNotBlank();
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void removesTheCorrelationIdEvenWhenTheJobThrows() {
        assertThatThrownBy(() -> ScheduledJobCorrelation.runWithFreshCorrelationId(() -> {
            throw new RuntimeException("boom");
        })).isInstanceOf(RuntimeException.class);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void eachRunGetsADifferentCorrelationId() {
        String[] first = new String[1];
        String[] second = new String[1];

        ScheduledJobCorrelation.runWithFreshCorrelationId(() -> first[0] = MDC.get(CorrelationIdFilter.MDC_KEY));
        ScheduledJobCorrelation.runWithFreshCorrelationId(() -> second[0] = MDC.get(CorrelationIdFilter.MDC_KEY));

        assertThat(first[0]).isNotEqualTo(second[0]);
    }
}
