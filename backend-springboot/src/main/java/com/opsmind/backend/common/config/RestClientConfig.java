package com.opsmind.backend.common.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 集中创建后端对外发起 HTTP 请求时使用的 {@link RestClient}。
 *
 * <p>当前主要由诊断服务调用 Python FastAPI AI 服务，集中配置可以避免每个
 * Service 自己创建客户端，后续也方便统一增加超时、重试和追踪头。
 */
@Configuration
public class RestClientConfig {

    /**
     * 将基于 JDK HttpClient 的 RestClient 注册为 Spring Bean，供业务 Service 构造器注入。
     *
     * @return 全局复用的 HTTP 客户端
     */
    @Bean
    public RestClient restClient() {
        // 显式使用 HTTP/1.1，与当前本地 FastAPI 服务的调用环境保持简单兼容。
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }
}
