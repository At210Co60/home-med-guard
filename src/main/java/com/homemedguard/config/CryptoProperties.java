package com.homemedguard.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 健康数据字段级加密配置（AES-256-GCM）。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.crypto")
public class CryptoProperties {

    /** base64 编码的 32 字节主密钥。留空且启用了 ① 家庭模块时启动失败——静默降级成明文存储更危险。 */
    private String dataKey;
}
