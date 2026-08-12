package com.opsmind.backend.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.opsmind.backend.diagnosis.dto.DiagnosisTaskResponse;
import com.opsmind.backend.diagnosis.model.DiagnosisTask;
import com.opsmind.backend.diagnosis.model.DiagnosisTaskStatus;
import com.opsmind.backend.diagnosis.repository.DiagnosisTaskRepository;
import com.opsmind.backend.incident.service.IncidentService;
import com.opsmind.backend.observability.service.OpsMindMetrics;
import com.opsmind.backend.observability.service.TraceContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

class DiagnosisTaskServiceTest {

    private DiagnosisTaskRepository repository;
    private DiagnosisTaskExecutor executor;
    private DiagnosisTaskCacheService cacheService;
    private DiagnosisRequestRateLimiter rateLimiter;
    private IncidentService incidentService;
    private DiagnosisTaskService taskService;

    @BeforeEach
    void setUp() {
        repository = mock(DiagnosisTaskRepository.class);
        executor = mock(DiagnosisTaskExecutor.class);
        cacheService = mock(DiagnosisTaskCacheService.class);
        rateLimiter = mock(DiagnosisRequestRateLimiter.class);
        incidentService = mock(IncidentService.class);
        taskService = new DiagnosisTaskService(
                incidentService,
                repository,
                executor,
                mock(DiagnosisTaskEventPublisher.class),
                cacheService,
                rateLimiter,
                mock(OpsMindMetrics.class),
                mock(TraceContextService.class)
        );
    }

    @Test
    void reusableTaskBypassesRateLimitAndAiExecution() {
        DiagnosisTask existing = task(
                "task-1",
                "incident-1",
                DiagnosisTaskStatus.SUCCESS
        );
        when(cacheService.getReusableTaskId("incident-1"))
                .thenReturn(Optional.of("task-1"));
        when(repository.findById("task-1")).thenReturn(Optional.of(existing));

        DiagnosisTaskResponse response = taskService.createTask(
                "incident-1",
                "client-1"
        );

        assertThat(response.id()).isEqualTo("task-1");
        verify(rateLimiter, never()).check("client-1");
        verify(executor, never()).execute(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void heldLockWithoutVisibleTaskDoesNotCreateDuplicate() {
        when(cacheService.getReusableTaskId("incident-1"))
                .thenReturn(Optional.empty());
        when(cacheService.tryAcquireIncidentLock("incident-1")).thenReturn(false);
        when(repository.findFirstByIncidentIdAndStatusInOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyCollection()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.createTask("incident-1", "client-1"))
                .isInstanceOf(DiagnosisTaskConflictException.class);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void latestTaskRestoresIncidentContextAfterPageRefresh() {
        DiagnosisTask latest = task(
                "task-latest",
                "incident-1",
                DiagnosisTaskStatus.SUCCESS
        );
        when(repository.findFirstByIncidentIdOrderByCreatedAtDesc("incident-1"))
                .thenReturn(Optional.of(latest));

        Optional<DiagnosisTaskResponse> response =
                taskService.getLatestTaskForIncident("incident-1");

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().id()).isEqualTo("task-latest");
        verify(incidentService).getById("incident-1");
    }

    @Test
    void rejectedAsyncSubmissionPersistsFailedTerminalState() {
        DiagnosisTask savedTask = task(
                "task-rejected",
                "incident-1",
                DiagnosisTaskStatus.PENDING
        );
        when(cacheService.getReusableTaskId("incident-1")).thenReturn(Optional.empty());
        when(cacheService.tryAcquireIncidentLock("incident-1")).thenReturn(true);
        when(repository.save(org.mockito.ArgumentMatchers.any(DiagnosisTask.class)))
                .thenReturn(savedTask);
        when(repository.saveAndFlush(savedTask)).thenReturn(savedTask);
        doThrow(new TaskRejectedException("queue full"))
                .when(executor).execute("task-rejected");

        assertThatThrownBy(() -> taskService.createTask("incident-1", "client-1"))
                .isInstanceOf(TaskRejectedException.class);

        verify(savedTask).markFailed(org.mockito.ArgumentMatchers.contains("容量"));
        verify(repository).saveAndFlush(savedTask);
        verify(cacheService).finishTask("incident-1", false);
    }

    private DiagnosisTask task(
            String taskId,
            String incidentId,
            DiagnosisTaskStatus status
    ) {
        DiagnosisTask task = mock(DiagnosisTask.class);
        Instant now = Instant.parse("2026-07-05T10:00:00Z");
        when(task.getId()).thenReturn(taskId);
        when(task.getIncidentId()).thenReturn(incidentId);
        when(task.getTraceId()).thenReturn("0123456789abcdef0123456789abcdef");
        when(task.getStatus()).thenReturn(status);
        when(task.getCreatedAt()).thenReturn(now);
        when(task.getUpdatedAt()).thenReturn(now);
        when(task.getStartedAt()).thenReturn(now);
        when(task.getFinishedAt()).thenReturn(now);
        return task;
    }
}
