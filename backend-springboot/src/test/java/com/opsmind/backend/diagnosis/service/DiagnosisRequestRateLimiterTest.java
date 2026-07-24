package com.opsmind.backend.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class DiagnosisRequestRateLimiterTest {

    private ValueOperations<String, String> valueOperations;
    private DiagnosisRequestRateLimiter rateLimiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiter = new DiagnosisRequestRateLimiter(redisTemplate, 2);
    }

    @Test
    void rejectsRequestAfterConfiguredQuota() {
        when(valueOperations.increment("opsmind:diagnosis:rate:client-1"))
                .thenReturn(3L);

        assertThatThrownBy(() -> rateLimiter.check("client-1"))
                .isInstanceOf(DiagnosisRateLimitExceededException.class)
                .hasMessageContaining("频繁");
    }

    @Test
    void failsOpenWhenRedisIsUnavailable() {
        when(valueOperations.increment("opsmind:diagnosis:rate:client-1"))
                .thenThrow(new DataAccessResourceFailureException("redis down"));

        assertThatCode(() -> rateLimiter.check("client-1"))
                .doesNotThrowAnyException();
    }
}
