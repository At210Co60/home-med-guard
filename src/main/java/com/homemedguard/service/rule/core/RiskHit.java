package com.homemedguard.service.rule.core;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一条命中。可解释性靠这三个字段凑齐：
 * basis = 为什么命中（哪两个成分共现 / 哪段表达式为真 / 超了上限多少）；
 * evidence = 参与判定的数值；sourceRef + sourceDate = 依据出自哪份说明书、哪天整理的。
 */
public record RiskHit(
        RiskRuleKind kind,
        Long ruleId,
        String ruleCode,
        Integer ruleVersion,
        Severity severity,
        List<String> components,
        String mechanism,
        String advice,
        String basis,
        Map<String, String> evidence,
        String sourceRef,
        LocalDate sourceDate) {

    public RiskHit {
        components = components == null ? List.of() : List.copyOf(components);
        evidence = evidence == null ? Map.of() : new LinkedHashMap<>(evidence);
    }
}
