package com.homemedguard.service.drug;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.homemedguard.config.CacheConfig;
import com.homemedguard.service.drug.entity.DrugDictEntity;
import com.homemedguard.service.drug.mapper.DrugDictMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 名称自动补全的查询侧，单独成 bean：@Cacheable 走 Spring 代理，
 * 塞在 DrugDictService 里自我调用的话缓存根本不会生效。
 */
@DrugModule
@Service
public class DrugSuggestQuery {

    private final DrugDictMapper drugDictMapper;

    public DrugSuggestQuery(DrugDictMapper drugDictMapper) {
        this.drugDictMapper = drugDictMapper;
    }

    /** 缓存按关键词存原始结果，可见性过滤在 DrugDictService 里做，避免把别人的自建药缓存到公共键上。 */
    @Cacheable(value = CacheConfig.DRUG_DICT_SUGGEST, key = "#keyword.trim().toLowerCase()")
    public List<DrugDictEntity> byKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String kw = keyword.trim();
        return drugDictMapper.selectList(Wrappers.<DrugDictEntity>lambdaQuery()
                .and(w -> w.like(DrugDictEntity::getGenericName, kw).or().like(DrugDictEntity::getTradeName, kw))
                .orderByAsc(DrugDictEntity::getGenericName)
                .last("limit 20"));
    }
}
