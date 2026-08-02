package com.opsmind.backend.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class DiagnosisTaskCacheServiceTest {

    private ValueOperations<String, String> valueOperations;
    private DiagnosisTaskCacheService cacheService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService = new DiagnosisTaskCacheService(
                redisTemplate,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void corruptedTaskCacheFallsBackToDatabaseCaller() {
        when(valueOperations.get("opsmind:diagnosis:task:task-1"))
                .thenReturn("{not-json");

        assertThat(cacheService.getTask("task-1")).isEmpty();
    }

    @Test
    void redisFailureDoesNotBlockIncidentLock() {
        when(valueOperations.setIfAbsent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        )).thenThrow(new DataAccessResourceFailureException("redis down"));

        assertThat(cacheService.tryAcquireIncidentLock("incident-1")).isTrue();
    }
}
