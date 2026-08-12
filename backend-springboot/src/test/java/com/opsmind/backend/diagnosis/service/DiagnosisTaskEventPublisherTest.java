package com.opsmind.backend.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.opsmind.backend.diagnosis.dto.DiagnosisTaskEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class DiagnosisTaskEventPublisherTest {

    @Test
    void keepsEverySubscriberForTheSameTask() {
        DiagnosisTaskEventPublisher publisher = new DiagnosisTaskEventPublisher();

        publisher.subscribe("task-1");
        publisher.subscribe("task-1");

        assertThat(publisher.subscriberCount("task-1")).isEqualTo(2);
    }

    @Test
    void duplicateTerminalSnapshotOnCompletedEmitterIsIdempotent() {
        DiagnosisTaskEventPublisher publisher = new DiagnosisTaskEventPublisher();
        SseEmitter emitter = publisher.subscribe("task-1");
        emitter.complete();

        assertThatCode(() -> publisher.sendSnapshot(
                "task-1",
                emitter,
                DiagnosisTaskEvent.failed("task-1", "service restarted")
        )).doesNotThrowAnyException();
        assertThat(publisher.subscriberCount("task-1")).isZero();
    }
}
