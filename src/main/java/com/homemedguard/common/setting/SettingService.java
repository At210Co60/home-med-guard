package com.homemedguard.common.setting;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 系统参数读取（阈值、开关）。家庭级覆盖优先，其次全局默认（sys_config.family_id = 0）。
 * 这里用显式 Caffeine 而不是 @Cacheable：intOf/boolOf 要复用 value()，
 * 走 Spring 代理的自我调用会静默绕开缓存。
 */
@Service
public class SettingService {

    private static final Logger log = LoggerFactory.getLogger(SettingService.class);
    private static final String GLOBAL = "G";
    private static final long GLOBAL_SCOPE = 0L;

    private final SysConfigMapper mapper;
    private final Cache<String, Optional<String>> cache;

    public SettingService(SysConfigMapper mapper) {
        this.mapper = mapper;
        this.cache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build();
    }

    public Optional<String> value(String configKey, Long familyId) {
        String key = configKey + ':' + (familyId == null ? GLOBAL : familyId);
        return cache.get(key, k -> load(configKey, familyId));
    }

    public int intOf(String configKey, Long familyId, int fallback) {
        return value(configKey, familyId).map(raw -> {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException ex) {
                log.warn("sys_config {}={} 不是整数，回退默认 {}", configKey, raw, fallback);
                return fallback;
            }
        }).orElse(fallback);
    }

    public boolean boolOf(String configKey, Long familyId, boolean fallback) {
        return value(configKey, familyId)
                .map(raw -> "true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim()))
                .orElse(fallback);
    }

    /** 管理员改完参数调用它，否则最坏要等 10 分钟才生效。 */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    private Optional<String> load(String configKey, Long familyId) {
        if (familyId != null && familyId != GLOBAL_SCOPE) {
            SysConfigEntity own = selectOne(configKey, familyId);
            if (own != null) {
                return Optional.ofNullable(own.getConfigValue());
            }
        }
        return Optional.ofNullable(selectOne(configKey, GLOBAL_SCOPE)).map(SysConfigEntity::getConfigValue);
    }

    private SysConfigEntity selectOne(String configKey, long familyId) {
        return mapper.selectOne(Wrappers.<SysConfigEntity>lambdaQuery()
                .eq(SysConfigEntity::getConfigKey, configKey)
                .eq(SysConfigEntity::getFamilyId, familyId)
                .last("limit 1"));
    }
}
