package com.payflow.core.idempotency.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.core.idempotency.domain.IdempotencyKey;
import com.payflow.core.idempotency.domain.IdempotencyKeyStatus;
import com.payflow.core.idempotency.persistence.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Postgres is the durable source of truth; Redis is a fast-path cache only -
 * a cache miss or a down Redis both fall back to the database correctly, per
 * ADR-0004. Concurrent duplicate inserts are resolved by catching the unique
 * constraint violation rather than an explicit SELECT ... FOR UPDATE: simpler,
 * and avoids row-lock contention for what is otherwise a short insert.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService implements IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String CACHE_KEY_PREFIX = "idem:";

    private final IdempotencyKeyRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final IdempotencyProperties properties;

    @Override
    @Transactional
    public IdempotencyCheckResult check(UUID organizationId, String key, String requestFingerprint) {
        Optional<CachedIdempotentResponse> cached = readFromCache(organizationId, key);
        if (cached.isPresent()) {
            CachedIdempotentResponse response = cached.get();
            return response.fingerprint().equals(requestFingerprint)
                    ? new IdempotencyCheckResult.Replay(response.statusCode(), response.responseBody())
                    : new IdempotencyCheckResult.FingerprintMismatch();
        }

        Optional<IdempotencyKey> existing = repository.findByOrganizationIdAndKey(organizationId, key);
        if (existing.isPresent()) {
            IdempotencyKey record = existing.get();
            if (!record.getRequestFingerprint().equals(requestFingerprint)) {
                return new IdempotencyCheckResult.FingerprintMismatch();
            }
            if (record.getStatus() == IdempotencyKeyStatus.IN_PROGRESS) {
                return new IdempotencyCheckResult.InProgress();
            }
            writeToCache(organizationId, key, requestFingerprint, record.getResponseStatusCode(), record.getResponseBody());
            return new IdempotencyCheckResult.Replay(record.getResponseStatusCode(), record.getResponseBody());
        }

        try {
            Instant expiresAt = Instant.now().plus(Duration.ofHours(properties.keyTtlHours()));
            repository.saveAndFlush(new IdempotencyKey(organizationId, key, requestFingerprint, expiresAt));
            return new IdempotencyCheckResult.Proceed();
        } catch (DataIntegrityViolationException lostRace) {
            return new IdempotencyCheckResult.InProgress();
        }
    }

    @Override
    @Transactional
    public void complete(UUID organizationId, String key, String requestFingerprint, int statusCode, String responseBody) {
        repository.findByOrganizationIdAndKey(organizationId, key)
                .ifPresent(record -> record.markCompleted(statusCode, responseBody));
        writeToCache(organizationId, key, requestFingerprint, statusCode, responseBody);
    }

    @Override
    @Transactional
    public void abandon(UUID organizationId, String key) {
        repository.deleteByOrganizationIdAndKey(organizationId, key);
    }

    private Optional<CachedIdempotentResponse> readFromCache(UUID organizationId, String key) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey(organizationId, key));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, CachedIdempotentResponse.class));
        } catch (Exception e) {
            log.warn("Idempotency cache read failed, falling back to database", e);
            return Optional.empty();
        }
    }

    private void writeToCache(UUID organizationId, String key, String fingerprint, int statusCode, String responseBody) {
        try {
            CachedIdempotentResponse cached = new CachedIdempotentResponse(fingerprint, statusCode, responseBody);
            redisTemplate.opsForValue().set(
                    cacheKey(organizationId, key),
                    objectMapper.writeValueAsString(cached),
                    Duration.ofMinutes(properties.redisCacheTtlMinutes()));
        } catch (Exception e) {
            log.warn("Idempotency cache write failed", e);
        }
    }

    private String cacheKey(UUID organizationId, String key) {
        return CACHE_KEY_PREFIX + organizationId + ":" + key;
    }
}
