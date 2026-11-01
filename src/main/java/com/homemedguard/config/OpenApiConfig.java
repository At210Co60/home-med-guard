package com.homemedguard.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI homeMedGuardOpenApi() {
        return new OpenAPI().info(new Info()
                .title("HomeMedGuard API")
                .version("0.1.0")
                .description("""
                        家用药箱效期与用药冲突监护系统。
                        本系统为家庭用药提醒辅助工具，规则库不完整，不构成诊疗建议，用药请遵医嘱。
                        """));
    }
}
