package com.opsmind.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Spring Boot 应用入口。
 *
 * <p>{@link EnableAsync} 开启 Spring 异步方法代理，使诊断任务执行器中的 {@code @Async}
 * 方法可以在后台线程执行，避免创建任务的 HTTP 请求等待 AI 诊断完成。
 */
@EnableAsync
@SpringBootApplication
public class OpsMindBackendApplication {

    /**
     * 启动 Spring 容器、自动配置和内嵌 Web 服务器。
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(OpsMindBackendApplication.class, args);
    }
}
