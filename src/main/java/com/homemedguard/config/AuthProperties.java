package com.homemedguard.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** JWT 与口令相关配置。密钥一律来自环境变量或 application-local.yml，仓库里只留占位符。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** HS256 密钥，base64，至少 32 字节。留空直接启动失败，不允许用默认值跑起来。 */
    private String jwtSecret;

    private Duration accessTokenTtl = Duration.ofHours(2);

    private Duration refreshTokenTtl = Duration.ofDays(30);

    /** 家庭邀请码有效期。 */
    private Duration inviteTtl = Duration.ofHours(24);

    /**
     * 引导管理员：仓库里不放任何口令，本地/测试在自己的配置里显式打开。
     * 不做这个就没法产生第一个 SYS_ADMIN（提升接口本身要求 SYS_ADMIN，会死锁）。
     */
    private BootstrapAdmin bootstrap = new BootstrapAdmin();

    @Getter
    @Setter
    public static class BootstrapAdmin {

        private String username;

        private String password;
    }
}
