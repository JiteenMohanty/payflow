package com.payflow.core.idempotency.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.core.idempotency.domain.IdempotencyKey;
import com.payflow.core.idempotency.domain.IdempotencyKeyStatus;
import com.payflow.core.idempotency.persistence.IdempotencyKeyRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository repository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IdempotencyProperties properties = new IdempotencyProperties(24, 60);
    private final UUID organizationId = UUID.randomUUID();

    private IdempotencyService idempotencyService;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        idempotencyService = new IdempotencyService(repository, redisTemplate, objectMapper, properties, meterRegistry);
    }

    @Test
    void newKeyProceedsAndPersists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(repository.findByOrganizationIdAndKey(organizationId, "key-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(IdempotencyKey.class))).thenAnswer(inv -> inv.getArgument(0));

        IdempotencyCheckResult result = idempotencyService.check(organizationId, "key-1", "fingerprint-a");

        assertThat(result).isInstanceOf(IdempotencyCheckResult.Proceed.class);
        assertThat(meterRegistry.get("payflow.idempotency.requests").tag("result", "proceed").counter().count()).isEqualTo(1.0);
    }

    @Test
    void concurrentDuplicateInsertIsTreatedAsInProgress() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(repository.findByOrganizationIdAndKey(organizationId, "key-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(IdempotencyKey.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        IdempotencyCheckResult result = idempotencyService.check(organizationId, "key-1", "fingerprint-a");

        assertThat(result).isInstanceOf(IdempotencyCheckResult.InProgress.class);
    }

    @Test
    void existingInProgressRecordReturnsInProgress() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(repository.findByOrganizationIdAndKey(organizationId, "key-1")).thenReturn(Optional.of(newRecord("fingerprint-a")));

        IdempotencyCheckResult result = idempotencyService.check(organizationId, "key-1", "fingerprint-a");

        assertThat(result).isInstanceOf(IdempotencyCheckResult.InProgress.class);
    }

    @Test
    void existingCompletedRecordReplaysFromDatabaseOnCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        IdempotencyKey record = newRecord("fingerprint-a");
        record.markCompleted(201, "{\"id\":\"abc\"}");
        when(repository.findByOrganizationIdAndKey(organizationId, "key-1")).thenReturn(Optional.of(record));

        IdempotencyCheckResult result = idempotencyService.check(organizationId, "key-1", "fingerprint-a");

        assertThat(result).isInstanceOf(IdempotencyCheckResult.Replay.class);
        IdempotencyCheckResult.Replay replay = (IdempotencyCheckResult.Replay) result;
        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(replay.responseBody()).isEqualTo("{\"id\":\"abc\"}");
    }

    @Test
    void mismatchedFingerprintIsRejected() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(repository.findByOrganizationIdAndKey(organizationId, "key-1")).thenReturn(Optional.of(newRecord("fingerprint-a")));

        IdempotencyCheckResult result = idempotencyService.check(organizationId, "key-1", "fingerprint-DIFFERENT");

        assertThat(result).isInstanceOf(IdempotencyCheckResult.FingerprintMismatch.class);
    }

    @Test
    void cacheHitReplaysWithoutTouchingTheDatabase() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String cachedJson = "{\"fingerprint\":\"fingerprint-a\",\"statusCode\":201,\"responseBody\":\"{\\\"id\\\":\\\"abc\\\"}\"}";
        when(valueOperations.get(anyString())).thenReturn(cachedJson);

        IdempotencyCheckResult result = idempotencyService.check(organizationId, "key-1", "fingerprint-a");

        assertThat(result).isInstanceOf(IdempotencyCheckResult.Replay.class);
        verifyNoInteractions(repository);
    }

    @Test
    void redisFailureDegradesGracefullyToTheDatabasePath() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis is down"));
        when(repository.findByOrganizationIdAndKey(organizationId, "key-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(IdempotencyKey.class))).thenAnswer(inv -> inv.getArgument(0));

        IdempotencyCheckResult result = idempotencyService.check(organizationId, "key-1", "fingerprint-a");

        assertThat(result).isInstanceOf(IdempotencyCheckResult.Proceed.class);
    }

    @Test
    void completeMarksTheRecordAndWritesToCache() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        IdempotencyKey record = newRecord("fingerprint-a");
        when(repository.findByOrganizationIdAndKey(organizationId, "key-1")).thenReturn(Optional.of(record));

        idempotencyService.complete(organizationId, "key-1", "fingerprint-a", 201, "{\"id\":\"abc\"}");

        assertThat(record.getStatus()).isEqualTo(IdempotencyKeyStatus.COMPLETED);
        assertThat(record.getResponseStatusCode()).isEqualTo(201);
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void abandonDeletesTheRecord() {
        idempotencyService.abandon(organizationId, "key-1");

        verify(repository).deleteByOrganizationIdAndKey(organizationId, "key-1");
    }

    private IdempotencyKey newRecord(String fingerprint) {
        IdempotencyKey record = new IdempotencyKey(organizationId, "key-1", fingerprint, Instant.now().plus(24, ChronoUnit.HOURS));
        ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
        return record;
    }
}
