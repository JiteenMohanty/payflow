package com.payflow.core.idempotency.scheduled;

import com.payflow.core.idempotency.persistence.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyKeyCleanupJobTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private IdempotencyKeyCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new IdempotencyKeyCleanupJob(idempotencyKeyRepository);
    }

    @Test
    void deletesKeysPastTheirExpiry() {
        when(idempotencyKeyRepository.deleteByExpiresAtBefore(any(Instant.class))).thenReturn(3L);

        job.cleanup();

        verify(idempotencyKeyRepository).deleteByExpiresAtBefore(any(Instant.class));
    }
}
