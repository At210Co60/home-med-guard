package com.homemedguard.common.crypto;

import com.homemedguard.config.CryptoProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * ① 启用时把主密钥交给 codec。密钥缺失直接拒绝启动——
 * 健康数据要么加密要么不上线，不允许"密钥没配就退化成明文"这种静默降级。
 */
@Component
@ConditionalOnProperty(prefix = "app.module", name = "family", havingValue = "true")
public class CryptoKeyInitializer {

    public CryptoKeyInitializer(CryptoProperties properties) {
        String encoded = properties.getDataKey();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "app.module.family=true 但 app.crypto.data-key 未配置：请设置环境变量 HMG_DATA_KEY（base64 的 32 字节密钥）");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.crypto.data-key 不是合法 base64", ex);
        }
        AesGcmCodec.use(keyBytes);
    }
}
