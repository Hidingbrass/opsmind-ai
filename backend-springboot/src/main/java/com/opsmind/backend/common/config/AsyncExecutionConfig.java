package com.opsmind.backend.common.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 为高成本诊断任务提供显式、有界且可观测的进程内线程池。 */
@Configuration
public class AsyncExecutionConfig {

    /** DiagnosisTaskExecutor 的专用 Bean 名称，避免回退到无界默认执行器。 */
    public static final String DIAGNOSIS_EXECUTOR = "diagnosisTaskWorkerPool";

    /**
     * @return 核心线程、最大线程和等待队列均可配置的诊断任务执行器
     */
    @Bean(name = DIAGNOSIS_EXECUTOR)
    public ThreadPoolTaskExecutor diagnosisTaskExecutor(
            ContextPropagatingTaskDecorator taskDecorator,
            @Value("${opsmind.diagnosis.executor.core-pool-size:2}") int corePoolSize,
            @Value("${opsmind.diagnosis.executor.max-pool-size:4}") int maxPoolSize,
            @Value("${opsmind.diagnosis.executor.queue-capacity:20}") int queueCapacity
    ) {
        if (corePoolSize < 1 || maxPoolSize < corePoolSize || queueCapacity < 0) {
            throw new IllegalArgumentException("诊断线程池容量配置不合法");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("diagnosis-");
        executor.setTaskDecorator(taskDecorator);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
