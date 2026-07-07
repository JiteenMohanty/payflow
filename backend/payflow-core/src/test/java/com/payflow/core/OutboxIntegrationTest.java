package com.payflow.core;

import com.payflow.core.common.event.OutboxTopics;
import com.payflow.core.outbox.application.OutboxPublisher;
import com.payflow.core.outbox.domain.OutboxEvent;
import com.payflow.core.outbox.domain.OutboxEventStatus;
import com.payflow.core.outbox.persistence.OutboxEventRepository;
import com.payflow.core.payment.api.CreatePaymentRequest;
import com.payflow.core.payment.api.PaymentResponse;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the outbox against a real Postgres and Kafka (the schedule
 * itself is disabled in the test profile - see application-test.yml - so
 * relayPendingEvents() is called directly here rather than waited on, which
 * would otherwise race this test's own assertions).
 */
class OutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private OutboxPublisher outboxPublisher;

    @Test
    void creatingAPaymentWritesAPendingOutboxRowInTheSameTransaction() {
        Tenant tenant = provisionTenant();

        UUID paymentId = createPayment(tenant);

        List<OutboxEvent> events = outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(paymentId))
                .toList();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("payment.created");
        assertThat(events.get(0).getKafkaTopic()).isEqualTo(OutboxTopics.PAYMENTS);
        assertThat(events.get(0).getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void relayPendingEventsPublishesToKafkaAndMarksTheRowPublished() {
        Tenant tenant = provisionTenant();
        UUID paymentId = createPayment(tenant);

        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            consumer.subscribe(List.of(OutboxTopics.PAYMENTS));

            outboxPublisher.relayPendingEvents();

            ConsumerRecords<String, String> records = pollUntilNotEmpty(consumer);
            List<ConsumerRecord<String, String>> forThisPayment = StreamSupport
                    .stream(records.records(OutboxTopics.PAYMENTS).spliterator(), false)
                    .filter(r -> r.key().equals(paymentId.toString()))
                    .toList();
            assertThat(forThisPayment).hasSize(1);
            assertThat(forThisPayment.get(0).value()).contains("\"status\":\"CREATED\"");
        }

        OutboxEvent event = outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(paymentId))
                .findFirst().orElseThrow();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    private ConsumerRecords<String, String> pollUntilNotEmpty(KafkaConsumer<String, String> consumer) {
        for (int i = 0; i < 20; i++) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return records;
            }
        }
        return ConsumerRecords.empty();
    }

    private KafkaConsumer<String, String> newConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-integration-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(props);
    }

    private UUID createPayment(Tenant tenant) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenant.apiKey());
        HttpEntity<CreatePaymentRequest> request = new HttpEntity<>(
                new CreatePaymentRequest(tenant.merchantId(), new BigDecimal("1.00"), "USD", null, null), headers);
        ResponseEntity<PaymentResponse> response =
                restTemplate.exchange("/v1/payments", HttpMethod.POST, request, PaymentResponse.class);
        return response.getBody().id();
    }
}
