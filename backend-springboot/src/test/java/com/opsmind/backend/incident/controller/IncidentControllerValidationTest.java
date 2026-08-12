package com.opsmind.backend.incident.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opsmind.backend.common.exception.GlobalExceptionHandler;
import com.opsmind.backend.incident.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IncidentControllerValidationTest {

    @Test
    void rejectsIncidentFieldsThatExceedPersistenceLimits() throws Exception {
        IncidentService incidentService = mock(IncidentService.class);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new IncidentController(incidentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        String oversizedTitle = "x".repeat(121);

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "serviceName": "payment-service",
                                  "severity": "HIGH",
                                  "symptom": "payment timeout"
                                }
                                """.formatted(oversizedTitle)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(incidentService);
    }
}
