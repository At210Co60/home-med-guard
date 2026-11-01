package com.homemedguard.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模块开关位（app.module.*）。每个模块的 Bean 用 @ConditionalOnProperty 挂在自己名下，
 * 关掉后系统仍要能启动、其他模块功能不受影响——这是"完全模块化"的可验证证据。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class ModuleProperties {

    private Map<String, Boolean> module = new LinkedHashMap<>();

    public boolean enabled(String name) {
        return module.getOrDefault(name, Boolean.TRUE);
    }
}
