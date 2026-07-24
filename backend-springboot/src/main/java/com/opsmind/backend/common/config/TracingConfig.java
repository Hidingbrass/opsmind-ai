package com.opsmind.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

/**
 * 将 Micrometer/OpenTelemetry 上下文从 HTTP 请求线程复制到 {@code @Async} 诊断线程。
 *
 * <p>没有该装饰器时，异步执行器会丢失当前 Span，后续 Java -> Python 调用就无法继续
 * 原始请求的 traceId。
 */
@Configuration
public class TracingConfig {

    /** @return 由 Spring 自动配置的异步线程池使用的上下文复制器 */
    @Bean
    public ContextPropagatingTaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }
}
