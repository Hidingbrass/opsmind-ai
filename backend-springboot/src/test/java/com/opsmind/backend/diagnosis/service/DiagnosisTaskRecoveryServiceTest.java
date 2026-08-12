package com.opsmind.backend.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.opsmind.backend.diagnosis.model.DiagnosisTask;
import com.opsmind.backend.diagnosis.model.DiagnosisTaskStatus;
import com.opsmind.backend.diagnosis.repository.DiagnosisTaskRepository;
import org.junit.jupiter.api.Test;

class DiagnosisTaskRecoveryServiceTest {

    @Test
    void marksTasksInterruptedByRestartAsFailedAndClearsReuseState() {
        DiagnosisTaskRepository repository = mock(DiagnosisTaskRepository.class);
        DiagnosisTaskCacheService cacheService = mock(DiagnosisTaskCacheService.class);
        DiagnosisTask task = mock(DiagnosisTask.class);
        AtomicReference<DiagnosisTaskStatus> status =
                new AtomicReference<>(DiagnosisTaskStatus.RUNNING);
        AtomicReference<String> failureReason = new AtomicReference<>();
        Instant createdAt = Instant.parse("2026-08-10T07:00:00Z");

        when(task.getId()).thenReturn("task-1");
        when(task.getIncidentId()).thenReturn("incident-1");
        when(task.getTraceId()).thenReturn("0123456789abcdef0123456789abcdef");
        when(task.getStatus()).thenAnswer(ignored -> status.get());
        when(task.getFailureReason()).thenAnswer(ignored -> failureReason.get());
        when(task.getCreatedAt()).thenReturn(createdAt);
        when(task.getUpdatedAt()).thenReturn(createdAt);
        when(task.getStartedAt()).thenReturn(createdAt);
        when(task.getFinishedAt()).thenReturn(createdAt);
        doAnswer(invocation -> {
            status.set(DiagnosisTaskStatus.FAILED);
            failureReason.set(invocation.getArgument(0));
            return null;
        }).when(task).markFailed(org.mockito.ArgumentMatchers.anyString());
        when(repository.findAllByStatusIn(anyCollection())).thenReturn(List.of(task));
        when(repository.saveAndFlush(task)).thenReturn(task);

        DiagnosisTaskRecoveryService recoveryService =
                new DiagnosisTaskRecoveryService(repository, cacheService);

        assertThat(recoveryService.recoverInterruptedTasks()).isEqualTo(1);
        verify(repository).saveAndFlush(task);
        verify(cacheService).putTask(argThat(response ->
                response.status() == DiagnosisTaskStatus.FAILED
                        && response.failureReason().contains("服务重启")));
        verify(cacheService).finishTask("incident-1", false);
    }
}
