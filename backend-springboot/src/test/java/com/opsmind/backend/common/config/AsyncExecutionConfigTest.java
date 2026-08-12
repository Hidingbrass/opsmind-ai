package com.opsmind.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncExecutionConfigTest {

    @Test
    void createsBoundedDiagnosisExecutor() {
        AsyncExecutionConfig config = new AsyncExecutionConfig();
        ThreadPoolTaskExecutor executor = config.diagnosisTaskExecutor(
                new ContextPropagatingTaskDecorator(),
                2,
                4,
                20
        );
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(4);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity())
                    .isEqualTo(20);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void rejectsInvalidCapacityConfiguration() {
        AsyncExecutionConfig config = new AsyncExecutionConfig();

        assertThatThrownBy(() -> config.diagnosisTaskExecutor(
                new ContextPropagatingTaskDecorator(),
                4,
                2,
                20
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
