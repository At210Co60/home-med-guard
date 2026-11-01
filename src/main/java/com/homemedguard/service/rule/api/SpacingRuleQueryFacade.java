package com.homemedguard.service.rule.api;

import java.util.List;
import java.util.Optional;

/**
 * ② 给 ③ 排程求解器的只读契约：只回答"这两味药至少要隔多久"，
 * 错开到哪个时点是求解器的事，② 不参与排程。
 */
public interface SpacingRuleQueryFacade {

    record SpacingRequirement(String componentA,
                              String componentB,
                              int minGapMinutes,
                              String reason,
                              String ruleCode,
                              Integer ruleVersion) {
    }

    /** 成分对无序：(A,B) 与 (B,A) 返回同一条最严格的间隔。 */
    Optional<SpacingRequirement> minGapBetween(String componentA, String componentB);

    /** 在服成分集合内部的所有冲突对，求解器一次拿全，避免逐个查询。 */
    List<SpacingRequirement> gapsWithin(List<String> componentCodes);
}
