package com.homemedguard.service.rule.core;

import java.util.List;

/**
 * 一次求值的完整结论。
 *
 * @param failedRuleCodes   表达式求值出错的规则编号——绝不静默当成"没有风险"，必须回传并留痕
 * @param incompleteReasons 判定不完整的客观原因（缺体重、成分未归一、① 未接入等）
 */
public record EvaluationOutcome(
        String ruleSetStamp,
        List<RiskHit> hits,
        List<String> failedRuleCodes,
        List<String> incompleteReasons) {

    public Severity maxSeverity() {
        Severity max = null;
        for (RiskHit hit : hits) {
            max = Severity.max(max, hit.severity());
        }
        return max;
    }

    public boolean blocking() {
        return hits.stream().anyMatch(hit -> hit.severity().blocking());
    }
}
