package com.opsmind.backend.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 配置 Swagger UI 展示的 OpsMind API 基本信息。 */
@Configuration
public class OpenApiConfig {

    /** @return 由 springdoc 扫描 Controller 后合并的 OpenAPI 元数据 */
    @Bean
    public OpenAPI opsMindOpenApi() {
        return new OpenAPI().info(new Info()
                .title("OpsMind AI API")
                .version("1.0.0")
                .description("故障注入、异步诊断、SSE、工具网关与审计接口")
                .contact(new Contact().name("OpsMind AI")));
    }
}
