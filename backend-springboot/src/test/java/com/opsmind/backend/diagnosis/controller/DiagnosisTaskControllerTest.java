package com.opsmind.backend.diagnosis.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opsmind.backend.diagnosis.service.DiagnosisTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class DiagnosisTaskControllerTest {

    @Test
    void ignoresUntrustedForwardedForHeaderByDefault() {
        DiagnosisTaskService taskService = mock(DiagnosisTaskService.class);
        when(taskService.createTask("incident-1", "127.0.0.1")).thenReturn(null);
        DiagnosisTaskController controller = new DiagnosisTaskController(taskService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.50");

        controller.createTask("incident-1", request);

        verify(taskService).createTask("incident-1", "127.0.0.1");
    }
}
