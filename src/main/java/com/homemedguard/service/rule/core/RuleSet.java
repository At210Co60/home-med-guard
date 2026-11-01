package com.homemedguard.service.rule.core;

import com.homemedguard.service.rule.entity.ComponentLimitEntity;
import com.homemedguard.service.rule.entity.ContraindicationRuleEntity;
import com.homemedguard.service.rule.entity.InteractionRuleEntity;
import com.homemedguard.service.rule.entity.SpacingRuleEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 一次求值用的不可变规则快照。② 的全部判定只读这个对象，测试里直接手工构造即可，不碰数据库。
 *
 * @param stamp 规则集指纹（条数 + 最后更新时间），固化进 risk_evaluation_log 供追溯
 */
public record RuleSet(
        List<InteractionRuleEntity> interactions,
        List<ContraindicationRuleEntity> contraindications,
        List<SpacingRuleEntity> spacings,
        Map<String, List<ComponentLimitEntity>> limitsByComponent,
        Map<String, String> aliasToCode,
        Map<String, String> unitByComponent,
        Map<Long, List<DrugComponentView>> componentsByDrug,
        String stamp) {

    public record DrugComponentView(String componentCode, BigDecimal contentPerForm, String formUnit, String resolveStatus) {
    }

    public static RuleSet empty() {
        return new RuleSet(List.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), "empty@0");
    }

    /** 别名、商品名里的成分写法、标准码大小写都归到标准成分码；归不到返回 empty。 */
    public Optional<String> normalize(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(aliasToCode.get(text.trim().toLowerCase(Locale.ROOT)));
    }

    public List<ComponentLimitEntity> limits(String componentCode) {
        return limitsByComponent.getOrDefault(componentCode, List.of());
    }

    public String unitOf(String componentCode) {
        return unitByComponent.getOrDefault(componentCode, "mg");
    }
}
