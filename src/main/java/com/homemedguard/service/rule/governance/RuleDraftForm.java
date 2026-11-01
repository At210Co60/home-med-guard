package com.homemedguard.service.rule.governance;

import java.time.LocalDate;

/**
 * 规则草稿录入表单（三张表共用，缺的字段按 table 校验）。
 * componentA/B 用标准成分码大写；severity 只允许 A/B/C；sourceRef+sourceDate 不填就不能发布。
 */
public record RuleDraftForm(
        String ruleCode,
        Integer version,
        String componentA,
        String componentB,
        String component,
        String tagCode,
        String severity,
        Integer minGapMinutes,
        String mechanism,
        String advice,
        String expr,
        String sourceRef,
        LocalDate sourceDate) {
}
