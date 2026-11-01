package com.homemedguard.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * 单机 Caffeine 本地缓存，不引 Redis（handoff §四.4）。
 * 缓存名按模块申请，别共用：规则缓存和库存缓存的失效时机完全不同。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String RULE_SET = "ruleSet";
    public static final String COMPONENT_DICT = "componentDict";
    public static final String DRUG_DICT_SUGGEST = "drugDictSuggest";
    public static final String STOCK_BATCH = "stockBatch";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(5_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats());
        manager.setCacheNames(List.of(RULE_SET, COMPONENT_DICT, DRUG_DICT_SUGGEST, STOCK_BATCH));
        return manager;
    }
}
