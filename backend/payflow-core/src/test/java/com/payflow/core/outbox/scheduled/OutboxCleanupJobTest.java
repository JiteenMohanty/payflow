package com.payflow.core.outbox.scheduled;

import com.payflow.core.outbox.domain.OutboxEventStatus;
import com.payflow.core.outbox.persistence.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxCleanupJobTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new OutboxCleanupJob(outboxEventRepository, new OutboxCleanupProperties("0 0 3 * * *", 30));
    }

    @Test
    void deletesPublishedEventsOlderThanTheRetentionWindow() {
        when(outboxEventRepository.deleteByStatusAndCreatedAtBefore(eq(OutboxEventStatus.PUBLISHED), any(Instant.class)))
                .thenReturn(5L);

        job.cleanup();

        verify(outboxEventRepository).deleteByStatusAndCreatedAtBefore(eq(OutboxEventStatus.PUBLISHED), any(Instant.class));
    }
}
