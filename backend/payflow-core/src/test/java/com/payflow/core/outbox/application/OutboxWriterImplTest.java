package com.payflow.core.outbox.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.core.outbox.domain.OutboxEvent;
import com.payflow.core.outbox.persistence.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxWriterImplTest {

    @Mock
    private OutboxEventRepository repository;

    private OutboxWriterImpl outboxWriter;

    @BeforeEach
    void setUp() {
        outboxWriter = new OutboxWriterImpl(repository, new ObjectMapper());
    }

    @Test
    void writeSerializesThePayloadAndSavesAPendingRow() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UUID paymentId = UUID.randomUUID();
        record Payload(String status) {
        }

        outboxWriter.write("PAYMENT", paymentId, "payment.created", "payflow.payments", new Payload("CREATED"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("PAYMENT");
        assertThat(saved.getAggregateId()).isEqualTo(paymentId);
        assertThat(saved.getEventType()).isEqualTo("payment.created");
        assertThat(saved.getKafkaTopic()).isEqualTo("payflow.payments");
        assertThat(saved.getPayload()).contains("\"status\":\"CREATED\"");
    }
}
